package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.GenerationRepositoryImpl
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.generation.engine.GenerationCoordinator
import com.voconexus.app.core.generation.queue.GenerationQueue
import com.voconexus.app.core.generation.recovery.GenerationRecoveryManager
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.engine.ScriptPlannerEngine
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import com.voconexus.app.ui.screens.generation.GenerationQueueViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GenerationQueueViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var viewModel: GenerationQueueViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = AudioStorageManager(context)
        val queue = GenerationQueue(database.chunkDao())
        val registry = TtsEngineRegistry(context)
        val validator = AudioValidator()

        val modelStorageManager = com.voconexus.app.core.tts.installer.ModelStorageManager(context)
        val modelInstaller = com.voconexus.app.core.tts.installer.ModelInstaller(modelStorageManager)
        val deviceEvaluator = com.voconexus.app.core.tts.device.DeviceProfileEvaluator(context)

        val coordinator = GenerationCoordinator(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            jobDao = database.generationJobDao(),
            audioAssetDao = database.audioAssetDao(),
            queue = queue,
            engineRegistry = registry,
            storageManager = storageManager,
            audioValidator = validator,
            context = context,
            ttsRepository = com.voconexus.app.core.data.repository.TtsRepositoryImpl(
                modelDao = database.ttsModelDao(),
                voiceDao = database.ttsVoiceDao(),
                storageManager = modelStorageManager,
                modelInstaller = modelInstaller,
                deviceEvaluator = deviceEvaluator,
                engineRegistry = registry
            )
        )

        val recoveryManager = GenerationRecoveryManager(
            jobDao = database.generationJobDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            audioValidator = validator
        )

        val genRepo = GenerationRepositoryImpl(
            context = context,
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            jobDao = database.generationJobDao(),
            queue = queue,
            coordinator = coordinator,
            recoveryManager = recoveryManager
        )

        val tokenEstimator = HeuristicTokenEstimator()
        val scriptPlannerEngine = ScriptPlannerEngine(
            sentenceSegmenter = RuleBasedSentenceSegmenter(),
            tokenEstimator = tokenEstimator,
            durationEstimator = com.voconexus.app.core.domain.StandardDurationEstimator(),
            chunkPlanner = ChunkPlanner(tokenEstimator),
            partBuilder = PartBuilder(),
            planValidator = PlanValidator()
        )

        val projRepo = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            scriptPlannerEngine = scriptPlannerEngine
        )

        // Seed mock project data
        val now = System.currentTimeMillis()
        database.projectDao().insertProject(ProjectEntity("proj-1", "P1", "D", now, now, "DRAFT"))
        database.documentDao().insertDocument(DocumentEntity("doc-1", "proj-1", "D1", "Text.", "h", 1, 1, 5, now, "VALID", "1.0", 1))
        database.chunkDao().insertChunk(ChunkEntity("chunk-1", "proj-1", "doc-1", "part-1", 0, "Text.", "h", "Text.", "nh", "fake-tts", "fake-model-en", "fake_voice_female", status = ChunkStatus.QUEUED, createdAt = now, updatedAt = now))

        viewModel = GenerationQueueViewModel("proj-1", genRepo, projRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testGenerationQueueViewModelStateAndStart() {
        runBlocking {
            val state1 = viewModel.uiState.first { it.chunks.isNotEmpty() }
            assertEquals(1, state1.totalChunksCount)
            assertEquals(0, state1.completedChunksCount)

            viewModel.startGeneration("doc-1")

            val state2 = viewModel.uiState.first { it.job != null }
            assertNotNull(state2.job)
            assertEquals("proj-1", state2.job?.projectId)
        }
    }
}
