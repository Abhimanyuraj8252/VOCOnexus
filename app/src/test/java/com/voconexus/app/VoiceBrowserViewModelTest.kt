package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.TtsRepositoryImpl
import com.voconexus.app.core.tts.VoiceGender
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import com.voconexus.app.core.tts.installer.ModelInstaller
import com.voconexus.app.core.tts.installer.ModelStorageManager
import com.voconexus.app.core.tts.preview.AudioPreviewPlayer
import com.voconexus.app.core.tts.preview.VoicePreviewManager
import com.voconexus.app.ui.screens.voices.VoiceBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceBrowserViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var ttsRepository: TtsRepositoryImpl
    private lateinit var voicePreviewManager: VoicePreviewManager
    private lateinit var viewModel: VoiceBrowserViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = ModelStorageManager(context)
        val installer = ModelInstaller(storageManager)
        val evaluator = DeviceProfileEvaluator(context)
        val registry = TtsEngineRegistry(context)

        ttsRepository = TtsRepositoryImpl(
            modelDao = database.ttsModelDao(),
            voiceDao = database.ttsVoiceDao(),
            storageManager = storageManager,
            modelInstaller = installer,
            deviceEvaluator = evaluator,
            engineRegistry = registry
        )

        val player = AudioPreviewPlayer(context)
        voicePreviewManager = VoicePreviewManager(context, player)
        val userPrefsManager = com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context)

        viewModel = VoiceBrowserViewModel(ttsRepository, voicePreviewManager, userPrefsManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testVoiceFilteringBySearchAndGender() {
        runBlocking {
            val initialVoices = ttsRepository.getAllVoices().first()
            assertTrue(initialVoices.isNotEmpty())

            viewModel.onSearchQueryChanged("Heart")
            val state1 = viewModel.uiState.value
            assertTrue(state1.filteredVoices.all { it.name.contains("Heart", ignoreCase = true) })

            viewModel.onSearchQueryChanged("")
            viewModel.onGenderSelected(VoiceGender.FEMALE)
            val state2 = viewModel.uiState.value
            assertTrue(state2.filteredVoices.all { it.gender == VoiceGender.FEMALE })
        }
    }
}
