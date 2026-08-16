package com.voconexus.app.ui.screens.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.repository.TtsRepository
import com.voconexus.app.core.preferences.UserPreferencesManager
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.TtsVoice
import com.voconexus.app.core.tts.VoiceGender
import com.voconexus.app.core.tts.preview.VoicePreviewManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoiceBrowserUiState(
    val voices: List<TtsVoice> = emptyList(),
    val filteredVoices: List<TtsVoice> = emptyList(),
    val allModels: List<TtsModel> = emptyList(),
    val searchQuery: String = "",
    val selectedLanguage: String = "ALL",
    val selectedGender: VoiceGender? = null,
    val selectedEngineId: String = "ALL",
    val activeModelId: String = "kokoro-v1.0",
    val activeEngineId: String = "kokoro-82m",
    val isPlayingPreview: Boolean = false,
    val playingVoiceId: String? = null,
    val isGeneratingPreview: Boolean = false,
    val errorMessage: String? = null
)

// Language option with flag emoji
data class LangFilterOption(
    val code: String,
    val displayName: String,
    val flag: String,
    val nativeName: String = ""
)

class VoiceBrowserViewModel(
    private val ttsRepository: TtsRepository,
    private val voicePreviewManager: VoicePreviewManager,
    private val prefsManager: UserPreferencesManager
) : ViewModel() {

    val allLanguages = listOf(
        LangFilterOption("ALL",          "All",           "🌐"),
        LangFilterOption("MULTILINGUAL", "Multilingual",  "🔀"),
        LangFilterOption("en",           "English",       "🇺🇸"),
        LangFilterOption("hi",           "Hindi",         "🇮🇳", "हिन्दी"),
        LangFilterOption("fr",           "French",        "🇫🇷", "Français"),
        LangFilterOption("es",           "Spanish",       "🇪🇸", "Español"),
        LangFilterOption("de",           "German",        "🇩🇪", "Deutsch"),
        LangFilterOption("it",           "Italian",       "🇮🇹", "Italiano"),
        LangFilterOption("pt",           "Portuguese",    "🇧🇷", "Português"),
        LangFilterOption("ja",           "Japanese",      "🇯🇵", "日本語"),
        LangFilterOption("ko",           "Korean",        "🇰🇷", "한국어"),
        LangFilterOption("zh",           "Chinese",       "🇨🇳", "中文"),
        LangFilterOption("ar",           "Arabic",        "🇸🇦", "العربية"),
        LangFilterOption("ru",           "Russian",       "🇷🇺", "Русский"),
        LangFilterOption("nl",           "Dutch",         "🇳🇱", "Nederlands"),
        LangFilterOption("pl",           "Polish",        "🇵🇱", "Polski"),
        LangFilterOption("tr",           "Turkish",       "🇹🇷", "Türkçe"),
        LangFilterOption("uk",           "Ukrainian",     "🇺🇦", "Українська"),
        LangFilterOption("vi",           "Vietnamese",    "🇻🇳", "Tiếng Việt"),
        LangFilterOption("el",           "Greek",         "🇬🇷", "Ελληνικά"),
        LangFilterOption("sv",           "Swedish",       "🇸🇪", "Svenska"),
        LangFilterOption("da",           "Danish",        "🇩🇰", "Dansk"),
        LangFilterOption("nb",           "Norwegian",     "🇳🇴", "Norsk"),
        LangFilterOption("fi",           "Finnish",       "🇫🇮", "Suomi"),
        LangFilterOption("cs",           "Czech",         "🇨🇿", "Čeština"),
        LangFilterOption("ro",           "Romanian",      "🇷🇴", "Română"),
        LangFilterOption("hu",           "Hungarian",     "🇭🇺", "Magyar"),
        LangFilterOption("sk",           "Slovak",        "🇸🇰", "Slovenčina"),
        LangFilterOption("ca",           "Catalan",       "🏳️", "Català"),
        LangFilterOption("sr",           "Serbian",       "🇷🇸", "Српски"),
        LangFilterOption("hr",           "Croatian",      "🇭🇷", "Hrvatski"),
        LangFilterOption("is",           "Icelandic",     "🇮🇸", "Íslenska"),
        LangFilterOption("af",           "Afrikaans",     "🇿🇦"),
        LangFilterOption("sw",           "Swahili",       "🇰🇪"),
        LangFilterOption("bn",           "Bengali",       "🇧🇩", "বাংলা"),
        LangFilterOption("gu",           "Gujarati",      "🇮🇳", "ગુજરાતી"),
        LangFilterOption("te",           "Telugu",        "🇮🇳", "తెలుగు"),
        LangFilterOption("ta",           "Tamil",         "🇮🇳", "தமிழ்"),
        LangFilterOption("kn",           "Kannada",       "🇮🇳", "ಕನ್ನಡ"),
        LangFilterOption("mr",           "Marathi",       "🇮🇳", "मराठी"),
        LangFilterOption("pa",           "Punjabi",       "🇮🇳", "ਪੰਜਾਬੀ"),
    )

    private val _uiState = MutableStateFlow(VoiceBrowserUiState())
    val uiState: StateFlow<VoiceBrowserUiState> = _uiState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceBrowserUiState()
    )

    init {
        observeVoicesAndPrefs()
        observePlaybackState()
        loadModels()
    }

    private fun observeVoicesAndPrefs() {
        viewModelScope.launch {
            combine(
                ttsRepository.getAllVoices(),
                prefsManager.preferences
            ) { voices, prefs ->
                Triple(voices, prefs.selectedModelId, prefs.defaultEngineId)
            }.collect { (voices, activeModelId, activeEngineId) ->
                _uiState.value = _uiState.value.copy(
                    voices = voices,
                    activeModelId = activeModelId,
                    activeEngineId = activeEngineId
                )
                applyFilters()
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            combine(
                voicePreviewManager.previewPlayer.isPlaying,
                voicePreviewManager.previewPlayer.activeVoiceId
            ) { isPlaying, activeVoiceId ->
                Pair(isPlaying, activeVoiceId)
            }.collect { (isPlaying, activeVoiceId) ->
                _uiState.value = _uiState.value.copy(
                    isPlayingPreview = isPlaying,
                    playingVoiceId = activeVoiceId
                )
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            ttsRepository.getAllModels().collect { models ->
                _uiState.value = _uiState.value.copy(allModels = models)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onLanguageSelected(language: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
        applyFilters()
    }

    fun onGenderSelected(gender: VoiceGender?) {
        _uiState.value = _uiState.value.copy(selectedGender = gender)
        applyFilters()
    }

    fun onEngineSelected(engineId: String) {
        _uiState.value = _uiState.value.copy(selectedEngineId = engineId)
        applyFilters()
    }

    fun setActiveModel(modelId: String) {
        val model = _uiState.value.allModels.find { it.id == modelId }
        val engineId = model?.engineId ?: "google-cloud-tts"
        prefsManager.setSelectedModel(modelId, engineId)
        _uiState.value = _uiState.value.copy(
            activeModelId = modelId,
            activeEngineId = engineId
        )
        applyFilters()
    }

    private fun applyFilters() {
        val current = _uiState.value
        var result = current.voices

        // Engine/Model filter: STRICTLY show voices from the active engine only.
        if (current.activeEngineId.isNotBlank() && current.activeEngineId != "ALL") {
            result = result.filter { 
                it.engineId == current.activeEngineId 
            }
        } else if (current.activeModelId.isNotBlank() && current.activeModelId != "ALL") {
            result = result.filter { 
                it.modelId == current.activeModelId
            }
        }

        if (current.searchQuery.isNotBlank()) {
            result = result.filter {
                it.name.contains(current.searchQuery, ignoreCase = true) ||
                    it.language.contains(current.searchQuery, ignoreCase = true) ||
                    it.locale.contains(current.searchQuery, ignoreCase = true) ||
                    it.id.contains(current.searchQuery, ignoreCase = true)
            }
        }

        if (current.selectedLanguage != "ALL") {
            result = when (current.selectedLanguage) {
                "MULTILINGUAL" -> result.filter {
                    it.language.contains("hi", ignoreCase = true) ||
                        it.language.contains("+", ignoreCase = true) ||
                        it.locale.contains("IN", ignoreCase = true) ||
                        it.name.contains("Multilingual", ignoreCase = true) ||
                        it.name.contains("Hindi", ignoreCase = true) ||
                        it.name.contains("Indian", ignoreCase = true) ||
                        it.id.contains("multi", ignoreCase = true) ||
                        it.id.contains("hi-IN", ignoreCase = true) ||
                        it.id.contains("en-IN", ignoreCase = true) ||
                        it.id.contains("g_hi", ignoreCase = true) ||
                        it.id.contains("g_en_in", ignoreCase = true) ||
                        it.id.contains("hf_", ignoreCase = true) ||
                        it.id.contains("hm_", ignoreCase = true) ||
                        it.id.contains("sherpa_", ignoreCase = true) ||
                        it.id.contains("piper_", ignoreCase = true)
                }
                "hi" -> result.filter {
                    it.language.equals("hi", ignoreCase = true) ||
                        it.name.contains("Hindi", ignoreCase = true) ||
                        it.name.contains("Swara", ignoreCase = true) ||
                        it.name.contains("Madhur", ignoreCase = true) ||
                        it.name.contains("Neerja", ignoreCase = true) ||
                        it.name.contains("Prabhat", ignoreCase = true) ||
                        it.name.contains("Ava", ignoreCase = true) ||
                        it.name.contains("Andrew", ignoreCase = true) ||
                        it.name.contains("Emma", ignoreCase = true) ||
                        it.name.contains("Brian", ignoreCase = true) ||
                        it.id.contains("hf_", ignoreCase = true) ||
                        it.id.contains("hm_", ignoreCase = true) ||
                        it.id.contains("sherpa_", ignoreCase = true)
                }
                "en" -> result.filter {
                    it.language.equals("en", ignoreCase = true) ||
                        it.name.contains("English", ignoreCase = true) ||
                        it.name.contains("Multilingual", ignoreCase = true)
                }
                else -> result.filter {
                    it.language.equals(current.selectedLanguage, ignoreCase = true) ||
                        it.locale.contains(current.selectedLanguage, ignoreCase = true)
                }
            }
        }

        if (current.selectedGender != null) {
            result = result.filter { it.gender == current.selectedGender }
        }

        if (current.selectedEngineId != "ALL") {
            result = result.filter { it.engineId == current.selectedEngineId }
        }

        _uiState.value = current.copy(
            filteredVoices = result.sortedWith(compareBy({ it.language }, { it.name }))
        )
    }

    fun playVoicePreview(voice: TtsVoice) {
        viewModelScope.launch {
            if (_uiState.value.playingVoiceId == voice.id && _uiState.value.isPlayingPreview) {
                voicePreviewManager.stopPreview()
                return@launch
            }

            _uiState.value = _uiState.value.copy(isGeneratingPreview = true, playingVoiceId = voice.id)
            try {
                val engine = ttsRepository.getEngine(voice.engineId)
                val model = ttsRepository.getModelById(voice.modelId)
                if (model?.installedPath != null) {
                    engine.loadModel(voice.modelId, model.installedPath)
                }

                voicePreviewManager.generateAndPlayPreview(engine, voice)
                _uiState.value = _uiState.value.copy(isGeneratingPreview = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingPreview = false,
                    errorMessage = e.message ?: "Failed to generate preview"
                )
            }
        }
    }

    fun stopPreview() {
        voicePreviewManager.stopPreview()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        voicePreviewManager.stopPreview()
    }

    companion object {
        fun provideFactory(
            ttsRepository: TtsRepository,
            voicePreviewManager: VoicePreviewManager,
            prefsManager: UserPreferencesManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Use a default no-op prefs manager if not provided (backward compat)
                val pm = prefsManager ?: throw IllegalStateException("UserPreferencesManager is required")
                return VoiceBrowserViewModel(ttsRepository, voicePreviewManager, pm) as T
            }
        }
    }
}
