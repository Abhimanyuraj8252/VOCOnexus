package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.AudioRepositoryImpl
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.export.AudioCombiner
import com.voconexus.app.core.export.AudioExporter
import com.voconexus.app.core.playback.PlaybackController
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.engine.ScriptPlannerEngine
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.ui.screens.audiolibrary.AudioLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioLibraryViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var viewModel: AudioLibraryViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = AudioStorageManager(context)
        val audioRepo = AudioRepositoryImpl(database.audioAssetDao(), database.chunkDao(), storageManager)
        val playbackController = PlaybackController(context)
        val exporter = AudioExporter(context, storageManager, AudioCombiner())

        val tokenEstimator = HeuristicTokenEstimator()
        val scriptPlannerEngine = ScriptPlannerEngine(
            sentenceSegmenter = RuleBasedSentenceSegmenter(),
            tokenEstimator = tokenEstimator,
            durationEstimator = com.voconexus.app.core.domain.StandardDurationEstimator(),
            chunkPlanner = ChunkPlanner(tokenEstimator),
            partBuilder = PartBuilder(),
            planValidator = PlanValidator()
        )

        val projectRepo = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            scriptPlannerEngine = scriptPlannerEngine
        )

        val now = System.currentTimeMillis()
        database.projectDao().insertProject(ProjectEntity("proj-1", "P1", "D", now, now, "DRAFT"))
        database.documentDao().insertDocument(DocumentEntity("doc-1", "proj-1", "D1", "Text.", "h", 1, 1, 5, now, "VALID", "1.0", 2))
        database.partDao().insertPart(PartEntity("part-1", "proj-1", "doc-1", "P1", 0, 2, 2, 10))
        database.chunkDao().insertChunk(ChunkEntity("chunk-1", "proj-1", "doc-1", "part-1", 0, "Text 1", "h1", "Text 1", "nh1", "e", "m", "v", status = ChunkStatus.COMPLETED, createdAt = now, updatedAt = now))
        database.chunkDao().insertChunk(ChunkEntity("chunk-2", "proj-1", "doc-1", "part-1", 1, "Text 2", "h2", "Text 2", "nh2", "e", "m", "v", status = ChunkStatus.COMPLETED, createdAt = now, updatedAt = now))

        viewModel = AudioLibraryViewModel(audioRepo, projectRepo, playbackController, exporter, storageManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testMultiSelectionModelAndOperations() {
        runBlocking {
            viewModel.selectProject("proj-1")

            val state1 = viewModel.uiState.first { it.chunks.isNotEmpty() }
            assertEquals(2, state1.chunks.size)
            assertFalse(state1.isMultiSelectMode)

            viewModel.toggleChunkSelection("chunk-1")
            assertTrue(viewModel.uiState.value.isMultiSelectMode)
            assertEquals(1, viewModel.uiState.value.selectedChunkIds.size)

            viewModel.selectAll()
            assertEquals(2, viewModel.uiState.value.selectedChunkIds.size)

            viewModel.invertSelection()
            assertEquals(0, viewModel.uiState.value.selectedChunkIds.size)

            viewModel.selectAll()
            viewModel.clearSelection()
            assertEquals(0, viewModel.uiState.value.selectedChunkIds.size)
        }
    }
}
