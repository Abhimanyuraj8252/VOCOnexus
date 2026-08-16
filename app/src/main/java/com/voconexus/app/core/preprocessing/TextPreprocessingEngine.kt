package com.voconexus.app.core.preprocessing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreprocessingOptions(
    val removeSrtTimestamps: Boolean = true,
    val removeSrtNumbering: Boolean = true,
    val stripFormattingTags: Boolean = true,
    val normalizeWhitespace: Boolean = true,
    val normalizeRepeatedPunctuation: Boolean = true,
    val classifyTechnicalArtifacts: Boolean = true
)

data class PreprocessingSummary(
    val charCountBefore: Int,
    val charCountAfter: Int,
    val wordCountBefore: Int,
    val wordCountAfter: Int,
    val paragraphCountBefore: Int,
    val paragraphCountAfter: Int,
    val timestampsRemoved: Int = 0,
    val numberingRemoved: Int = 0,
    val tagsRemoved: Int = 0,
    val whitespaceFixes: Int = 0,
    val artifactsDetected: Int = 0
)

data class PreprocessingResult(
    val normalizedText: String,
    val summary: PreprocessingSummary
)



class TextPreprocessingEngine {

    private val htmlTagRegex = "</?[a-zA-Z][^>]*>".toRegex()
    private val srtTimestampRegex = "\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}".toRegex()
    private val repeatedExclamationRegex = "!{2,}".toRegex()
    private val repeatedQuestionRegex = "\\?{2,}".toRegex()
    private val technicalArtifactRegex = "^(#+|=|\\*+|-+|\\[SCENE\\s*\\d+\\])$".toRegex(RegexOption.IGNORE_CASE)

    suspend fun preprocess(
        text: String,
        options: PreprocessingOptions = PreprocessingOptions()
    ): PreprocessingResult = withContext(Dispatchers.Default) {
        val charCountBefore = text.length
        val wordCountBefore = countWords(text)
        val paragraphCountBefore = countParagraphs(text)

        var currentText = text
        var timestampsRemoved = 0
        var numberingRemoved = 0
        var tagsRemoved = 0
        var whitespaceFixes = 0
        var artifactsDetected = 0

        // 1. Strip HTML tags
        if (options.stripFormattingTags) {
            val matches = htmlTagRegex.findAll(currentText).count()
            if (matches > 0) {
                tagsRemoved += matches
                currentText = currentText.replace(htmlTagRegex, "")
            }
        }

        // 2. Strip SRT Timestamps
        if (options.removeSrtTimestamps) {
            val matches = srtTimestampRegex.findAll(currentText).count()
            if (matches > 0) {
                timestampsRemoved += matches
                currentText = currentText.replace(srtTimestampRegex, "")
            }
        }

        // 3. Normalize Whitespace & Line Endings
        if (options.normalizeWhitespace) {
            // Replace CRLF with LF
            currentText = currentText.replace("\r\n", "\n").replace("\r", "\n")

            // Split into lines to normalize tabs/spaces
            val lines = currentText.split("\n")
            val cleanLines = mutableListOf<String>()

            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) {
                    cleanLines.add("")
                    continue
                }

                // Check for technical metadata artifacts
                if (options.classifyTechnicalArtifacts && technicalArtifactRegex.matches(trimmedLine)) {
                    artifactsDetected++
                    // Retain line but count artifact
                }

                val spaceCleaned = trimmedLine.replace("[ \\t]+".toRegex(), " ")
                if (spaceCleaned != line) {
                    whitespaceFixes++
                }
                cleanLines.add(spaceCleaned)
            }

            // Collapse 3+ consecutive newlines to 2 newlines (paragraph separator)
            currentText = cleanLines.joinToString("\n").replace("\n{3,}".toRegex(), "\n\n")
        }

        // 4. Normalize Repeated Punctuation conservatively
        if (options.normalizeRepeatedPunctuation) {
            currentText = currentText
                .replace(repeatedExclamationRegex, "!")
                .replace(repeatedQuestionRegex, "?")
        }

        val finalNormalizedText = currentText.trim()

        val summary = PreprocessingSummary(
            charCountBefore = charCountBefore,
            charCountAfter = finalNormalizedText.length,
            wordCountBefore = wordCountBefore,
            wordCountAfter = countWords(finalNormalizedText),
            paragraphCountBefore = paragraphCountBefore,
            paragraphCountAfter = countParagraphs(finalNormalizedText),
            timestampsRemoved = timestampsRemoved,
            numberingRemoved = numberingRemoved,
            tagsRemoved = tagsRemoved,
            whitespaceFixes = whitespaceFixes,
            artifactsDetected = artifactsDetected
        )

        PreprocessingResult(
            normalizedText = finalNormalizedText,
            summary = summary
        )
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    private fun countParagraphs(text: String): Int {
        if (text.isBlank()) return 0
        return text.split("\n\n", "\r\n\r\n").filter { it.trim().isNotEmpty() }.size
    }
}
