package com.voconexus.app.ui.screens.speechcontrols

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.domain.DurationEstimator
import com.voconexus.app.core.domain.duration.TargetDurationPlan
import com.voconexus.app.core.domain.duration.TargetDurationPlanner
import com.voconexus.app.core.domain.speech.NaturalnessLevel
import com.voconexus.app.core.domain.speech.PitchConfig
import com.voconexus.app.core.domain.speech.SpeechSpeedConfig
import com.voconexus.app.core.domain.speech.TargetDurationConfig
import com.voconexus.app.core.domain.speech.calculateNaturalness
import com.voconexus.app.core.tts.preview.SpeechPreviewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpeechControlsUiState(
    val projectId: String = "",
    val speed: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val targetDurationMs: Long = 0L,
    val durationMode: String = "OFF",
    val estimatedDurationMs: Long = 0L,
    val targetPlan: TargetDurationPlan? = null,
    val naturalnessLevel: NaturalnessLevel = NaturalnessLevel.HIGH,
    val speedWarning: String? = null,
    val pitchWarning: String? = null,
    val isPreviewPlaying: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class SpeechControlsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val durationEstimator: DurationEstimator,
    private val targetPlanner: TargetDurationPlanner,
    private val previewManager: SpeechPreviewManager,
    private val prefsManager: com.voconexus.app.core.preferences.UserPreferencesManager,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeechControlsUiState(projectId = projectId))
    val uiState: StateFlow<SpeechControlsUiState> = _uiState.asStateFlow()

    init {
        loadProjectSettings()
    }

    private fun loadProjectSettings() {
        viewModelScope.launch(ioDispatcher) {
            projectRepository.getProjectById(projectId).collect { project ->
                if (project != null) {
                    val estimated = project.estimatedDurationMs
                    val speedConfig = SpeechSpeedConfig(project.speed)
                    val pitchConfig = PitchConfig(project.pitch)
                    val plan = if (project.targetDurationMs > 0L) {
                        targetPlanner.planTargetDuration(estimated, project.targetDurationMs, project.speed, project.pitch)
                    } else null

                    val naturalness = calculateNaturalness(project.speed, project.pitch, plan?.requiredRatio ?: 1.0f)

                    _uiState.value = _uiState.value.copy(
                        speed = project.speed,
                        pitchSemitones = project.pitch,
                        targetDurationMs = project.targetDurationMs,
                        durationMode = project.durationMode,
                        estimatedDurationMs = estimated,
                        targetPlan = plan,
                        naturalnessLevel = naturalness,
                        speedWarning = speedConfig.warningMessage,
                        pitchWarning = pitchConfig.warningMessage
                    )
                }
            }
        }
    }

    fun updateSpeed(newSpeed: Float) {
        val speedConfig = SpeechSpeedConfig(newSpeed)
        if (!speedConfig.isValid) return

        val plan = if (_uiState.value.targetDurationMs > 0L) {
            targetPlanner.planTargetDuration(_uiState.value.estimatedDurationMs, _uiState.value.targetDurationMs, newSpeed, _uiState.value.pitchSemitones)
        } else null

        val naturalness = calculateNaturalness(newSpeed, _uiState.value.pitchSemitones, plan?.requiredRatio ?: 1.0f)

        _uiState.value = _uiState.value.copy(
            speed = newSpeed,
            targetPlan = plan,
            naturalnessLevel = naturalness,
            speedWarning = speedConfig.warningMessage
        )
        saveSettings()
    }

    fun updatePitch(newPitch: Float) {
        val pitchConfig = PitchConfig(newPitch)
        if (!pitchConfig.isValid) return

        val naturalness = calculateNaturalness(_uiState.value.speed, newPitch, _uiState.value.targetPlan?.requiredRatio ?: 1.0f)

        _uiState.value = _uiState.value.copy(
            pitchSemitones = newPitch,
            naturalnessLevel = naturalness,
            pitchWarning = pitchConfig.warningMessage
        )
        saveSettings()
    }

    fun updateTargetDuration(targetMs: Long) {
        val mode = if (targetMs > 0L) "TARGET" else "OFF"
        val plan = if (targetMs > 0L) {
            targetPlanner.planTargetDuration(_uiState.value.estimatedDurationMs, targetMs, _uiState.value.speed, _uiState.value.pitchSemitones)
        } else null

        val naturalness = calculateNaturalness(_uiState.value.speed, _uiState.value.pitchSemitones, plan?.requiredRatio ?: 1.0f)

        _uiState.value = _uiState.value.copy(
            targetDurationMs = targetMs,
            durationMode = mode,
            targetPlan = plan,
            naturalnessLevel = naturalness
        )
        saveSettings()
    }

    fun playPreview(voiceId: String = "af_heart") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreviewPlaying = true)
            try {
                val isKokoroVoice = voiceId.startsWith("hf_") || voiceId.startsWith("hm_") ||
                    voiceId.startsWith("af_") || voiceId.startsWith("am_") ||
                    voiceId.startsWith("bf_") || voiceId.startsWith("bm_") ||
                    voiceId.startsWith("ff_") || voiceId.startsWith("ef_") ||
                    voiceId.startsWith("em_") || voiceId.startsWith("if_") ||
                    voiceId.startsWith("jf_") || voiceId.startsWith("zf_") || voiceId.startsWith("zm_") ||
                    voiceId.contains("alpha", ignoreCase = true) || voiceId.contains("beta", ignoreCase = true) ||
                    voiceId.contains("omega", ignoreCase = true) || voiceId.contains("psi", ignoreCase = true)

                val targetEngineId = if (isKokoroVoice) "kokoro-v1.0" else prefsManager.preferences.value.defaultEngineId
                val success = previewManager.playSpeechPreview(
                    voiceId = voiceId,
                    engineId = targetEngineId,
                    speed = _uiState.value.speed,
                    pitchSemitones = _uiState.value.pitchSemitones
                )
                _uiState.value = _uiState.value.copy(
                    isPreviewPlaying = false,
                    infoMessage = if (success) "Playing preview..." else "Preview playback failed"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPreviewPlaying = false,
                    errorMessage = e.message ?: "Failed to play preview"
                )
            }
        }
    }

    fun stopPreview() {
        previewManager.stopPreview()
        _uiState.value = _uiState.value.copy(isPreviewPlaying = false)
    }

    private fun saveSettings() {
        viewModelScope.launch(ioDispatcher) {
            projectRepository.updateProjectSpeechSettings(
                id = projectId,
                speed = _uiState.value.speed,
                pitch = _uiState.value.pitchSemitones,
                targetDurationMs = _uiState.value.targetDurationMs,
                durationMode = _uiState.value.durationMode
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    class Factory(
        private val projectId: String,
        private val projectRepository: ProjectRepository,
        private val durationEstimator: DurationEstimator,
        private val targetPlanner: TargetDurationPlanner,
        private val previewManager: SpeechPreviewManager,
        private val prefsManager: com.voconexus.app.core.preferences.UserPreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeechControlsViewModel(
                projectId = projectId,
                projectRepository = projectRepository,
                durationEstimator = durationEstimator,
                targetPlanner = targetPlanner,
                previewManager = previewManager,
                prefsManager = prefsManager
            ) as T
        }
    }
}
