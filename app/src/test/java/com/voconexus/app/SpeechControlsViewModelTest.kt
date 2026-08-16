package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.domain.duration.TargetDurationPlanner
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.engine.ScriptPlannerEngine
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.tts.preview.AudioPreviewPlayer
import com.voconexus.app.core.tts.preview.SpeechPreviewManager
import com.voconexus.app.ui.screens.speechcontrols.SpeechControlsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
class SpeechControlsViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var projectRepo: ProjectRepositoryImpl
    private lateinit var previewManager: SpeechPreviewManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = AudioStorageManager(context)

        val tokenEstimator = HeuristicTokenEstimator()
        val scriptPlannerEngine = ScriptPlannerEngine(
            sentenceSegmenter = RuleBasedSentenceSegmenter(),
            tokenEstimator = tokenEstimator,
            durationEstimator = StandardDurationEstimator(),
            chunkPlanner = ChunkPlanner(tokenEstimator),
            partBuilder = PartBuilder(),
            planValidator = PlanValidator()
        )

        projectRepo = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            scriptPlannerEngine = scriptPlannerEngine,
            ioDispatcher = testDispatcher
        )

        val modelStorageManager = com.voconexus.app.core.tts.installer.ModelStorageManager(context)
        val modelInstaller = com.voconexus.app.core.tts.installer.ModelInstaller(modelStorageManager)
        val deviceEvaluator = com.voconexus.app.core.tts.device.DeviceProfileEvaluator(context)
        val engineRegistry = com.voconexus.app.core.tts.engine.TtsEngineRegistry(context)

        previewManager = SpeechPreviewManager(
            context = context,
            audioPreviewPlayer = AudioPreviewPlayer(context),
            engineRegistry = engineRegistry,
            ttsRepository = com.voconexus.app.core.data.repository.TtsRepositoryImpl(
                modelDao = database.ttsModelDao(),
                voiceDao = database.ttsVoiceDao(),
                storageManager = modelStorageManager,
                modelInstaller = modelInstaller,
                deviceEvaluator = deviceEvaluator,
                engineRegistry = engineRegistry
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testViewModelSpeedAndPitchUpdates() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val project = ProjectEntity("proj-1", "P1", "D", now, now, "DRAFT", speed = 1.0f, pitch = 0.0f)
        database.projectDao().insertProject(project)

        val viewModel = SpeechControlsViewModel(
            projectId = "proj-1",
            projectRepository = projectRepo,
            durationEstimator = StandardDurationEstimator(),
            targetPlanner = TargetDurationPlanner(),
            previewManager = previewManager,
            prefsManager = com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(ApplicationProvider.getApplicationContext()),
            ioDispatcher = testDispatcher
        )

        viewModel.updateSpeed(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.speed, 0.01f)

        viewModel.updatePitch(2.0f)
        assertEquals(2.0f, viewModel.uiState.value.pitchSemitones, 0.01f)

        viewModel.updateSpeed(2.2f)
        assertNotNull(viewModel.uiState.value.speedWarning)
    }
}
