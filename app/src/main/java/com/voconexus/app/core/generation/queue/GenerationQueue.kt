package com.voconexus.app.core.generation.queue

import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerationQueue(
    private val chunkDao: ChunkDao
) {

    suspend fun claimNextChunk(projectId: String): ChunkEntity? = withContext(Dispatchers.IO) {
        val eligible = chunkDao.getNextEligibleChunk(projectId) ?: return@withContext null

        val claimedRows = chunkDao.atomicClaimChunk(eligible.id)
        if (claimedRows > 0) {
            return@withContext chunkDao.getChunkById(eligible.id)
        } else {
            // Concurrency race: chunk was claimed by another worker, try next
            return@withContext claimNextChunk(projectId)
        }
    }

    suspend fun resetFailedChunks(projectId: String) = withContext(Dispatchers.IO) {
        chunkDao.resetFailedChunksToQueued(projectId)
    }

    suspend fun updateChunkStatus(chunkId: String, status: ChunkStatus) = withContext(Dispatchers.IO) {
        chunkDao.updateChunkStatus(chunkId, status, System.currentTimeMillis())
    }

    suspend fun updateChunkCompleted(
        chunkId: String,
        audioPath: String,
        durationMs: Long,
        fileSizeBytes: Long,
        fingerprint: String,
        checksum: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        chunkDao.updateChunkCompleted(
            chunkId = chunkId,
            status = ChunkStatus.COMPLETED,
            audioPath = audioPath,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            completedAt = now,
            updatedAt = now
        )
        chunkDao.updateChunkFingerprint(chunkId, ChunkStatus.COMPLETED, fingerprint, checksum, now)
    }
}
