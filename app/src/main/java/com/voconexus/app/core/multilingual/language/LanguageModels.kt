package com.voconexus.app.core.multilingual.language

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

enum class ScriptType {
    DEVANAGARI,
    LATIN,
    BENGALI,
    TAMIL,
    TELUGU,
    GUJARATI,
    OTHER
}

data class DetectedLanguage(
    val languageCode: String = "en",
    val scriptType: ScriptType = ScriptType.LATIN,
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val confidenceFraction: Float = 1.0f
)

data class LanguageSegment(
    val id: String,
    val chunkId: String = "",
    val sequenceIndex: Int = 0,
    val text: String,
    val languageCode: String,
    val speakerId: String? = null,
    val voiceId: String? = null,
    val engineId: String? = null,
    val modelId: String? = null
)

data class VoiceProfile(
    val voiceId: String,
    val engineId: String,
    val modelId: String,
    val languageCode: String,
    val displayName: String,
    val isInstalled: Boolean = true
)

data class VoiceRoutingReport(
    val totalSegments: Int = 0,
    val preferredVoiceCount: Int = 0,
    val fallbackVoiceCount: Int = 0,
    val unresolvedCount: Int = 0
)
