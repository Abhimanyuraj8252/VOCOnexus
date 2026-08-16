package com.voconexus.app.core.planner.chunking

import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.core.planner.model.TokenEstimator
import com.voconexus.app.core.planner.segmentation.Sentence

class ChunkPlanner(
    private val tokenEstimator: TokenEstimator
) {

    fun planChunks(
        sentences: List<Sentence>,
        config: ChunkingConfig
    ): List<PlannedChunk> {
        if (sentences.isEmpty()) return emptyList()

        val plannedChunks = mutableListOf<PlannedChunk>()
        val profile = config.profile

        var globalChunkIndex = 0
        var currentSentenceIndex = 0

        val currentBatch = mutableListOf<Sentence>()
        var currentBatchTokens = 0
        var currentBatchDuration = 0L

        fun finalizeCurrentBatch(isOversized: Boolean = false) {
            if (currentBatch.isEmpty()) return

            val firstSentence = currentBatch.first()
            val lastSentence = currentBatch.last()

            val combinedSourceText = currentBatch.joinToString(" ") { it.text }
            val normalizedText = GenerationFingerprint.normalizeText(combinedSourceText)

            val sourceHash = GenerationFingerprint.sha256(combinedSourceText)
            val normalizedHash = GenerationFingerprint.sha256(normalizedText)

            val chunk = PlannedChunk(
                sequenceIndex = globalChunkIndex++,
                partSequenceIndex = 0,
                sourceText = combinedSourceText,
                normalizedText = normalizedText,
                sourceTextHash = sourceHash,
                normalizedTextHash = normalizedHash,
                startOffset = firstSentence.startOffset,
                endOffset = lastSentence.endOffset,
                paragraphIndex = firstSentence.paragraphIndex,
                sentenceIndex = firstSentence.sentenceIndex,
                estimatedTokenCount = currentBatchTokens,
                estimatedDurationMs = currentBatchDuration,
                language = firstSentence.language,
                isOversizedSplit = isOversized
            )
            plannedChunks.add(chunk)

            currentBatch.clear()
            currentBatchTokens = 0
            currentBatchDuration = 0L
        }

        for (sentence in sentences) {
            val sentenceTokens = sentence.estimatedTokenCount

            // Case A: Single sentence exceeds hard maximum -> Fallback oversized splitting
            if (sentenceTokens > profile.hardMaxTokenCount) {
                // First finalize whatever was accumulated in current batch
                finalizeCurrentBatch()

                val subChunks = splitOversizedSentence(
                    sentence = sentence,
                    config = config,
                    startGlobalIndex = globalChunkIndex
                )
                plannedChunks.addAll(subChunks)
                globalChunkIndex += subChunks.size
                continue
            }

            // Case B: Paragraph boundary check
            val isNewParagraph = currentBatch.isNotEmpty() &&
                    profile.preserveParagraphs &&
                    sentence.paragraphIndex != currentBatch.last().paragraphIndex

            if (isNewParagraph) {
                finalizeCurrentBatch()
            }

            // Case C: Check if adding sentence exceeds soft maximum tokens
            val wouldExceedSoftMax = (currentBatchTokens + sentenceTokens) > profile.softMaxTokenCount

            if (wouldExceedSoftMax && currentBatch.isNotEmpty()) {
                finalizeCurrentBatch()
            }

            // Add sentence to current batch
            currentBatch.add(sentence)
            currentBatchTokens += sentenceTokens
            currentBatchDuration += sentence.estimatedDurationMs

            // If we reached or passed preferred token count and paragraph ends, we can finalize
            if (currentBatchTokens >= profile.preferredTokenCount) {
                // If soft max reached, finalize
                if (currentBatchTokens >= profile.softMaxTokenCount) {
                    finalizeCurrentBatch()
                }
            }
        }

        // Finalize remaining batch
        finalizeCurrentBatch()

        return plannedChunks
    }

    private fun splitOversizedSentence(
        sentence: Sentence,
        config: ChunkingConfig,
        startGlobalIndex: Int
    ): List<PlannedChunk> {
        val result = mutableListOf<PlannedChunk>()
        val profile = config.profile
        val text = sentence.text
        val hardMaxTokens = profile.hardMaxTokenCount

        // 1. Try splitting at clause punctuation boundaries: ',', ';', ':', '—', '-', '|'
        val clauseRanges = splitByClauses(text)

        var currentText = StringBuilder()
        var currentTokens = 0
        var subChunkStartOffset = sentence.startOffset
        var localChunkIndex = startGlobalIndex

        for (clause in clauseRanges) {
            val clauseTokens = tokenEstimator.estimateTokens(clause)

            if (currentTokens + clauseTokens > hardMaxTokens && currentText.isNotEmpty()) {
                val chunkText = currentText.toString().trim()
                val normalizedText = GenerationFingerprint.normalizeText(chunkText)
                val chunkEndOffset = (subChunkStartOffset + chunkText.length).coerceAtMost(sentence.endOffset)

                result.add(
                    PlannedChunk(
                        sequenceIndex = localChunkIndex++,
                        partSequenceIndex = 0,
                        sourceText = chunkText,
                        normalizedText = normalizedText,
                        sourceTextHash = GenerationFingerprint.sha256(chunkText),
                        normalizedTextHash = GenerationFingerprint.sha256(normalizedText),
                        startOffset = subChunkStartOffset,
                        endOffset = chunkEndOffset,
                        paragraphIndex = sentence.paragraphIndex,
                        sentenceIndex = sentence.sentenceIndex,
                        estimatedTokenCount = currentTokens,
                        estimatedDurationMs = (currentTokens * 400L),
                        language = sentence.language,
                        isOversizedSplit = true
                    )
                )

                subChunkStartOffset = chunkEndOffset
                currentText.clear()
                currentTokens = 0
            }

            // If a single clause itself > hardMaxTokens, fall back to safe whitespace word splitting
            if (clauseTokens > hardMaxTokens) {
                val wordChunks = splitClauseByWords(
                    clause = clause,
                    clauseStartOffset = subChunkStartOffset,
                    sentence = sentence,
                    hardMaxTokens = hardMaxTokens,
                    startGlobalIndex = localChunkIndex
                )
                result.addAll(wordChunks)
                localChunkIndex += wordChunks.size
                subChunkStartOffset = wordChunks.lastOrNull()?.endOffset ?: subChunkStartOffset
            } else {
                if (currentText.isNotEmpty()) currentText.append(" ")
                currentText.append(clause)
                currentTokens += clauseTokens
            }
        }

        if (currentText.isNotEmpty()) {
            val chunkText = currentText.toString().trim()
            val normalizedText = GenerationFingerprint.normalizeText(chunkText)
            result.add(
                PlannedChunk(
                    sequenceIndex = localChunkIndex,
                    partSequenceIndex = 0,
                    sourceText = chunkText,
                    normalizedText = normalizedText,
                    sourceTextHash = GenerationFingerprint.sha256(chunkText),
                    normalizedTextHash = GenerationFingerprint.sha256(normalizedText),
                    startOffset = subChunkStartOffset,
                    endOffset = sentence.endOffset,
                    paragraphIndex = sentence.paragraphIndex,
                    sentenceIndex = sentence.sentenceIndex,
                    estimatedTokenCount = currentTokens,
                    estimatedDurationMs = (currentTokens * 400L),
                    language = sentence.language,
                    isOversizedSplit = true
                )
            )
        }

        return result
    }

    private fun splitByClauses(text: String): List<String> {
        val clauses = mutableListOf<String>()
        val regex = Regex("""(?<=[,;:—|])\s+""")
        val parts = text.split(regex)
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                clauses.add(trimmed)
            }
        }
        return clauses.ifEmpty { listOf(text) }
    }

    private fun splitClauseByWords(
        clause: String,
        clauseStartOffset: Int,
        sentence: Sentence,
        hardMaxTokens: Int,
        startGlobalIndex: Int
    ): List<PlannedChunk> {
        val result = mutableListOf<PlannedChunk>()
        val words = clause.split(Regex("""\s+""")).filter { it.isNotBlank() }

        var currentText = StringBuilder()
        var currentTokens = 0
        var currentStartOffset = clauseStartOffset
        var localIndex = startGlobalIndex

        for (word in words) {
            val wordTokens = tokenEstimator.estimateTokens(word)

            if (currentTokens + wordTokens > hardMaxTokens && currentText.isNotEmpty()) {
                val textChunk = currentText.toString().trim()
                val normText = GenerationFingerprint.normalizeText(textChunk)
                val chunkEnd = (currentStartOffset + textChunk.length).coerceAtMost(sentence.endOffset)

                result.add(
                    PlannedChunk(
                        sequenceIndex = localIndex++,
                        partSequenceIndex = 0,
                        sourceText = textChunk,
                        normalizedText = normText,
                        sourceTextHash = GenerationFingerprint.sha256(textChunk),
                        normalizedTextHash = GenerationFingerprint.sha256(normText),
                        startOffset = currentStartOffset,
                        endOffset = chunkEnd,
                        paragraphIndex = sentence.paragraphIndex,
                        sentenceIndex = sentence.sentenceIndex,
                        estimatedTokenCount = currentTokens,
                        estimatedDurationMs = (currentTokens * 400L),
                        language = sentence.language,
                        isOversizedSplit = true
                    )
                )

                currentStartOffset = chunkEnd
                currentText.clear()
                currentTokens = 0
            }

            if (currentText.isNotEmpty()) currentText.append(" ")
            currentText.append(word)
            currentTokens += wordTokens
        }

        if (currentText.isNotEmpty()) {
            val textChunk = currentText.toString().trim()
            val normText = GenerationFingerprint.normalizeText(textChunk)
            result.add(
                PlannedChunk(
                    sequenceIndex = localIndex,
                    partSequenceIndex = 0,
                    sourceText = textChunk,
                    normalizedText = normText,
                    sourceTextHash = GenerationFingerprint.sha256(textChunk),
                    normalizedTextHash = GenerationFingerprint.sha256(normText),
                    startOffset = currentStartOffset,
                    endOffset = (currentStartOffset + textChunk.length).coerceAtMost(sentence.endOffset),
                    paragraphIndex = sentence.paragraphIndex,
                    sentenceIndex = sentence.sentenceIndex,
                    estimatedTokenCount = currentTokens,
                    estimatedDurationMs = (currentTokens * 400L),
                    language = sentence.language,
                    isOversizedSplit = true
                )
            )
        }

        return result
    }
}
