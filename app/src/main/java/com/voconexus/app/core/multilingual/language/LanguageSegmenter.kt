package com.voconexus.app.core.multilingual.language

import java.util.UUID

class LanguageSegmenter(
    private val detector: LanguageDetector = LanguageDetector()
) {

    fun segmentText(fullText: String, chunkId: String = ""): List<LanguageSegment> {
        val trimmed = fullText.trim()
        if (trimmed.isBlank()) return emptyList()

        val paragraphs = trimmed.split("\n\n+".toRegex())
        val segments = mutableListOf<LanguageSegment>()
        var seqIndex = 0

        for (paragraph in paragraphs) {
            val sentenceMatches = paragraph.split("(?<=[.!?])\\s+".toRegex())
            for (sentence in sentenceMatches) {
                val sentenceText = sentence.trim()
                if (sentenceText.isBlank()) continue

                val detected = detector.detectLanguage(sentenceText)

                segments.add(
                    LanguageSegment(
                        id = UUID.randomUUID().toString(),
                        chunkId = chunkId,
                        sequenceIndex = seqIndex++,
                        text = sentenceText,
                        languageCode = detected.languageCode
                    )
                )
            }
        }
        return segments
    }
}
