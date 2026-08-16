package com.voconexus.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.error.DiagnosticsExporter
import com.voconexus.app.core.preferences.QualityPreset
import com.voconexus.app.core.preferences.ThemeMode
import com.voconexus.app.core.preferences.UserPreferencesManager
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isHighContrastEnabled: Boolean = false,
    val qualityPreset: QualityPreset = QualityPreset.BALANCED,
    val defaultPlaybackSpeed: Float = 1.0f,
    val autoPlayNextChunk: Boolean = true,
    val isWifiOnlyDownloads: Boolean = true,
    val isNotificationsEnabled: Boolean = true,
    val defaultEngineId: String = "kokoro-82m",
    val availableStorageBytes: Long = 0L,
    val lastDiagnosticReport: String? = null
)

class SettingsViewModel(
    private val userPreferencesManager: UserPreferencesManager,
    private val storageManager: AudioStorageManager,
    private val diagnosticsExporter: DiagnosticsExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            availableStorageBytes = storageManager.getAvailableStorageBytes()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        userPreferencesManager.preferences.onEach { prefs ->
            _uiState.value = _uiState.value.copy(
                themeMode = prefs.themeMode,
                isHighContrastEnabled = prefs.isHighContrastEnabled,
                qualityPreset = prefs.qualityPreset,
                defaultPlaybackSpeed = prefs.defaultPlaybackSpeed,
                autoPlayNextChunk = prefs.autoPlayNextChunk,
                isWifiOnlyDownloads = prefs.isWifiOnlyDownloads,
                isNotificationsEnabled = prefs.isNotificationsEnabled,
                defaultEngineId = prefs.defaultEngineId
            )
        }.launchIn(viewModelScope)
    }

    fun setThemeMode(mode: ThemeMode) {
        userPreferencesManager.setThemeMode(mode)
    }

    fun toggleHighContrast(enabled: Boolean) {
        userPreferencesManager.setHighContrastEnabled(enabled)
    }

    fun setQualityPreset(preset: QualityPreset) {
        userPreferencesManager.setQualityPreset(preset)
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        userPreferencesManager.setWifiOnlyDownloads(enabled)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        userPreferencesManager.setNotificationsEnabled(enabled)
    }

    fun generateDiagnosticReport() {
        val report = diagnosticsExporter.generateDiagnosticReport()
        _uiState.value = _uiState.value.copy(lastDiagnosticReport = report)
    }

    fun resetAllData(context: Context) {
        userPreferencesManager.resetPreferencesToDefault()
        // Storage cleanup
        storageManager.clearAllTempAudioFiles()
    }

    class Factory(
        private val userPreferencesManager: UserPreferencesManager,
        private val storageManager: AudioStorageManager,
        private val diagnosticsExporter: DiagnosticsExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(userPreferencesManager, storageManager, diagnosticsExporter) as T
        }
    }
}
