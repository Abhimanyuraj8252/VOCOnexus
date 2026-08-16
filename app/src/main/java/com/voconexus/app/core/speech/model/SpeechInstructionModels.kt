package com.voconexus.app.core.speech.model

enum class SpeechInstructionType {
    PRONUNCIATION,
    PAUSE,
    EMPHASIS,
    RATE,
    PITCH,
    NUMBER_FORMAT,
    SYMBOL_EXPANSION
}

enum class PronunciationScope {
    GLOBAL,
    PROJECT,
    DOCUMENT
}

enum class NormalizationProfile {
    NATURAL_NARRATION,
    EXACT_TEXT
}

data class PronunciationEntry(
    val id: String,
    val matchText: String,
    val replacement: String,
    val languageCode: String = "en",
    val scope: PronunciationScope = PronunciationScope.PROJECT,
    val voiceId: String? = null,
    val isCaseSensitive: Boolean = false,
    val isWholeWord: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class SpeechInstruction(
    val type: SpeechInstructionType,
    val value: String,
    val startOffset: Int = 0,
    val endOffset: Int = 0
)

data class SpeechPlan(
    val rawText: String,
    val normalizedText: String,
    val instructions: List<SpeechInstruction> = emptyList(),
    val speechPlanHash: String = "",
    val profile: NormalizationProfile = NormalizationProfile.NATURAL_NARRATION
)
