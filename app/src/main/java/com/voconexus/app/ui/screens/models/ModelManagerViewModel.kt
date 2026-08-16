package com.voconexus.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.repository.TtsRepository
import com.voconexus.app.core.preferences.UserPreferencesManager
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.device.BenchmarkResult
import com.voconexus.app.core.tts.device.CompatibilityReport
import com.voconexus.app.core.tts.device.DeviceProfile
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelManagerUiState(
    val models: List<TtsModel> = emptyList(),
    val deviceProfile: DeviceProfile? = null,
    val compatibilityReports: Map<String, CompatibilityReport> = emptyMap(),
    val isInstalling: Boolean = false,
    val installingModelId: String? = null,
    val installProgress: Float = 0f,
    val isRunningBenchmark: Boolean = false,
    val lastBenchmarkResult: BenchmarkResult? = null,
    val activeModelId: String = "kokoro-v1.0",  // Currently globally selected model
    val errorMessage: String? = null
)

class ModelManagerViewModel(
    private val ttsRepository: TtsRepository,
    private val deviceEvaluator: DeviceProfileEvaluator,
    private val prefsManager: UserPreferencesManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    init {
        loadData()
        // Load active model from prefs
        prefsManager?.preferences?.value?.selectedModelId?.let { savedId ->
            if (savedId.isNotBlank()) {
                _uiState.value = _uiState.value.copy(activeModelId = savedId)
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            val profile = ttsRepository.getDeviceProfile()
            ttsRepository.getAllModels().collect { models ->
                val reports = models.associate { model ->
                    model.id to deviceEvaluator.classifyCompatibility(profile, model.minRamMb)
                }

                _uiState.value = _uiState.value.copy(
                    models = models,
                    deviceProfile = profile,
                    compatibilityReports = reports
                )
            }
        }
    }

    fun installModel(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isInstalling = true,
                installingModelId = modelId,
                installProgress = 0f,
                errorMessage = null
            )

            try {
                ttsRepository.installModel(modelId) { progress ->
                    _uiState.value = _uiState.value.copy(installProgress = progress)
                }
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    installingModelId = null,
                    installProgress = 1.0f
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    installingModelId = null,
                    errorMessage = e.message ?: "Failed to install model"
                )
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            try {
                ttsRepository.deleteModel(modelId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Failed to delete model")
            }
        }
    }

    fun runBenchmark(modelId: String, voiceId: String = "af_heart") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunningBenchmark = true)
            try {
                val result = ttsRepository.runBenchmark(modelId, voiceId)
                _uiState.value = _uiState.value.copy(
                    isRunningBenchmark = false,
                    lastBenchmarkResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningBenchmark = false,
                    errorMessage = e.message ?: "Benchmark failed"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setActiveModel(modelId: String) {
        val model = _uiState.value.models.find { it.id == modelId }
        prefsManager?.setSelectedModel(modelId, model?.engineId)
        _uiState.value = _uiState.value.copy(activeModelId = modelId)
    }

    companion object {
        fun provideFactory(
            ttsRepository: TtsRepository,
            deviceEvaluator: DeviceProfileEvaluator,
            prefsManager: UserPreferencesManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ModelManagerViewModel(ttsRepository, deviceEvaluator, prefsManager) as T
            }
        }
    }
}
