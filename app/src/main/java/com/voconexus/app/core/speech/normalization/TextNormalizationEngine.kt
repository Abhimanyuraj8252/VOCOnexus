package com.voconexus.app.core.speech.normalization

import com.voconexus.app.core.speech.model.NormalizationProfile

class TextNormalizationEngine {

    companion object {
        private val TECHNICAL_PATTERN = "^(https?://|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|[A-Z]:\\\\|v\\d+\\.\\d+).*$".toRegex(RegexOption.IGNORE_CASE)
    }

    fun normalizeText(
        text: String,
        profile: NormalizationProfile = NormalizationProfile.NATURAL_NARRATION,
        languageCode: String = "en"
    ): String {
        val trimmed = text.trim()
        if (trimmed.isBlank() || profile == NormalizationProfile.EXACT_TEXT) {
            return trimmed
        }

        // Technical text protection
        if (TECHNICAL_PATTERN.matches(trimmed)) {
            return trimmed
        }

        var normalized = trimmed

        // Currency normalization
        normalized = normalized.replace("₹(\\d+)".toRegex()) { match ->
            val num = match.groupValues[1]
            if (languageCode.startsWith("hi")) "$num रुपये" else "$num rupees"
        }
        normalized = normalized.replace("\\$(\\d+)".toRegex()) { match ->
            val num = match.groupValues[1]
            "$num dollars"
        }
        normalized = normalized.replace("€(\\d+)".toRegex()) { match ->
            val num = match.groupValues[1]
            "$num euros"
        }

        // Acronym period removal (e.g. A.I. -> AI, I.S.R.O. -> ISRO)
        normalized = normalized.replace("(?<=\\b[A-Z])\\.(?=[A-Z]\\b|\\.)".toRegex(), "")

        return normalized
    }
}
