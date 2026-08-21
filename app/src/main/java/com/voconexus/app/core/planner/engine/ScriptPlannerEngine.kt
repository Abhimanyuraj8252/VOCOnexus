package com.voconexus.app.core.planner.engine

import com.voconexus.app.core.domain.DurationEstimator
import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.core.planner.model.TokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.part.PlannedPart
import com.voconexus.app.core.planner.segmentation.SentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.planner.validation.ValidationResult
import com.voconexus.app.core.utils.FastTextHelpers
import java.util.UUID

enum class PlanStatus {
    NOT_ANALYZED,
    ANALYZING,
    PLANNED,
    PLAN_STALE,
    PLAN_FAILED
}

data class ScriptAnalysisPlan(
    val projectId: String,
    val documentId: String,
    val documentHash: String,
    val planVersion: String = UUID.randomUUID().toString(),
    val totalWords: Int,
    val totalCharacters: Int,
    val estimatedDurationMs: Long,
    val parts: List<PlannedPart>,
    val totalChunkCount: Int,
    val averageChunkTokens: Int,
    val averageChunkDurationMs: Long,
    val averagePartDurationMs: Long,
    val planStatus: PlanStatus = PlanStatus.PLANNED,
    val validationResult: ValidationResult
)

class ScriptPlannerEngine(
    private val sentenceSegmenter: SentenceSegmenter,
    private val tokenEstimator: TokenEstimator,
    private val durationEstimator: DurationEstimator,
    private val chunkPlanner: ChunkPlanner,
    private val partBuilder: PartBuilder,
    private val planValidator: PlanValidator
) {

    fun generatePlan(
        projectId: String,
        documentId: String,
        rawDocumentText: String,
        config: ChunkingConfig = ChunkingConfig()
    ): ScriptAnalysisPlan {
        require(rawDocumentText.isNotBlank()) { "Document text cannot be blank for script planning" }

        val documentHash = GenerationFingerprint.sha256(rawDocumentText)

        // 1. Sentence Segmentation
        val sentences = sentenceSegmenter.segmentDocument(
            rawText = rawDocumentText,
            tokenEstimator = tokenEstimator,
            durationEstimator = durationEstimator
        )

        // 2. Model-Aware Chunk Planning
        val plannedChunks = chunkPlanner.planChunks(
            sentences = sentences,
            config = config
        )

        // 3. Part Construction
        val parts = partBuilder.buildParts(
            plannedChunks = plannedChunks,
            targetPartCharCount = config.targetPartCharCount,
            targetPartDurationMs = config.targetPartDurationMs
        )

        // 4. Plan Validation
        val validationResult = planValidator.validatePlan(rawDocumentText, parts)

        // 5. Statistics Computation
        val allChunks = parts.flatMap { it.chunks }
        val totalWords = FastTextHelpers.fastWordCount(rawDocumentText)
        val totalChars = rawDocumentText.length
        val totalDurationMs = allChunks.sumOf { it.estimatedDurationMs }

        val avgTokens = if (allChunks.isNotEmpty()) allChunks.map { it.estimatedTokenCount }.average().toInt() else 0
        val avgChunkDuration = if (allChunks.isNotEmpty()) (totalDurationMs / allChunks.size) else 0L
        val avgPartDuration = if (parts.isNotEmpty()) (totalDurationMs / parts.size) else 0L

        return ScriptAnalysisPlan(
            projectId = projectId,
            documentId = documentId,
            documentHash = documentHash,
            planVersion = UUID.randomUUID().toString(),
            totalWords = totalWords,
            totalCharacters = totalChars,
            estimatedDurationMs = totalDurationMs,
            parts = parts,
            totalChunkCount = allChunks.size,
            averageChunkTokens = avgTokens,
            averageChunkDurationMs = avgChunkDuration,
            averagePartDurationMs = avgPartDuration,
            planStatus = if (validationResult.isValid) PlanStatus.PLANNED else PlanStatus.PLAN_FAILED,
            validationResult = validationResult
        )
    }
}
