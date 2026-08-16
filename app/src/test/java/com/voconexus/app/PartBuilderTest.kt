package com.voconexus.app

import com.voconexus.app.core.planner.chunking.PlannedChunk
import com.voconexus.app.core.planner.part.PartBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartBuilderTest {

    private val partBuilder = PartBuilder()

    @Test
    fun testPartBuildingGroupsChunksByTargetDuration() {
        val chunks = (0..20).map { i ->
            PlannedChunk(
                sequenceIndex = i,
                partSequenceIndex = 0,
                sourceText = "Chunk $i text sample.",
                normalizedText = "Chunk $i text sample.",
                sourceTextHash = "hash$i",
                normalizedTextHash = "hash$i",
                startOffset = i * 20,
                endOffset = (i + 1) * 20,
                paragraphIndex = 0,
                sentenceIndex = i,
                estimatedTokenCount = 50,
                estimatedDurationMs = 60000L // 1 minute per chunk
            )
        }

        // Target part duration: 5 minutes (300,000 ms)
        val parts = partBuilder.buildParts(chunks, targetPartDurationMs = 300000L)

        assertTrue(parts.size >= 4)
        assertEquals(0, parts[0].sequenceIndex)
        assertEquals("Part 1", parts[0].title)

        parts.forEach { part ->
            assertTrue(part.chunks.isNotEmpty())
        }
    }
}
