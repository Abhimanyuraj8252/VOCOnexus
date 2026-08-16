package com.voconexus.app.core.data.repository

import android.content.Context
import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.dao.DocumentDao
import com.voconexus.app.core.data.dao.GenerationJobDao
import com.voconexus.app.core.data.dao.ProjectDao
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.GenerationJobEntity
import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.data.db.JobType
import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.generation.GenerationJob
import com.voconexus.app.core.generation.engine.GenerationCoordinator
import com.voconexus.app.core.generation.queue.GenerationQueue
import com.voconexus.app.core.generation.recovery.GenerationRecoveryManager
import com.voconexus.app.core.generation.service.TtsGenerationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

interface GenerationRepository {
    fun getLatestJobFlow(projectId: String): Flow<GenerationJob?>
    fun getChunksFlow(projectId: String): Flow<List<ChunkEntity>>
    suspend fun createAndStartJob(
        projectId: String,
        documentId: String,
        jobType: JobType = JobType.GENERATE_SELECTED,
        engineId: String = "kokoro-82m",
        modelId: String = "kokoro-82m-v1.0",
        voiceId: String = "af_heart",
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        selectedChunkIds: List<String> = emptyList(),
        selectedPartIds: List<String> = emptyList()
    ): GenerationJob

    suspend fun pauseActiveJob(projectId: String)
    suspend fun stopActiveJob(projectId: String)
    suspend fun cancelActiveJob(projectId: String)
    suspend fun retryFailedChunks(projectId: String)
    suspend fun runStartupRecovery(projectId: String)
}

class GenerationRepositoryImpl(
    private val context: Context,
    private val projectDao: ProjectDao,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val jobDao: GenerationJobDao,
    private val queue: GenerationQueue,
    private val coordinator: GenerationCoordinator,
    private val recoveryManager: GenerationRecoveryManager
) : GenerationRepository {

    override fun getLatestJobFlow(projectId: String): Flow<GenerationJob?> {
        return jobDao.getLatestJobForProjectFlow(projectId).map { entity ->
            entity?.toDomainJob()
        }
    }

    override fun getChunksFlow(projectId: String): Flow<List<ChunkEntity>> {
        return chunkDao.getChunksForProjectFlow(projectId)
    }

    override suspend fun createAndStartJob(
        projectId: String,
        documentId: String,
        jobType: JobType,
        engineId: String,
        modelId: String,
        voiceId: String,
        speed: Float,
        pitch: Float,
        selectedChunkIds: List<String>,
        selectedPartIds: List<String>
    ): GenerationJob = withContext(Dispatchers.IO) {
        val activeJob = jobDao.getActiveJobForProject(projectId)
        if (activeJob != null) {
            return@withContext activeJob.toDomainJob()
        }

        val chunks = chunkDao.getChunksForProject(projectId)
        if (chunks.isEmpty()) {
            throw IllegalArgumentException("No chunks found in project to generate")
        }

        // Apply subset selection if provided
        val isSubset = selectedChunkIds.isNotEmpty() || selectedPartIds.isNotEmpty()
        var queuedCount = 0
        if (isSubset) {
            // First mark all chunks as PENDING
            chunks.forEach { 
                if (it.status == ChunkStatus.QUEUED || it.status == ChunkStatus.FAILED) {
                    chunkDao.updateChunkStatus(it.id, ChunkStatus.PENDING, System.currentTimeMillis())
                }
            }
            // Then set the selected ones to QUEUED
            if (selectedChunkIds.isNotEmpty()) {
                chunkDao.setChunksQueued(selectedChunkIds)
                queuedCount += selectedChunkIds.size
            }
            if (selectedPartIds.isNotEmpty()) {
                chunkDao.setPartsQueued(selectedPartIds)
                queuedCount += chunks.count { it.partId in selectedPartIds && it.id !in selectedChunkIds }
            }
        } else {
             // Generate all that are not completed
             chunks.forEach {
                 if (it.status != ChunkStatus.COMPLETED) {
                     chunkDao.updateChunkStatus(it.id, ChunkStatus.QUEUED, System.currentTimeMillis())
                     queuedCount++
                 }
             }
        }

        val fingerprint = GenerationFingerprint.computeChunkFingerprint(
            normalizedTextHash = "composite_${projectId}",
            engineId = engineId,
            modelId = modelId,
            voiceId = voiceId,
            speed = speed,
            pitch = pitch
        )

        val newJob = GenerationJobEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            documentId = documentId,
            jobType = jobType.name,
            status = GenerationJobStatus.QUEUED,
            totalChunks = queuedCount,
            completedChunks = 0,
            failedChunks = 0,
            startedAt = System.currentTimeMillis(),
            generationFingerprint = fingerprint,
            engineId = engineId,
            modelId = modelId,
            voiceId = voiceId,
            speed = speed,
            pitch = pitch
        )

        jobDao.insertJob(newJob)
        TtsGenerationService.startService(context, newJob.id)

        return@withContext newJob.toDomainJob()
    }

    override suspend fun pauseActiveJob(projectId: String) = withContext(Dispatchers.IO) {
        val active = jobDao.getActiveJobForProject(projectId) ?: return@withContext
        jobDao.updateJobStatus(active.id, GenerationJobStatus.PAUSE_REQUESTED, active.activeChunkId)
        coordinator.requestPause(active.id)
    }

    override suspend fun stopActiveJob(projectId: String) = withContext(Dispatchers.IO) {
        val active = jobDao.getActiveJobForProject(projectId) ?: return@withContext
        jobDao.updateJobStatus(active.id, GenerationJobStatus.STOP_REQUESTED, active.activeChunkId)
        coordinator.requestStop(active.id)
    }

    override suspend fun cancelActiveJob(projectId: String) = withContext(Dispatchers.IO) {
        val active = jobDao.getActiveJobForProject(projectId) ?: return@withContext
        jobDao.updateJobStatus(active.id, GenerationJobStatus.CANCELLED, null)
    }

    override suspend fun retryFailedChunks(projectId: String) = withContext(Dispatchers.IO) {
        queue.resetFailedChunks(projectId)
        val active = jobDao.getActiveJobForProject(projectId)
        if (active != null) {
            jobDao.updateJobStatus(active.id, GenerationJobStatus.QUEUED, null)
            TtsGenerationService.startService(context, active.id)
        }
    }

    override suspend fun runStartupRecovery(projectId: String) = withContext(Dispatchers.IO) {
        recoveryManager.performStartupRecoveryCheck(projectId)
    }

    private fun GenerationJobEntity.toDomainJob(): GenerationJob {
        val jobTypeEnum = runCatching { JobType.valueOf(jobType) }.getOrDefault(JobType.GENERATE_SELECTED)
        return GenerationJob(
            id = id,
            projectId = projectId,
            documentId = documentId,
            jobType = jobTypeEnum,
            status = status,
            totalChunks = totalChunks,
            completedChunks = completedChunks,
            failedChunks = failedChunks,
            activeChunkId = activeChunkId,
            startedAt = startedAt,
            endedAt = endedAt,
            pausedAt = pausedAt,
            stoppedAt = stoppedAt,
            errorMessage = errorMessage,
            generationFingerprint = generationFingerprint,
            engineId = engineId,
            modelId = modelId,
            voiceId = voiceId,
            speed = speed,
            pitch = pitch
        )
    }
}
