package com.voconexus.app.core.multilingual.language

class LanguageDetector {

    companion object {
        private val HINGLISH_KEYWORDS = setOf(
            "aaj", "hum", "aap", "main", "hai", "hain", "baat", "karenge",
            "namaste", "samjhenge", "kaha", "kya", "kar", "rahe", "ho", "kaise"
        )
    }

    fun detectLanguage(text: String): DetectedLanguage {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return DetectedLanguage("en", ScriptType.LATIN, ConfidenceLevel.UNKNOWN, 0.0f)
        }

        var devanagariCount = 0
        var latinCount = 0
        var bengaliCount = 0
        var tamilCount = 0
        var teluguCount = 0
        var gujaratiCount = 0
        var totalAlpha = 0

        for (char in trimmed) {
            val code = char.code
            when (code) {
                in 0x0900..0x097F -> { devanagariCount++; totalAlpha++ }
                in 0x0980..0x09FF -> { bengaliCount++; totalAlpha++ }
                in 0x0B80..0x0BFF -> { tamilCount++; totalAlpha++ }
                in 0x0C00..0x0C7F -> { teluguCount++; totalAlpha++ }
                in 0x0A80..0x0AFF -> { gujaratiCount++; totalAlpha++ }
                in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F -> { latinCount++; totalAlpha++ }
            }
        }

        if (totalAlpha == 0) {
            return DetectedLanguage("en", ScriptType.LATIN, ConfidenceLevel.UNKNOWN, 0.0f)
        }

        val devRatio = devanagariCount.toFloat() / totalAlpha
        val latRatio = latinCount.toFloat() / totalAlpha
        val benRatio = bengaliCount.toFloat() / totalAlpha
        val tamRatio = tamilCount.toFloat() / totalAlpha
        val telRatio = teluguCount.toFloat() / totalAlpha
        val gujRatio = gujaratiCount.toFloat() / totalAlpha

        val words = trimmed.lowercase().split("\\s+".toRegex())
        val isShortText = words.size < 3 || trimmed.length < 10

        return when {
            devRatio >= 0.6f -> {
                val conf = if (isShortText) ConfidenceLevel.LOW else ConfidenceLevel.HIGH
                DetectedLanguage("hi", ScriptType.DEVANAGARI, conf, devRatio)
            }
            benRatio >= 0.6f -> {
                val conf = if (isShortText) ConfidenceLevel.LOW else ConfidenceLevel.HIGH
                DetectedLanguage("bn", ScriptType.BENGALI, conf, benRatio)
            }
            tamRatio >= 0.6f -> {
                val conf = if (isShortText) ConfidenceLevel.LOW else ConfidenceLevel.HIGH
                DetectedLanguage("ta", ScriptType.TAMIL, conf, tamRatio)
            }
            telRatio >= 0.6f -> {
                val conf = if (isShortText) ConfidenceLevel.LOW else ConfidenceLevel.HIGH
                DetectedLanguage("te", ScriptType.TELUGU, conf, telRatio)
            }
            gujRatio >= 0.6f -> {
                val conf = if (isShortText) ConfidenceLevel.LOW else ConfidenceLevel.HIGH
                DetectedLanguage("gu", ScriptType.GUJARATI, conf, gujRatio)
            }
            latRatio >= 0.6f -> {
                val hinglishMatches = words.count { HINGLISH_KEYWORDS.contains(it) }
                val isHinglish = hinglishMatches >= 2 || (words.size <= 4 && hinglishMatches >= 1)
                val lang = if (isHinglish) "hi" else "en"
                val conf = if (isShortText) ConfidenceLevel.MEDIUM else ConfidenceLevel.HIGH
                DetectedLanguage(lang, ScriptType.LATIN, conf, latRatio)
            }
            else -> {
                DetectedLanguage("en", ScriptType.OTHER, ConfidenceLevel.LOW, 0.5f)
            }
        }
    }
}
