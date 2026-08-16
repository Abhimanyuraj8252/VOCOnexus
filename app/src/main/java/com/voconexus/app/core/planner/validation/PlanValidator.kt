package com.voconexus.app.core.planner.validation

import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.planner.part.PlannedPart

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

class PlanValidator {

    fun validatePlan(
        rawDocumentText: String,
        parts: List<PlannedPart>
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (parts.isEmpty()) {
            errors.add("Plan contains no Parts")
            return ValidationResult(isValid = false, errors = errors)
        }

        val allChunks = parts.flatMap { it.chunks }
        if (allChunks.isEmpty()) {
            errors.add("Plan contains no Chunks")
            return ValidationResult(isValid = false, errors = errors)
        }

        // Invariant 5 & 6: Contiguous 0-indexed global sequence indices
        allChunks.forEachIndexed { expectedIndex, chunk ->
            if (chunk.sequenceIndex != expectedIndex) {
                errors.add("Chunk sequence gap or mismatch: expected $expectedIndex, got ${chunk.sequenceIndex}")
            }
        }

        // Invariant 3 & 4: Contiguous non-overlapping offsets
        var previousEnd = -1
        allChunks.forEach { chunk ->
            if (chunk.startOffset < previousEnd) {
                errors.add("Overlapping chunk offsets detected at chunk sequence ${chunk.sequenceIndex}: start ${chunk.startOffset} < prevEnd $previousEnd")
            }
            previousEnd = chunk.endOffset
        }

        // Invariant 7: Hash validation
        allChunks.forEach { chunk ->
            val computedHash = GenerationFingerprint.sha256(chunk.normalizedText)
            if (computedHash != chunk.normalizedTextHash) {
                errors.add("Hash mismatch for chunk ${chunk.sequenceIndex}: computed $computedHash != stored ${chunk.normalizedTextHash}")
            }
        }

        // Invariant 1 & 2: Text Reconstruction Invariant
        val reconstructedNormalized = GenerationFingerprint.normalizeText(allChunks.joinToString(" ") { it.sourceText })
        val documentNormalized = GenerationFingerprint.normalizeText(rawDocumentText)

        if (reconstructedNormalized != documentNormalized) {
            // Check if differences are purely whitespace/newlines
            val stripWSReconstructed = reconstructedNormalized.replace("\\s+".toRegex(), "")
            val stripWSDocument = documentNormalized.replace("\\s+".toRegex(), "")

            if (stripWSReconstructed != stripWSDocument) {
                errors.add("Text reconstruction mismatch: concatenated chunk text does not match normalized document text")
            } else {
                warnings.add("Minor whitespace difference between reconstructed chunks and document text")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
