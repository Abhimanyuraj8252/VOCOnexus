package com.voconexus.app

import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.core.planner.model.ChunkingProfile
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChunkingEdgeCasesTest {

    private lateinit var chunkPlanner: ChunkPlanner
    private lateinit var sentenceSegmenter: RuleBasedSentenceSegmenter
    private val tokenEstimator = HeuristicTokenEstimator()
    private val durationEstimator = StandardDurationEstimator()

    @Before
    fun setUp() {
        chunkPlanner = ChunkPlanner(tokenEstimator)
        sentenceSegmenter = RuleBasedSentenceSegmenter()
    }

    @Test
    fun testEmptyAndWhitespaceScript() {
        val sentences = sentenceSegmenter.segmentDocument("", tokenEstimator, durationEstimator)
        val chunks = chunkPlanner.planChunks(sentences, ChunkingConfig())

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun testQuotesAndDialogue() {
        val text = """"Wait," she said. "Is that really true?" He replied, "Yes, absolutely!""""
        val sentences = sentenceSegmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertTrue(sentences.isNotEmpty())
        val chunks = chunkPlanner.planChunks(sentences, ChunkingConfig())
        assertTrue(chunks.isNotEmpty())
    }

    @Test
    fun testUrlsAndEmails() {
        val text = "Visit https://example.com/api/v1 or email contact@example.com for support."
        val sentences = sentenceSegmenter.segmentDocument(text, tokenEstimator, durationEstimator)
        val chunks = chunkPlanner.planChunks(sentences, ChunkingConfig())

        assertNotNull(chunks)
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].sourceText.contains("https://example.com/api/v1"))
    }

    @Test
    fun testNumbersAndCurrencies() {
        val text = "The revenue in 2026 was ₹500,000,000, which is up 20% from last year."
        val sentences = sentenceSegmenter.segmentDocument(text, tokenEstimator, durationEstimator)
        val chunks = chunkPlanner.planChunks(sentences, ChunkingConfig())

        assertNotNull(chunks)
        assertTrue(chunks.isNotEmpty())
    }

    @Test
    fun testVeryLongParagraphChunking() {
        val longParagraph = (1..50).joinToString(" ") { "Sentence $it is a long text paragraph designed to stress chunking boundaries." }
        val sentences = sentenceSegmenter.segmentDocument(longParagraph, tokenEstimator, durationEstimator)
        val config = ChunkingConfig(profile = ChunkingProfile(preferredTokenCount = 50, softMaxTokenCount = 80))

        val chunks = chunkPlanner.planChunks(sentences, config)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(chunk.estimatedTokenCount <= 120)
        }
    }
}
