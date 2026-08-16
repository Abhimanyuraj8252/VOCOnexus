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
        targetPartDurationMs: Long = 600000L // Default ~10 minutes
    ): List<PlannedPart> {
        if (plannedChunks.isEmpty()) return emptyList()

        val parts = mutableListOf<PlannedPart>()
        var currentPartIndex = 0

        var currentPartChunks = mutableListOf<PlannedChunk>()
        var currentPartDurationMs = 0L

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
        }

        for (chunk in plannedChunks) {
            // If adding chunk exceeds target duration AND current part is non-empty, finalize
            if (currentPartDurationMs + chunk.estimatedDurationMs > targetPartDurationMs && currentPartChunks.isNotEmpty()) {
                finalizeCurrentPart()
            }

            currentPartChunks.add(chunk)
            currentPartDurationMs += chunk.estimatedDurationMs
        }

        finalizeCurrentPart()

        return parts
    }
}
