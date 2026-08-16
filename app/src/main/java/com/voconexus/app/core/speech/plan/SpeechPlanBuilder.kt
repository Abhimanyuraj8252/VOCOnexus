package com.voconexus.app.core.speech.plan

import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.speech.model.NormalizationProfile
import com.voconexus.app.core.speech.model.PronunciationEntry
import com.voconexus.app.core.speech.model.SpeechInstruction
import com.voconexus.app.core.speech.model.SpeechInstructionType
import com.voconexus.app.core.speech.model.SpeechPlan
import com.voconexus.app.core.speech.normalization.TextNormalizationEngine
import com.voconexus.app.core.speech.pronunciation.PronunciationEngine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpeechPlanBuilder(
    private val normalizer: TextNormalizationEngine = TextNormalizationEngine(),
    private val pronunciationEngine: PronunciationEngine = PronunciationEngine()
) {

    companion object {
        private val PAUSE_TAG_PATTERN = "\\[Pause:\\s*(\\d+)\\s*ms\\]".toRegex(RegexOption.IGNORE_CASE)
    }

    suspend fun buildSpeechPlan(
        rawText: String,
        pronunciationRules: List<PronunciationEntry> = emptyList(),
        profile: NormalizationProfile = NormalizationProfile.NATURAL_NARRATION,
        languageCode: String = "en",
        voiceId: String? = null
    ): SpeechPlan = withContext(Dispatchers.Default) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return@withContext SpeechPlan(rawText = "", normalizedText = "", profile = profile)
        }

        // 1. Text Normalization
        val normalized = normalizer.normalizeText(trimmed, profile, languageCode)

        // 2. Pronunciation Rules
        val pronouncedText = pronunciationEngine.applyPronunciationRules(normalized, pronunciationRules, languageCode, voiceId)

        // 3. Pause Instruction Extraction
        val instructions = mutableListOf<SpeechInstruction>()
        val pauseMatches = PAUSE_TAG_PATTERN.findAll(pronouncedText)
        for (match in pauseMatches) {
            val pauseMs = match.groupValues[1]
            instructions.add(
                SpeechInstruction(
                    type = SpeechInstructionType.PAUSE,
                    value = pauseMs,
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1
                )
            )
        }

        // Clean pause tags from actual spoken string if any
        val cleanSpokenText = PAUSE_TAG_PATTERN.replace(pronouncedText, "").replace("\\s+".toRegex(), " ").trim()

        // 4. Deterministic Hash
        val hashContent = "$cleanSpokenText|${profile.name}|$languageCode|${instructions.joinToString(",") { it.value }}"
        val planHash = GenerationFingerprint.sha256(hashContent)

        SpeechPlan(
            rawText = trimmed,
            normalizedText = cleanSpokenText,
            instructions = instructions,
            speechPlanHash = planHash,
            profile = profile
        )
    }
}
