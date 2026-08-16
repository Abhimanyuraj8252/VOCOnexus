package com.voconexus.app.ui.screens.multilingual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.SpeakerMappingEntity
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.data.repository.VoiceRepository
import com.voconexus.app.core.multilingual.language.LanguageDetector
import com.voconexus.app.core.multilingual.language.LanguageSegment
import com.voconexus.app.core.multilingual.language.LanguageSegmenter
import com.voconexus.app.core.multilingual.language.VoiceProfile
import com.voconexus.app.core.multilingual.routing.VoiceRouter
import com.voconexus.app.core.multilingual.speaker.SpeakerParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class LanguageDistribution(
    val languageCode: String,
    val segmentCount: Int,
    val percentageFraction: Float
)

data class ReadinessError(
    val title: String,
    val description: String,
    val isBlocking: Boolean = true
)

data class LanguageVoiceUiState(
    val projectId: String = "",
    val languageDistributions: List<LanguageDistribution> = emptyList(),
    val speakerMappings: List<SpeakerMappingEntity> = emptyList(),
    val readinessErrors: List<ReadinessError> = emptyList(),
    val segments: List<LanguageSegment> = emptyList(),
    val availableVoices: List<VoiceProfile> = emptyList(),
    val projectDefaultVoice: VoiceProfile? = null,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class LanguageVoiceViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val voiceRepository: VoiceRepository,
    private val languageDetector: LanguageDetector = LanguageDetector(),
    private val segmenter: LanguageSegmenter = LanguageSegmenter(languageDetector),
    private val speakerParser: SpeakerParser = SpeakerParser(),
    private val voiceRouter: VoiceRouter = VoiceRouter(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguageVoiceUiState(projectId = projectId))
    val uiState: StateFlow<LanguageVoiceUiState> = _uiState.asStateFlow()

    init {
        loadDataAndAnalyze()
    }

    private fun loadDataAndAnalyze() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            val chunks = projectRepository.getChunksForProjectDirect(projectId)
            val allText = chunks.joinToString("\n") { it.normalizedText }

            val segments = segmenter.segmentText(allText)
            val langCounts = segments.groupingBy { it.languageCode }.eachCount()
            val totalSegs = segments.size.coerceAtLeast(1)

            val distributions = langCounts.map { (lang, count) ->
                LanguageDistribution(
                    languageCode = lang,
                    segmentCount = count,
                    percentageFraction = count.toFloat() / totalSegs.toFloat()
                )
            }.sortedByDescending { it.percentageFraction }

            val defaultVoice = VoiceProfile(
                voiceId = "af_heart",
                engineId = "kokoro-82m",
                modelId = "kokoro-82m-v1.0",
                languageCode = "en",
                displayName = "Heart (Kokoro English)",
                isInstalled = true
            )

            val voices = listOf(
                defaultVoice,
                VoiceProfile("af_bella", "kokoro-82m", "kokoro-82m-v1.0", "en", "Bella (English)", true),
                VoiceProfile("hi_female", "sherpa-onnx", "vits-hindi-v1.0", "hi", "Aarti (Hindi)", true),
                VoiceProfile("bn_female", "sherpa-onnx", "vits-bengali-v1.0", "bn", "Debjani (Bengali)", true)
            )

            _uiState.value = _uiState.value.copy(
                segments = segments,
                languageDistributions = distributions,
                availableVoices = voices,
                projectDefaultVoice = defaultVoice,
                isAnalyzing = false
            )

            runReadinessCheck()
        }
    }

    fun setSpeakerMapping(speakerId: String, voiceId: String) {
        val selectedVoice = _uiState.value.availableVoices.firstOrNull { it.voiceId == voiceId } ?: return
        val currentMappings = _uiState.value.speakerMappings.toMutableList()
        currentMappings.removeAll { it.speakerId == speakerId }
        currentMappings.add(
            SpeakerMappingEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                speakerId = speakerId,
                voiceId = selectedVoice.voiceId,
                engineId = selectedVoice.engineId,
                modelId = selectedVoice.modelId
            )
        )
        _uiState.value = _uiState.value.copy(speakerMappings = currentMappings)
        runReadinessCheck()
    }

    fun bulkAssignLanguageVoice(languageCode: String, voiceId: String) {
        val selectedVoice = _uiState.value.availableVoices.firstOrNull { it.voiceId == voiceId } ?: return
        val updatedSegments = _uiState.value.segments.map { seg ->
            if (seg.languageCode == languageCode) {
                seg.copy(voiceId = selectedVoice.voiceId, engineId = selectedVoice.engineId, modelId = selectedVoice.modelId)
            } else seg
        }
        _uiState.value = _uiState.value.copy(
            segments = updatedSegments,
            infoMessage = "Assigned '${selectedVoice.displayName}' to all $languageCode segments"
        )
        runReadinessCheck()
    }

    private fun runReadinessCheck() {
        val errors = mutableListOf<ReadinessError>()
        val defaultVoice = _uiState.value.projectDefaultVoice

        if (defaultVoice == null || !defaultVoice.isInstalled) {
            errors.add(ReadinessError("Default Voice Unavailable", "Project default voice is not installed.", true))
        }

        val unmappedLangs = _uiState.value.languageDistributions.filter { dist ->
            _uiState.value.availableVoices.none { it.languageCode == dist.languageCode && it.isInstalled }
        }

        for (lang in unmappedLangs) {
            errors.add(ReadinessError("Missing Model for '${lang.languageCode}'", "No installed model found for language '${lang.languageCode}'. Fallback voice will be used.", false))
        }

        _uiState.value = _uiState.value.copy(readinessErrors = errors)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    class Factory(
        private val projectId: String,
        private val projectRepository: ProjectRepository,
        private val voiceRepository: VoiceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LanguageVoiceViewModel(projectId, projectRepository, voiceRepository) as T
        }
    }
}
