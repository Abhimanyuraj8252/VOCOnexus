package com.voconexus.app.core.speech.pronunciation

import com.voconexus.app.core.speech.model.PronunciationEntry
import com.voconexus.app.core.speech.model.PronunciationScope

class PronunciationEngine {

    fun applyPronunciationRules(
        text: String,
        rules: List<PronunciationEntry>,
        languageCode: String = "en",
        voiceId: String? = null
    ): String {
        if (text.isBlank() || rules.isEmpty()) return text

        // Filter applicable rules
        val applicableRules = rules.filter { rule ->
            (rule.languageCode == "all" || rule.languageCode == languageCode) &&
            (rule.voiceId == null || rule.voiceId == voiceId)
        }

        // Sort rules by scope priority (DOCUMENT > PROJECT > GLOBAL) and match text length descending
        val sortedRules = applicableRules.sortedWith(
            compareByDescending<PronunciationEntry> { getScopePriority(it.scope) }
                .thenByDescending { it.matchText.length }
        )

        var resultText = text

        for (rule in sortedRules) {
            val match = rule.matchText.trim()
            val replacement = rule.replacement.trim()
            if (match.isBlank() || match.equals(replacement, ignoreCase = true)) continue

            val regexOptions = if (!rule.isCaseSensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
            val patternStr = if (rule.isWholeWord) "\\b${Regex.escape(match)}\\b" else Regex.escape(match)
            val regex = Regex(patternStr, regexOptions)

            resultText = regex.replace(resultText, replacement)
        }

        return resultText
    }

    private fun getScopePriority(scope: PronunciationScope): Int {
        return when (scope) {
            PronunciationScope.DOCUMENT -> 3
            PronunciationScope.PROJECT -> 2
            PronunciationScope.GLOBAL -> 1
        }
    }
}
