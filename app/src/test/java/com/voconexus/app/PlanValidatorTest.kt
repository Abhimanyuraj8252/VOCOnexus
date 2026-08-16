package com.voconexus.app

import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {

    private val tokenEstimator = HeuristicTokenEstimator()
    private val durationEstimator = StandardDurationEstimator()
    private val segmenter = RuleBasedSentenceSegmenter()
    private val chunkPlanner = ChunkPlanner(tokenEstimator)
    private val partBuilder = PartBuilder()
    private val validator = PlanValidator()

    @Test
    fun testValidPlanPassesReconstructionAndInvariantChecks() {
        val docText = "First paragraph sentence 1. First paragraph sentence 2.\n\nSecond paragraph sentence 3. Second paragraph sentence 4."

        val sentences = segmenter.segmentDocument(docText, tokenEstimator, durationEstimator)
        val plannedChunks = chunkPlanner.planChunks(sentences, ChunkingConfig())
        val parts = partBuilder.buildParts(plannedChunks)

        val result = validator.validatePlan(docText, parts)
        assertTrue(result.errors.joinToString(), result.isValid)
    }
}
