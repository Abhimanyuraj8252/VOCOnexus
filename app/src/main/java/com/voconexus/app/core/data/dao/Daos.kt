package com.voconexus.app.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.voconexus.app.core.data.db.AudioAssetEntity
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.ExportJobEntity
import com.voconexus.app.core.data.db.GenerationJobEntity
import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.TtsModelEntity
import com.voconexus.app.core.data.db.TtsVoiceEntity
import kotlinx.coroutines.flow.Flow

data class ChunkStatusCount(
    val status: ChunkStatus,
    val count: Int
)

data class ProjectStats(
    val totalChunks: Int,
    val completedChunks: Int,
    val failedChunks: Int,
    val pendingChunks: Int,
    val totalDurationMs: Long,
    val completedDurationMs: Long,
    val totalSizeBytes: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectByIdFlow(id: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameProject(id: String, title: String, updatedAt: Long)

    @Query("UPDATE projects SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE projects SET estimatedDurationMs = :estimatedDurationMs, partCount = :partCount, chunkCount = :chunkCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectStats(id: String, estimatedDurationMs: Long, partCount: Int, chunkCount: Int, updatedAt: Long)

    @Query("UPDATE projects SET speed = :speed, pitch = :pitch, targetDurationMs = :targetDurationMs, durationMode = :durationMode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectSpeechSettings(id: String, speed: Float, pitch: Float, targetDurationMs: Long, durationMode: String, updatedAt: Long)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE projectId = :projectId")
    fun getDocumentsForProjectFlow(projectId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE projectId = :projectId LIMIT 1")
    suspend fun getDocumentForProject(projectId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Query("UPDATE documents SET planStatus = :planStatus, planVersion = :planVersion, chunkCount = :chunkCount, partCount = :partCount WHERE id = :documentId")
    suspend fun updateDocumentPlan(documentId: String, planStatus: String, planVersion: String, chunkCount: Int, partCount: Int)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
}

@Dao
interface PartDao {
    @Query("SELECT * FROM parts WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    fun getPartsForProjectFlow(projectId: String): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts WHERE documentId = :documentId ORDER BY sequenceIndex ASC")
    suspend fun getPartsForDocument(documentId: String): List<PartEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<PartEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: PartEntity)

    @Query("DELETE FROM parts WHERE projectId = :projectId")
    suspend fun deletePartsForProject(projectId: String)
}

@Dao
interface ChunkDao {
    @Query("SELECT * FROM chunks WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    fun getChunksForProjectFlow(projectId: String): Flow<List<ChunkEntity>>

    @Query("SELECT * FROM chunks WHERE partId = :partId ORDER BY sequenceIndex ASC")
    fun getChunksForPartFlow(partId: String): Flow<List<ChunkEntity>>

    @Query("SELECT * FROM chunks WHERE partId = :partId ORDER BY sequenceIndex ASC")
    suspend fun getChunksForPart(partId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE projectId = :projectId ORDER BY sequenceIndex ASC LIMIT :limit OFFSET :offset")
    suspend fun getChunksPaged(projectId: String, limit: Int, offset: Int): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    suspend fun getChunksForProject(projectId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE id = :id")
    suspend fun getChunkById(id: String): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE status = :status ORDER BY sequenceIndex ASC")
    suspend fun getChunksByStatus(status: ChunkStatus): List<ChunkEntity>

    @Query("SELECT status as status, COUNT(*) as count FROM chunks WHERE projectId = :projectId GROUP BY status")
    fun getChunkStatusCountsFlow(projectId: String): Flow<List<ChunkStatusCount>>

    @Query("SELECT COUNT(*) FROM chunks WHERE projectId = :projectId AND status = :status")
    fun getChunkCountByStatusFlow(projectId: String, status: ChunkStatus): Flow<Int>

    @Query("SELECT SUM(durationMs) FROM chunks WHERE projectId = :projectId AND status = 'COMPLETED'")
    suspend fun getCompletedDurationForProject(projectId: String): Long?

    @Query("SELECT SUM(fileSizeBytes) FROM chunks WHERE projectId = :projectId")
    suspend fun getTotalAudioSizeBytesForProject(projectId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: ChunkEntity)

    @Update
    suspend fun updateChunk(chunk: ChunkEntity)

    @Query("UPDATE chunks SET status = 'PENDING', audioPath = NULL, durationMs = 0, fileSizeBytes = 0 WHERE id = :chunkId")
    suspend fun resetChunkAudio(chunkId: String)

    @Query("UPDATE chunks SET status = :status, updatedAt = :updatedAt WHERE id = :chunkId")
    suspend fun updateChunkStatus(chunkId: String, status: ChunkStatus, updatedAt: Long)

    @Query("UPDATE chunks SET status = :status, audioPath = :audioPath, durationMs = :durationMs, fileSizeBytes = :fileSizeBytes, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :chunkId")
    suspend fun updateChunkCompleted(
        chunkId: String,
        status: ChunkStatus,
        audioPath: String,
        durationMs: Long,
        fileSizeBytes: Long,
        completedAt: Long,
        updatedAt: Long
    )

    @Query("UPDATE chunks SET status = :status, generationFingerprint = :fingerprint, checksumSha256 = :checksum, updatedAt = :updatedAt WHERE id = :chunkId")
    suspend fun updateChunkFingerprint(chunkId: String, status: ChunkStatus, fingerprint: String, checksum: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chunks SET status = 'QUEUED', attemptCount = 0, updatedAt = :updatedAt WHERE projectId = :projectId AND status = 'FAILED'")
    suspend fun resetFailedChunksToQueued(projectId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chunks SET status = 'QUEUED', attemptCount = 0, updatedAt = :updatedAt WHERE id IN (:chunkIds)")
    suspend fun setChunksQueued(chunkIds: List<String>, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chunks SET status = 'QUEUED', attemptCount = 0, updatedAt = :updatedAt WHERE partId IN (:partIds)")
    suspend fun setPartsQueued(partIds: List<String>, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chunks WHERE projectId = :projectId AND status = 'QUEUED' ORDER BY sequenceIndex ASC LIMIT 1")
    suspend fun getNextEligibleChunk(projectId: String): ChunkEntity?

    @Query("UPDATE chunks SET status = 'GENERATING', updatedAt = :updatedAt WHERE id = :chunkId AND status = 'QUEUED'")
    suspend fun atomicClaimChunk(chunkId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE chunks SET voiceId = :voiceId, engineId = :engineId WHERE projectId = :projectId")
    suspend fun updateChunksVoiceForProject(projectId: String, voiceId: String, engineId: String)

    @Query("DELETE FROM chunks WHERE projectId = :projectId")
    suspend fun deleteChunksForProject(projectId: String)
}

@Dao
interface GenerationJobDao {
    @Query("SELECT * FROM generation_jobs WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun getJobsForProjectFlow(projectId: String): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE projectId = :projectId ORDER BY startedAt DESC LIMIT 1")
    fun getLatestJobForProjectFlow(projectId: String): Flow<GenerationJobEntity?>

    @Query("SELECT * FROM generation_jobs WHERE projectId = :projectId AND status IN ('QUEUED', 'STARTING', 'RUNNING', 'PAUSE_REQUESTED', 'STOP_REQUESTED') LIMIT 1")
    suspend fun getActiveJobForProject(projectId: String): GenerationJobEntity?

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    suspend fun getJobById(id: String): GenerationJobEntity?

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    fun getJobByIdFlow(id: String): Flow<GenerationJobEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: GenerationJobEntity)

    @Update
    suspend fun updateJob(job: GenerationJobEntity)

    @Query("UPDATE generation_jobs SET status = :status, activeChunkId = :activeChunkId WHERE id = :id")
    suspend fun updateJobStatus(id: String, status: GenerationJobStatus, activeChunkId: String? = null)

    @Query("UPDATE generation_jobs SET completedChunks = completedChunks + 1 WHERE id = :id")
    suspend fun incrementCompletedChunks(id: String)

    @Query("UPDATE generation_jobs SET failedChunks = failedChunks + 1 WHERE id = :id")
    suspend fun incrementFailedChunks(id: String)
}

data class ProjectAudioSummary(
    val totalDurationMs: Long = 0L,
    val totalFileSizeBytes: Long = 0L,
    val completedChunkCount: Int = 0
)

@Dao
interface AudioAssetDao {
    @Query("SELECT * FROM audio_assets WHERE chunkId = :chunkId")
    suspend fun getAssetForChunk(chunkId: String): AudioAssetEntity?

    @Query("SELECT * FROM audio_assets")
    suspend fun getAllAudioAssets(): List<AudioAssetEntity>

    @Query("SELECT * FROM audio_assets WHERE chunkId IN (SELECT id FROM chunks WHERE projectId = :projectId)")
    fun getAudioAssetsForProjectFlow(projectId: String): Flow<List<AudioAssetEntity>>

    @Query("SELECT * FROM audio_assets WHERE chunkId IN (SELECT id FROM chunks WHERE partId = :partId)")
    fun getAudioAssetsForPartFlow(partId: String): Flow<List<AudioAssetEntity>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) as totalDurationMs, COALESCE(SUM(fileSizeBytes), 0) as totalFileSizeBytes, COUNT(id) as completedChunkCount FROM audio_assets WHERE chunkId IN (SELECT id FROM chunks WHERE projectId = :projectId)")
    suspend fun getProjectAudioSummary(projectId: String): ProjectAudioSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AudioAssetEntity)

    @Query("DELETE FROM audio_assets WHERE chunkId = :chunkId")
    suspend fun deleteAssetForChunk(chunkId: String)

    @Query("DELETE FROM audio_assets WHERE id = :id")
    suspend fun deleteAssetById(id: String)
}

@Dao
interface ExportJobDao {
    @Query("SELECT * FROM export_jobs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getExportJobsForProjectFlow(projectId: String): Flow<List<ExportJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportJob(job: ExportJobEntity)

    @Update
    suspend fun updateExportJob(job: ExportJobEntity)
}

@Dao
interface TtsModelDao {
    @Query("SELECT * FROM tts_models ORDER BY name ASC")
    fun getAllModelsFlow(): Flow<List<TtsModelEntity>>

    @Query("SELECT * FROM tts_models WHERE id = :id")
    suspend fun getModelById(id: String): TtsModelEntity?

    @Query("SELECT * FROM tts_models WHERE engineId = :engineId")
    suspend fun getModelsForEngine(engineId: String): List<TtsModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: TtsModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<TtsModelEntity>)

    @Query("UPDATE tts_models SET status = :status, downloadProgress = :progress, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateModelStatus(
        id: String,
        status: String,
        progress: Float = 0f,
        errorMessage: String = "",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tts_models SET localPath = :path, isDownloaded = 1, status = 'INSTALLED', downloadProgress = 1.0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markModelInstalled(id: String, path: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tts_models SET localPath = '', isDownloaded = 0, status = 'NOT_INSTALLED', downloadProgress = 0.0, errorMessage = '', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markModelUninstalled(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tts_models WHERE id = :id")
    suspend fun deleteModel(id: String)
}

@Dao
interface TtsVoiceDao {
    @Query("SELECT * FROM tts_voices ORDER BY name ASC")
    fun getAllVoicesFlow(): Flow<List<TtsVoiceEntity>>

    @Query("SELECT * FROM tts_voices WHERE modelId = :modelId ORDER BY name ASC")
    fun getVoicesForModelFlow(modelId: String): Flow<List<TtsVoiceEntity>>

    @Query("SELECT * FROM tts_voices WHERE modelId = :modelId ORDER BY name ASC")
    suspend fun getVoicesForModel(modelId: String): List<TtsVoiceEntity>

    @Query("SELECT * FROM tts_voices WHERE language = :language ORDER BY name ASC")
    suspend fun getVoicesByLanguage(language: String): List<TtsVoiceEntity>

    @Query("SELECT * FROM tts_voices WHERE id = :id")
    suspend fun getVoiceById(id: String): TtsVoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoice(voice: TtsVoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoices(voices: List<TtsVoiceEntity>)

    @Query("DELETE FROM tts_voices WHERE modelId = :modelId")
    suspend fun deleteVoicesForModel(modelId: String)
}

@Dao
interface VoiceSegmentDao {
    @Query("SELECT * FROM voice_segments WHERE chunkId = :chunkId ORDER BY sequenceIndex ASC")
    fun getSegmentsForChunkFlow(chunkId: String): Flow<List<com.voconexus.app.core.data.db.VoiceSegmentEntity>>

    @Query("SELECT * FROM voice_segments WHERE chunkId = :chunkId ORDER BY sequenceIndex ASC")
    suspend fun getSegmentsForChunk(chunkId: String): List<com.voconexus.app.core.data.db.VoiceSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<com.voconexus.app.core.data.db.VoiceSegmentEntity>)

    @Query("DELETE FROM voice_segments WHERE chunkId = :chunkId")
    suspend fun deleteSegmentsForChunk(chunkId: String)
}

@Dao
interface SpeakerMappingDao {
    @Query("SELECT * FROM speaker_mappings WHERE projectId = :projectId ORDER BY speakerId ASC")
    fun getSpeakerMappingsForProjectFlow(projectId: String): Flow<List<com.voconexus.app.core.data.db.SpeakerMappingEntity>>

    @Query("SELECT * FROM speaker_mappings WHERE projectId = :projectId ORDER BY speakerId ASC")
    suspend fun getSpeakerMappingsForProject(projectId: String): List<com.voconexus.app.core.data.db.SpeakerMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakerMapping(mapping: com.voconexus.app.core.data.db.SpeakerMappingEntity)

    @Query("DELETE FROM speaker_mappings WHERE id = :id")
    suspend fun deleteSpeakerMapping(id: String)
}

@Dao
interface PronunciationRuleDao {
    @Query("SELECT * FROM pronunciation_rules WHERE scope = 'GLOBAL' OR projectId = :projectId ORDER BY createdAt DESC")
    fun getRulesForProjectFlow(projectId: String): Flow<List<com.voconexus.app.core.data.db.PronunciationRuleEntity>>

    @Query("SELECT * FROM pronunciation_rules WHERE scope = 'GLOBAL' OR projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getRulesForProject(projectId: String): List<com.voconexus.app.core.data.db.PronunciationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: com.voconexus.app.core.data.db.PronunciationRuleEntity)

    @Query("DELETE FROM pronunciation_rules WHERE id = :id")
    suspend fun deleteRule(id: String)
}

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmark_results ORDER BY timestamp DESC")
    fun getAllBenchmarksFlow(): Flow<List<com.voconexus.app.core.data.db.BenchmarkEntity>>

    @Query("SELECT * FROM benchmark_results WHERE modelId = :modelId AND isStale = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBenchmarkForModel(modelId: String): com.voconexus.app.core.data.db.BenchmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmark(benchmark: com.voconexus.app.core.data.db.BenchmarkEntity)

    @Query("UPDATE benchmark_results SET isStale = 1 WHERE modelId = :modelId")
    suspend fun markBenchmarksStaleForModel(modelId: String)

    @Query("DELETE FROM benchmark_results")
    suspend fun clearAllBenchmarks()
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY updatedAt DESC")
    fun getAllDownloadTasksFlow(): Flow<List<com.voconexus.app.core.data.db.DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE modelId = :modelId LIMIT 1")
    suspend fun getTaskForModel(modelId: String): com.voconexus.app.core.data.db.DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: com.voconexus.app.core.data.db.DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE modelId = :modelId")
    suspend fun deleteTaskForModel(modelId: String)
}


