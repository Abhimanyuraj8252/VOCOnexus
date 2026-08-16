package com.voconexus.app

import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.core.planner.model.ChunkingProfile
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkPlannerTest {

    private val tokenEstimator = HeuristicTokenEstimator()
    private val durationEstimator = StandardDurationEstimator()
    private val segmenter = RuleBasedSentenceSegmenter()
    private val planner = ChunkPlanner(tokenEstimator)

    @Test
    fun testShortScriptChunking() {
        val text = "Hello world. This is sentence two."
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        val chunks = planner.planChunks(sentences, ChunkingConfig())
        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].sequenceIndex)
        assertFalse(chunks[0].isOversizedSplit)
    }

    @Test
    fun testTargetTokenRangeGrouping() {
        val textBuilder = StringBuilder()
        for (i in 1..40) {
            textBuilder.append("This is sentence number $i in a long article. ")
        }

        val sentences = segmenter.segmentDocument(textBuilder.toString(), tokenEstimator, durationEstimator)
        val config = ChunkingConfig(profile = ChunkingProfile(preferredTokenCount = 100, softMaxTokenCount = 150))

        val chunks = planner.planChunks(sentences, config)
        assertTrue(chunks.size > 1)

        chunks.forEach { chunk ->
            assertTrue("Chunk token count ${chunk.estimatedTokenCount} should not exceed hard max", chunk.estimatedTokenCount <= 250)
        }
    }

    @Test
    fun testOversizedSentenceFallbackSplitting() {
        val longSentenceBuilder = StringBuilder("First clause of very long sentence, ")
        for (i in 1..200) {
            longSentenceBuilder.append("word$i ")
        }
        longSentenceBuilder.append("final clause.")

        val sentences = segmenter.segmentDocument(longSentenceBuilder.toString(), tokenEstimator, durationEstimator)
        val config = ChunkingConfig(profile = ChunkingProfile(hardMaxTokenCount = 50))

        val chunks = planner.planChunks(sentences, config)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.any { it.isOversizedSplit })

        chunks.forEach { chunk ->
            val words = chunk.sourceText.split("\\s+".toRegex())
            assertTrue("Chunk words should be valid tokens", words.isNotEmpty())
        }
    }
}
