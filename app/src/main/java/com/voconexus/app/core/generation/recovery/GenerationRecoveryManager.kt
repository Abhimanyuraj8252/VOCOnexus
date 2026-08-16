package com.voconexus.app.core.generation.recovery

import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.dao.GenerationJobDao
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerationRecoveryManager(
    private val jobDao: GenerationJobDao,
    private val chunkDao: ChunkDao,
    private val storageManager: AudioStorageManager,
    private val audioValidator: AudioValidator = AudioValidator()
) {

    suspend fun performStartupRecoveryCheck(projectId: String) = withContext(Dispatchers.IO) {
        val activeJob = jobDao.getActiveJobForProject(projectId)
        if (activeJob != null && (activeJob.status == GenerationJobStatus.RUNNING || activeJob.status == GenerationJobStatus.STARTING)) {
            // App died while job was running -> restore to PAUSED for safe user resume
            jobDao.updateJobStatus(activeJob.id, GenerationJobStatus.PAUSED, null)
        }

        // Recover stale GENERATING chunks
        val generatingChunks = chunkDao.getChunksByStatus(ChunkStatus.GENERATING)
        for (chunk in generatingChunks) {
            val tempFile = storageManager.getTempAudioFile(chunk.projectId, chunk.documentId, chunk.partId, chunk.id)
            val finalFile = storageManager.getChunkAudioFile(chunk.projectId, chunk.documentId, chunk.partId, chunk.id)

            if (tempFile.exists()) {
                val validation = audioValidator.validateWavFile(tempFile)
                if (validation.isValid) {
                    // Valid temp file recovered! Move to final and complete
                    tempFile.renameTo(finalFile)
                    chunkDao.updateChunkCompleted(
                        chunkId = chunk.id,
                        status = ChunkStatus.COMPLETED,
                        audioPath = finalFile.absolutePath,
                        durationMs = validation.durationMs,
                        fileSizeBytes = validation.fileSizeBytes,
                        completedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    continue
                } else {
                    tempFile.delete()
                }
            }

            // Return unrecovered generating chunk to QUEUED
            chunkDao.updateChunkStatus(chunk.id, ChunkStatus.QUEUED, System.currentTimeMillis())
        }
    }
}
