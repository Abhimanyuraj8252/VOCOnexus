package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.TtsRepositoryImpl
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import com.voconexus.app.core.tts.installer.ModelInstaller
import com.voconexus.app.core.tts.installer.ModelStorageManager
import com.voconexus.app.ui.screens.models.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModelManagerViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var ttsRepository: TtsRepositoryImpl
    private lateinit var evaluator: DeviceProfileEvaluator
    private lateinit var viewModel: ModelManagerViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = ModelStorageManager(context)
        val installer = ModelInstaller(storageManager)
        evaluator = DeviceProfileEvaluator(context)
        val registry = TtsEngineRegistry(context)

        ttsRepository = TtsRepositoryImpl(
            modelDao = database.ttsModelDao(),
            voiceDao = database.ttsVoiceDao(),
            storageManager = storageManager,
            modelInstaller = installer,
            deviceEvaluator = evaluator,
            engineRegistry = registry
        )

        viewModel = ModelManagerViewModel(ttsRepository, evaluator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testModelManagerInitialStateAndBenchmark() {
        runBlocking {
            val models = ttsRepository.getAllModels().first()
            assertTrue(models.isNotEmpty())

            viewModel.loadData()
            val state = viewModel.uiState.first { it.deviceProfile != null }
            assertNotNull(state.deviceProfile)

            viewModel.runBenchmark("fake-model-en", "fake_voice_female")
            val updatedState = viewModel.uiState.first { it.lastBenchmarkResult != null }
            assertNotNull(updatedState.lastBenchmarkResult)
            assertTrue(updatedState.lastBenchmarkResult!!.realTimeFactor > 0f)
        }
    }
}
