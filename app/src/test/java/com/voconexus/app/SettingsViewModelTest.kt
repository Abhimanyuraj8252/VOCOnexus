package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.error.DiagnosticsExporter
import com.voconexus.app.core.preferences.QualityPreset
import com.voconexus.app.core.preferences.ThemeMode
import com.voconexus.app.core.preferences.UserPreferencesManager
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var userPrefsManager: UserPreferencesManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPrefsManager = UserPreferencesManager.getInstance(context)
        val audioStorageManager = AudioStorageManager(context)
        val storageManager = StorageManager(context)
        val diagnosticsExporter = DiagnosticsExporter(context, storageManager)

        viewModel = SettingsViewModel(
            userPreferencesManager = userPrefsManager,
            storageManager = audioStorageManager,
            diagnosticsExporter = diagnosticsExporter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testThemeModeUpdate() {
        viewModel.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, userPrefsManager.preferences.value.themeMode)
    }

    @Test
    fun testQualityPresetUpdate() {
        viewModel.setQualityPreset(QualityPreset.HIGH_QUALITY)
        assertEquals(QualityPreset.HIGH_QUALITY, userPrefsManager.preferences.value.qualityPreset)
    }

    @Test
    fun testGenerateDiagnosticReport() {
        viewModel.generateDiagnosticReport()
        assertNotNull(viewModel.uiState.value.lastDiagnosticReport)
    }
}
