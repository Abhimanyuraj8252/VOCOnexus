package com.voconexus.app.core.generation

import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.data.db.JobType

data class GenerationJob(
    val id: String,
    val projectId: String,
    val documentId: String,
    val jobType: JobType,
    val status: GenerationJobStatus,
    val totalChunks: Int,
    val completedChunks: Int,
    val failedChunks: Int,
    val activeChunkId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val stoppedAt: Long? = null,
    val errorMessage: String? = null,
    val generationFingerprint: String,
    val engineId: String,
    val modelId: String,
    val voiceId: String,
    val speed: Float,
    val pitch: Float
) {
    val isFinished: Boolean
        get() = status in listOf(
            GenerationJobStatus.COMPLETED,
            GenerationJobStatus.COMPLETED_WITH_ERRORS,
            GenerationJobStatus.FAILED,
            GenerationJobStatus.CANCELLED,
            GenerationJobStatus.STOPPED
        )

    val isActive: Boolean
        get() = status in listOf(
            GenerationJobStatus.QUEUED,
            GenerationJobStatus.STARTING,
            GenerationJobStatus.RUNNING,
            GenerationJobStatus.PAUSE_REQUESTED,
            GenerationJobStatus.STOP_REQUESTED
        )

    val progressFraction: Float
        get() = if (totalChunks > 0) (completedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f) else 0f
}
