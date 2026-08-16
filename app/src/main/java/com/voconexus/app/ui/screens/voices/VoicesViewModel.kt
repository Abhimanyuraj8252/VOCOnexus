package com.voconexus.app.ui.screens.voices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.TtsVoiceEntity
import com.voconexus.app.core.data.repository.VoiceRepository
import com.voconexus.app.core.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanguageOption(
    val code: String,
    val displayName: String,
    val flag: String = "",
    val nativeName: String = ""
)

class VoicesViewModel(
    private val voiceRepository: VoiceRepository,
    private val prefsManager: UserPreferencesManager
) : ViewModel() {

    // All supported languages across Kokoro + Piper
    val availableLanguages = listOf(
        LanguageOption("ALL",          "All Languages",            "🌐", "All"),
        LanguageOption("MULTILINGUAL", "Multilingual",             "🔀", "Multi-Lang"),
        LanguageOption("en",           "English",                  "🇺🇸", "English"),
        LanguageOption("hi",           "Hindi",                    "🇮🇳", "हिन्दी"),
        LanguageOption("fr",           "French",                   "🇫🇷", "Français"),
        LanguageOption("es",           "Spanish",                  "🇪🇸", "Español"),
        LanguageOption("de",           "German",                   "🇩🇪", "Deutsch"),
        LanguageOption("it",           "Italian",                  "🇮🇹", "Italiano"),
        LanguageOption("pt",           "Portuguese",               "🇧🇷", "Português"),
        LanguageOption("ja",           "Japanese",                 "🇯🇵", "日本語"),
        LanguageOption("ko",           "Korean",                   "🇰🇷", "한국어"),
        LanguageOption("zh",           "Chinese",                  "🇨🇳", "中文"),
        LanguageOption("ar",           "Arabic",                   "🇸🇦", "العربية"),
        LanguageOption("ru",           "Russian",                  "🇷🇺", "Русский"),
        LanguageOption("nl",           "Dutch",                    "🇳🇱", "Nederlands"),
        LanguageOption("pl",           "Polish",                   "🇵🇱", "Polski"),
        LanguageOption("tr",           "Turkish",                  "🇹🇷", "Türkçe"),
        LanguageOption("uk",           "Ukrainian",                "🇺🇦", "Українська"),
        LanguageOption("vi",           "Vietnamese",               "🇻🇳", "Tiếng Việt"),
        LanguageOption("el",           "Greek",                    "🇬🇷", "Ελληνικά"),
        LanguageOption("cs",           "Czech",                    "🇨🇿", "Čeština"),
        LanguageOption("fi",           "Finnish",                  "🇫🇮", "Suomi"),
        LanguageOption("ro",           "Romanian",                 "🇷🇴", "Română"),
        LanguageOption("hu",           "Hungarian",                "🇭🇺", "Magyar"),
        LanguageOption("sk",           "Slovak",                   "🇸🇰", "Slovenčina"),
        LanguageOption("ca",           "Catalan",                  "🏳️", "Català"),
        LanguageOption("sr",           "Serbian",                  "🇷🇸", "Српски"),
        LanguageOption("hr",           "Croatian",                 "🇭🇷", "Hrvatski"),
        LanguageOption("da",           "Danish",                   "🇩🇰", "Dansk"),
        LanguageOption("nb",           "Norwegian",                "🇳🇴", "Norsk"),
        LanguageOption("sv",           "Swedish",                  "🇸🇪", "Svenska"),
        LanguageOption("is",           "Icelandic",                "🇮🇸", "Íslenska"),
        LanguageOption("af",           "Afrikaans",                "🇿🇦", "Afrikaans"),
        LanguageOption("sw",           "Swahili",                  "🇰🇪", "Kiswahili"),
        LanguageOption("bn",           "Bengali",                  "🇧🇩", "বাংলা"),
        LanguageOption("gu",           "Gujarati",                 "🇮🇳", "ગુજરાતી"),
        LanguageOption("te",           "Telugu",                   "🇮🇳", "తెలుగు"),
        LanguageOption("ta",           "Tamil",                    "🇮🇳", "தமிழ்"),
        LanguageOption("kn",           "Kannada",                  "🇮🇳", "ಕನ್ನಡ"),
        LanguageOption("mr",           "Marathi",                  "🇮🇳", "मराठी"),
        LanguageOption("pa",           "Punjabi",                  "🇮🇳", "ਪੰਜਾਬੀ"),
    )

    private val _selectedLanguage = MutableStateFlow("ALL")
    val selectedLanguage: StateFlow<String> = _selectedLanguage

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Active selected model (from preferences)
    val selectedModelId: StateFlow<String> = combine(
        prefsManager.preferences,
        MutableStateFlow(Unit)
    ) { prefs, _ -> prefs.selectedModelId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "kokoro-v1.0")

    val voicesState: StateFlow<List<TtsVoiceEntity>> = combine(
        voiceRepository.getAllVoices(),
        _selectedLanguage,
        _searchQuery,
        prefsManager.preferences
    ) { allVoices, langFilter, query, prefs ->
        val activeModelId = prefs.selectedModelId
        val activeEngineId = prefs.defaultEngineId

        allVoices.filter { voice ->
            // Engine filter: show ONLY voices from the active engine
            val matchesEngine = activeEngineId.isEmpty() || 
                activeEngineId == "ALL" ||
                voice.engineId == activeEngineId

            val matchesQuery = query.isBlank() ||
                voice.name.contains(query, ignoreCase = true) ||
                voice.language.contains(query, ignoreCase = true) ||
                voice.locale.contains(query, ignoreCase = true) ||
                voice.id.contains(query, ignoreCase = true)

            val matchesLang = when (langFilter) {
                "ALL"          -> true
                "MULTILINGUAL" -> voice.name.contains("Multilingual", ignoreCase = true) ||
                    voice.name.contains("Multi", ignoreCase = true) ||
                    voice.name.contains("Hindi", ignoreCase = true) ||
                    voice.language.contains("+", ignoreCase = true) ||
                    voice.locale.contains("IN", ignoreCase = true) ||
                    voice.language == "hi" ||
                    voice.id.contains("hf_", ignoreCase = true) ||
                    voice.id.contains("hm_", ignoreCase = true) ||
                    voice.id.contains("sherpa_", ignoreCase = true)
                "hi" -> voice.language.equals("hi", ignoreCase = true) ||
                    voice.name.contains("Hindi", ignoreCase = true) ||
                    voice.name.contains("Swara", ignoreCase = true) ||
                    voice.name.contains("Madhur", ignoreCase = true) ||
                    voice.name.contains("Neerja", ignoreCase = true) ||
                    voice.name.contains("Prabhat", ignoreCase = true) ||
                    voice.name.contains("Ava", ignoreCase = true) ||
                    voice.name.contains("Andrew", ignoreCase = true) ||
                    voice.id.contains("hf_", ignoreCase = true) ||
                    voice.id.contains("hm_", ignoreCase = true) ||
                    voice.id.contains("sherpa_", ignoreCase = true)
                else           -> voice.language.equals(langFilter, ignoreCase = true) ||
                    voice.locale.contains(langFilter, ignoreCase = true)
            }

            matchesEngine && matchesQuery && matchesLang
        }.sortedWith(
            compareBy(
                { it.language },
                { it.name }
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectLanguageFilter(code: String) {
        _selectedLanguage.value = code
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveModel(modelId: String) {
        prefsManager.setSelectedModel(modelId)
    }

    fun playVoicePreview(context: Context, voiceEntity: TtsVoiceEntity) {
        viewModelScope.launch {
            try {
                val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                container.speechPreviewManager.playSpeechPreview(
                    sampleText = buildPreviewText(voiceEntity),
                    voiceId = voiceEntity.id,
                    engineId = voiceEntity.engineId
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildPreviewText(voice: TtsVoiceEntity): String {
        return when (voice.language.lowercase()) {
            "hi"  -> "नमस्ते! मैं वोकोनेक्सस का एक ऑफलाइन आवाज हूं।"
            "fr"  -> "Bonjour! Je suis une voix de VocoNexus."
            "es"  -> "¡Hola! Soy una voz offline de VocoNexus."
            "de"  -> "Hallo! Ich bin eine Offline-Stimme von VocoNexus."
            "ja"  -> "こんにちは！私はVocoNexusの音声です。"
            "ko"  -> "안녕하세요! 저는 VocoNexus 음성입니다."
            "zh"  -> "你好！我是VocoNexus的离线语音。"
            "it"  -> "Ciao! Sono una voce offline di VocoNexus."
            "pt"  -> "Olá! Eu sou uma voz offline do VocoNexus."
            "ar"  -> "مرحباً! أنا صوت VocoNexus."
            "ru"  -> "Привет! Я голос VocoNexus."
            "nl"  -> "Hallo! Ik ben een VocoNexus stem."
            "pl"  -> "Cześć! Jestem głosem VocoNexus."
            "tr"  -> "Merhaba! Ben bir VocoNexus sesiyim."
            "uk"  -> "Привіт! Я голос VocoNexus."
            "vi"  -> "Xin chào! Tôi là giọng nói VocoNexus."
            "el"  -> "Γεια σας! Είμαι φωνή VocoNexus."
            "sv"  -> "Hej! Jag är en VocoNexus-röst."
            "da"  -> "Hej! Jeg er en VocoNexus-stemme."
            "nb"  -> "Hei! Jeg er en VocoNexus-stemme."
            "fi"  -> "Hei! Olen VocoNexus-ääni."
            "bn"  -> "হ্যালো! আমি ভোকোনেক্সাসের একটি কণ্ঠস্বর।"
            "sw"  -> "Habari! Mimi ni sauti ya VocoNexus."
            else  -> "Hello! This is a preview of the ${voice.name} voice from VocoNexus."
        }
    }

    class Factory(
        private val voiceRepository: VoiceRepository,
        private val prefsManager: UserPreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoicesViewModel(voiceRepository, prefsManager) as T
        }
    }
}
