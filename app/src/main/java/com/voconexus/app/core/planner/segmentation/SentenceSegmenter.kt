package com.voconexus.app.core.planner.segmentation

import com.voconexus.app.core.domain.DurationEstimator
import com.voconexus.app.core.planner.model.TokenEstimator

interface SentenceSegmenter {
    fun segmentDocument(
        rawText: String,
        tokenEstimator: TokenEstimator,
        durationEstimator: DurationEstimator
    ): List<Sentence>
}

class RuleBasedSentenceSegmenter(
    private val extraAbbreviations: Set<String> = emptySet()
) : SentenceSegmenter {

    private val defaultAbbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "co", "inc", "ltd",
        "eg", "ie", "etc", "vs", "no", "vol", "pp", "approx", "dept", "est",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"
    )

    private val allAbbreviations = defaultAbbreviations + extraAbbreviations.map { it.lowercase().removeSuffix(".") }

    override fun segmentDocument(
        rawText: String,
        tokenEstimator: TokenEstimator,
        durationEstimator: DurationEstimator
    ): List<Sentence> {
        if (rawText.isBlank()) return emptyList()

        val sentences = mutableListOf<Sentence>()
        var globalSentenceIndex = 0

        // Split document into paragraphs preserving global start/end offsets
        val paragraphs = splitIntoParagraphs(rawText)

        for ((paragraphIndex, paraRange) in paragraphs.withIndex()) {
            val paraText = rawText.substring(paraRange.first, paraRange.second)
            val paraSentences = segmentParagraphText(
                paraText = paraText,
                paraStartOffset = paraRange.first,
                paragraphIndex = paragraphIndex,
                startSentenceIndex = globalSentenceIndex,
                tokenEstimator = tokenEstimator,
                durationEstimator = durationEstimator
            )
            sentences.addAll(paraSentences)
            globalSentenceIndex += paraSentences.size
        }

        return sentences
    }

    private fun splitIntoParagraphs(text: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        val regex = Regex("""(\r?\n){2,}""")
        var lastIndex = 0

        for (match in regex.findAll(text)) {
            val end = match.range.first
            if (end > lastIndex) {
                val candidate = text.substring(lastIndex, end)
                if (candidate.isNotBlank()) {
                    ranges.add(Pair(lastIndex, end))
                }
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            val candidate = text.substring(lastIndex)
            if (candidate.isNotBlank()) {
                ranges.add(Pair(lastIndex, text.length))
            }
        }

        return ranges.ifEmpty { listOf(Pair(0, text.length)) }
    }

    private fun segmentParagraphText(
        paraText: String,
        paraStartOffset: Int,
        paragraphIndex: Int,
        startSentenceIndex: Int,
        tokenEstimator: TokenEstimator,
        durationEstimator: DurationEstimator
    ): List<Sentence> {
        val result = mutableListOf<Sentence>()
        var currentIndex = 0
        val length = paraText.length
        var currentSentenceIndex = startSentenceIndex

        var sentenceStart = 0
        var i = 0

        while (i < length) {
            val ch = paraText[i]

            // Sentence boundary candidates: '.', '!', '?', '…', or Devanagari danda '।'
            if (ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == '।') {
                var isRealBoundary = true

                // Check 1: Decimal numbers (e.g. 3.14 or 1,000.50 or $19.99)
                if (ch == '.') {
                    val isPrevDigit = i > 0 && paraText[i - 1].isDigit()
                    val isNextDigit = i + 1 < length && paraText[i + 1].isDigit()
                    if (isPrevDigit && isNextDigit) {
                        isRealBoundary = false
                    }
                }

                // Check 2: Abbreviation check (e.g., Mr. Smith, Dr. John)
                if (isRealBoundary && ch == '.') {
                    val wordBefore = getWordBefore(paraText, i)
                    if (wordBefore != null && allAbbreviations.contains(wordBefore.lowercase())) {
                        isRealBoundary = false
                    }
                }

                // Check 3: URL or Email domain check (e.g., example.com, user@domain.com)
                if (isRealBoundary && ch == '.') {
                    val isPrevLetter = i > 0 && paraText[i - 1].isLetterOrDigit()
                    val isNextLetter = i + 1 < length && paraText[i + 1].isLetterOrDigit()
                    val hasSpaceBefore = i > 0 && paraText[i - 1].isWhitespace()
                    val hasSpaceAfter = i + 1 < length && paraText[i + 1].isWhitespace()
                    if (isPrevLetter && isNextLetter && !hasSpaceBefore && !hasSpaceAfter) {
                        // Check if URL or email context
                        val contextSnippet = getContextSnippet(paraText, i)
                        if (contextSnippet.contains("http://") || contextSnippet.contains("https://") || contextSnippet.contains("@") || contextSnippet.contains("www.")) {
                            isRealBoundary = false
                        }
                    }
                }

                // Check 4: Quoted sentence boundary (e.g. He said, "Wait. Don't go.")
                // If closing quote immediately follows boundary, include closing quote in this sentence.
                if (isRealBoundary) {
                    var endBoundaryPos = i
                    // Consume closing quote or parenthesis right after punctuation
                    while (endBoundaryPos + 1 < length && isClosingDelimiter(paraText[endBoundaryPos + 1])) {
                        endBoundaryPos++
                    }

                    // Verify if there is trailing whitespace or end of paragraph
                    val isAtEnd = (endBoundaryPos + 1 >= length)
                    val nextIsWhitespace = !isAtEnd && paraText[endBoundaryPos + 1].isWhitespace()

                    if (isAtEnd || nextIsWhitespace) {
                        val rawSentenceText = paraText.substring(sentenceStart, endBoundaryPos + 1)
                        val trimmedText = rawSentenceText.trim()

                        if (trimmedText.isNotEmpty()) {
                            val trimStartOffset = sentenceStart + (rawSentenceText.indexOf(trimmedText[0]))
                            val trimEndOffset = trimStartOffset + trimmedText.length

                            val tokens = tokenEstimator.estimateTokens(trimmedText)
                            val wordCount = countWords(trimmedText)
                            val duration = durationEstimator.estimateDurationMs(trimmedText, wordCount, 1.0f)
                            val lang = detectLanguage(trimmedText)

                            result.add(
                                Sentence(
                                    text = trimmedText,
                                    startOffset = paraStartOffset + trimStartOffset,
                                    endOffset = paraStartOffset + trimEndOffset,
                                    paragraphIndex = paragraphIndex,
                                    sentenceIndex = currentSentenceIndex++,
                                    estimatedTokenCount = tokens,
                                    estimatedDurationMs = duration,
                                    language = lang
                                )
                            )
                        }

                        i = endBoundaryPos + 1
                        while (i < length && paraText[i].isWhitespace()) {
                            i++
                        }
                        sentenceStart = i
                        continue
                    }
                }
            }
            i++
        }

        // Catch any remaining un-punctuated text at the end of the paragraph
        if (sentenceStart < length) {
            val remainingText = paraText.substring(sentenceStart).trim()
            if (remainingText.isNotEmpty()) {
                val trimStartOffset = sentenceStart + (paraText.substring(sentenceStart).indexOf(remainingText[0]))
                val trimEndOffset = trimStartOffset + remainingText.length

                val tokens = tokenEstimator.estimateTokens(remainingText)
                val wordCount = countWords(remainingText)
                val duration = durationEstimator.estimateDurationMs(remainingText, wordCount, 1.0f)
                val lang = detectLanguage(remainingText)

                result.add(
                    Sentence(
                        text = remainingText,
                        startOffset = paraStartOffset + trimStartOffset,
                        endOffset = paraStartOffset + trimEndOffset,
                        paragraphIndex = paragraphIndex,
                        sentenceIndex = currentSentenceIndex++,
                        estimatedTokenCount = tokens,
                        estimatedDurationMs = duration,
                        language = lang
                    )
                )
            }
        }

        return result
    }

    private fun getWordBefore(text: String, dotIndex: Int): String? {
        var start = dotIndex - 1
        while (start >= 0 && (text[start].isLetter() || text[start] == '.')) {
            start--
        }
        val word = text.substring(start + 1, dotIndex).trim()
        return if (word.isNotBlank()) word else null
    }

    private fun getContextSnippet(text: String, index: Int): String {
        val start = (index - 20).coerceAtLeast(0)
        val end = (index + 20).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    private fun isClosingDelimiter(ch: Char): Boolean {
        return ch == '"' || ch == '\'' || ch == '”' || ch == '’' || ch == ')' || ch == ']' || ch == '}'
    }

    private fun countWords(text: String): Int {
        return Regex("""[\p{L}\p{N}]+""").findAll(text).count()
    }

    private fun detectLanguage(text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        val hasEnglish = text.any { it in 'a'..'z' || it in 'A'..'Z' }

        return when {
            hasDevanagari && hasEnglish -> "hi-IN"
            hasDevanagari -> "hi-IN"
            else -> "en-US"
        }
    }
}
