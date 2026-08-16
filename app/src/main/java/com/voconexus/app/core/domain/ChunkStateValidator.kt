package com.voconexus.app.core.domain

import com.voconexus.app.core.data.db.ChunkStatus

object ChunkStateValidator {

    private val allowedTransitions = mapOf(
        ChunkStatus.PENDING to setOf(ChunkStatus.QUEUED, ChunkStatus.SKIPPED, ChunkStatus.NEEDS_REGENERATION),
        ChunkStatus.QUEUED to setOf(ChunkStatus.GENERATING, ChunkStatus.CANCELLED, ChunkStatus.PENDING),
        ChunkStatus.GENERATING to setOf(ChunkStatus.VALIDATING, ChunkStatus.COMPLETED, ChunkStatus.FAILED, ChunkStatus.CANCELLED),
        ChunkStatus.VALIDATING to setOf(ChunkStatus.COMPLETED, ChunkStatus.FAILED, ChunkStatus.CANCELLED),
        ChunkStatus.COMPLETED to setOf(ChunkStatus.NEEDS_REGENERATION, ChunkStatus.PENDING),
        ChunkStatus.FAILED to setOf(ChunkStatus.QUEUED, ChunkStatus.PENDING, ChunkStatus.NEEDS_REGENERATION),
        ChunkStatus.CANCELLED to setOf(ChunkStatus.QUEUED, ChunkStatus.PENDING),
        ChunkStatus.SKIPPED to setOf(ChunkStatus.QUEUED, ChunkStatus.PENDING),
        ChunkStatus.NEEDS_REGENERATION to setOf(ChunkStatus.QUEUED, ChunkStatus.PENDING, ChunkStatus.GENERATING)
    )

    fun isValidTransition(from: ChunkStatus, to: ChunkStatus): Boolean {
        if (from == to) return true
        return allowedTransitions[from]?.contains(to) ?: false
    }

    fun validateTransition(from: ChunkStatus, to: ChunkStatus) {
        if (!isValidTransition(from, to)) {
            throw IllegalStateException("Invalid chunk state transition from $from to $to")
        }
    }
}
