package com.voconexus.app.core.planner.part

import com.voconexus.app.core.planner.chunking.PlannedChunk

data class PlannedPart(
    val sequenceIndex: Int,
    val title: String,
    val chunks: List<PlannedChunk>,
    val totalWordCount: Int,
    val totalCharacterCount: Int,
    val estimatedDurationMs: Long
)

class PartBuilder {

    fun buildParts(
        plannedChunks: List<PlannedChunk>,
        targetPartCharCount: Int = 1000,
        targetPartDurationMs: Long = 60000L
    ): List<PlannedPart> {
        if (plannedChunks.isEmpty()) return emptyList()

        val parts = mutableListOf<PlannedPart>()
        var currentPartIndex = 0

        var currentPartChunks = mutableListOf<PlannedChunk>()
        var currentPartDurationMs = 0L
        var currentPartCharCount = 0

        fun finalizeCurrentPart() {
            if (currentPartChunks.isEmpty()) return

            val updatedPartChunks = currentPartChunks.mapIndexed { chunkIndexInPart, chunk ->
                chunk.copy(partSequenceIndex = chunkIndexInPart)
            }

            val totalText = updatedPartChunks.joinToString(" ") { it.sourceText }
            val wordCount = Regex("""[\p{L}\p{N}]+""").findAll(totalText).count()
            val charCount = totalText.length

            val part = PlannedPart(
                sequenceIndex = currentPartIndex++,
                title = "Part ${currentPartIndex}",
                chunks = updatedPartChunks,
                totalWordCount = wordCount,
                totalCharacterCount = charCount,
                estimatedDurationMs = currentPartDurationMs
            )
            parts.add(part)

            currentPartChunks = mutableListOf()
            currentPartDurationMs = 0L
            currentPartCharCount = 0
        }

        for (chunk in plannedChunks) {
            val chunkCharCount = chunk.sourceText.length
            val potentialCharCount = currentPartCharCount + chunkCharCount

            // Finalize current part if adding next chunk would push current non-empty part past target (~1000 chars)
            if (currentPartChunks.isNotEmpty() && (currentPartCharCount >= targetPartCharCount || (currentPartCharCount >= 700 && potentialCharCount > targetPartCharCount))) {
                finalizeCurrentPart()
            }

            currentPartChunks.add(chunk)
            currentPartCharCount += chunkCharCount
            currentPartDurationMs += chunk.estimatedDurationMs
        }

        finalizeCurrentPart()

        return parts
    }
}
