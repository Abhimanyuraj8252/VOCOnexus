package com.voconexus.app.core.data.repository

import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.dao.DocumentDao
import com.voconexus.app.core.data.dao.PartDao
import com.voconexus.app.core.data.dao.ProjectDao
import com.voconexus.app.core.data.dao.ProjectStats
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.engine.ScriptAnalysisPlan
import com.voconexus.app.core.planner.engine.ScriptPlannerEngine
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID

interface ProjectRepository {
    fun getAllProjects(): Flow<List<ProjectEntity>>
    fun getProjectById(id: String): Flow<ProjectEntity?>
    fun getPartsForProject(projectId: String): Flow<List<PartEntity>>
    fun getChunksForProject(projectId: String): Flow<List<ChunkEntity>>
    fun getChunksForPartFlow(partId: String): Flow<List<ChunkEntity>>
    suspend fun getChunksForProjectDirect(projectId: String): List<ChunkEntity>
    suspend fun getChunksPaged(projectId: String, limit: Int, offset: Int): List<ChunkEntity>
    suspend fun getProjectStats(projectId: String): ProjectStats
    suspend fun createProject(
        title: String,
        description: String,
        rawScriptText: String,
        engineId: String = "kokoro-82m",
        modelId: String = "kokoro-v1.0",
        voiceId: String = "af_heart"
    ): String

    suspend fun renameProject(projectId: String, newTitle: String)
    suspend fun updateProjectVoice(projectId: String, voiceId: String, engineId: String)
    suspend fun updateProjectSpeechSettings(id: String, speed: Float, pitch: Float, targetDurationMs: Long, durationMode: String)
    suspend fun updateDocumentScript(projectId: String, newScriptText: String)
    suspend fun analyzeScript(projectId: String, config: ChunkingConfig = ChunkingConfig()): ScriptAnalysisPlan
    suspend fun commitScriptPlan(projectId: String, plan: ScriptAnalysisPlan)
    suspend fun invalidatePlan(projectId: String)
    suspend fun deleteProjectSafely(projectId: String)
    suspend fun resetChunkAudio(chunkId: String)
}

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao,
    private val documentDao: DocumentDao,
    private val partDao: PartDao,
    private val chunkDao: ChunkDao,
    private val storageManager: AudioStorageManager,
    scriptPlannerEngine: ScriptPlannerEngine? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ProjectRepository {

    private val scriptPlannerEngine: ScriptPlannerEngine = scriptPlannerEngine ?: ScriptPlannerEngine(
        sentenceSegmenter = RuleBasedSentenceSegmenter(),
        tokenEstimator = HeuristicTokenEstimator(),
        durationEstimator = StandardDurationEstimator(),
        chunkPlanner = ChunkPlanner(HeuristicTokenEstimator()),
        partBuilder = PartBuilder(),
        planValidator = PlanValidator()
    )

    override fun getAllProjects(): Flow<List<ProjectEntity>> {
        return projectDao.getAllProjectsFlow()
    }

    override fun getProjectById(id: String): Flow<ProjectEntity?> {
        return projectDao.getProjectByIdFlow(id).flowOn(ioDispatcher)
    }

    override fun getPartsForProject(projectId: String): Flow<List<PartEntity>> {
        return partDao.getPartsForProjectFlow(projectId)
    }

    override fun getChunksForProject(projectId: String): Flow<List<ChunkEntity>> {
        return chunkDao.getChunksForProjectFlow(projectId)
    }

    override fun getChunksForPartFlow(partId: String): Flow<List<ChunkEntity>> {
        return chunkDao.getChunksForPartFlow(partId)
    }

    override suspend fun getChunksForProjectDirect(projectId: String): List<ChunkEntity> {
        return chunkDao.getChunksForProject(projectId)
    }

    override suspend fun getChunksPaged(projectId: String, limit: Int, offset: Int): List<ChunkEntity> {
        return chunkDao.getChunksPaged(projectId, limit, offset)
    }

    override suspend fun getProjectStats(projectId: String): ProjectStats {
        val chunks = chunkDao.getChunksForProject(projectId)
        val completedChunks = chunks.count { it.status == ChunkStatus.COMPLETED }
        val failedChunks = chunks.count { it.status == ChunkStatus.FAILED }
        val pendingChunks = chunks.count { it.status == ChunkStatus.PENDING || it.status == ChunkStatus.QUEUED }
        val completedDuration = chunks.filter { it.status == ChunkStatus.COMPLETED }.sumOf { it.durationMs }
        val totalDuration = chunks.sumOf { it.durationMs }
        val totalSizeBytes = storageManager.getUsedStorageBytesForProject(projectId)

        return ProjectStats(
            totalChunks = chunks.size,
            completedChunks = completedChunks,
            failedChunks = failedChunks,
            pendingChunks = pendingChunks,
            totalDurationMs = totalDuration,
            completedDurationMs = completedDuration,
            totalSizeBytes = totalSizeBytes
        )
    }

    override suspend fun createProject(
        title: String,
        description: String,
        rawScriptText: String,
        engineId: String,
        modelId: String,
        voiceId: String
    ): String = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotBlank()) { "Project title cannot be blank" }
        require(rawScriptText.isNotBlank()) { "Script text cannot be blank" }

        val currentTime = System.currentTimeMillis()
        val projectId = UUID.randomUUID().toString()
        val documentId = UUID.randomUUID().toString()

        val textHash = GenerationFingerprint.sha256(rawScriptText)

        val totalWordCount = rawScriptText.split("\\s+".toRegex()).size
        val totalCharCount = rawScriptText.length
        val estimatedMs = (totalWordCount * 400L).coerceAtLeast(1000L)

        val project = ProjectEntity(
            id = projectId,
            title = trimmedTitle,
            description = description.trim(),
            createdAt = currentTime,
            updatedAt = currentTime,
            status = "DRAFT",
            estimatedDurationMs = estimatedMs,
            actualDurationMs = 0L,
            partCount = 0,
            chunkCount = 0
        )

        val document = DocumentEntity(
            id = documentId,
            projectId = projectId,
            title = "$trimmedTitle Document",
            rawText = rawScriptText,
            textHash = textHash,
            partCount = 0,
            wordCount = totalWordCount,
            characterCount = totalCharCount,
            createdAt = currentTime,
            planStatus = "NOT_ANALYZED",
            planVersion = "",
            chunkCount = 0
        )

        projectDao.insertProject(project)
        documentDao.insertDocument(document)

        val plan = scriptPlannerEngine.generatePlan(projectId, documentId, rawScriptText)
        commitScriptPlan(projectId, plan)

        return@withContext projectId
    }

    override suspend fun renameProject(projectId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        require(trimmed.isNotBlank()) { "Project title cannot be blank" }
        projectDao.renameProject(projectId, trimmed, System.currentTimeMillis())
    }

    override suspend fun updateProjectVoice(projectId: String, voiceId: String, engineId: String) = withContext(ioDispatcher) {
        chunkDao.updateChunksVoiceForProject(projectId, voiceId, engineId)
    }

    override suspend fun updateProjectSpeechSettings(
        id: String,
        speed: Float,
        pitch: Float,
        targetDurationMs: Long,
        durationMode: String
    ) = withContext(ioDispatcher) {
        projectDao.updateProjectSpeechSettings(
            id = id,
            speed = speed,
            pitch = pitch,
            targetDurationMs = targetDurationMs,
            durationMode = durationMode,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateDocumentScript(projectId: String, newScriptText: String) = withContext(Dispatchers.IO) {
        val trimmedScript = newScriptText.trim()
        require(trimmedScript.isNotBlank()) { "Script text cannot be blank" }

        val document = documentDao.getDocumentForProject(projectId)
            ?: throw IllegalStateException("Document not found for project: $projectId")

        val newTextHash = GenerationFingerprint.sha256(trimmedScript)
        if (document.textHash == newTextHash) {
            return@withContext // No changes
        }

        val plan = scriptPlannerEngine.generatePlan(projectId, document.id, trimmedScript)
        commitScriptPlan(projectId, plan)
    }

    override suspend fun analyzeScript(
        projectId: String,
        config: ChunkingConfig
    ): ScriptAnalysisPlan = withContext(Dispatchers.Default) {
        val document = documentDao.getDocumentForProject(projectId)
            ?: throw IllegalStateException("Document not found for project: $projectId")

        return@withContext scriptPlannerEngine.generatePlan(
            projectId = projectId,
            documentId = document.id,
            rawDocumentText = document.rawText,
            config = config
        )
    }

    override suspend fun commitScriptPlan(
        projectId: String,
        plan: ScriptAnalysisPlan
    ) = withContext(Dispatchers.IO) {
        require(plan.validationResult.isValid) { "Cannot commit an invalid script analysis plan" }

        val document = documentDao.getDocumentForProject(projectId)
            ?: throw IllegalStateException("Document not found for project: $projectId")

        val currentTime = System.currentTimeMillis()

        val dbParts = mutableListOf<PartEntity>()
        val dbChunks = mutableListOf<ChunkEntity>()

        plan.parts.forEach { plannedPart ->
            val partId = UUID.randomUUID().toString()

            val partEntity = PartEntity(
                id = partId,
                projectId = projectId,
                documentId = document.id,
                title = plannedPart.title,
                sequenceIndex = plannedPart.sequenceIndex,
                chunkCount = plannedPart.chunks.size,
                wordCount = plannedPart.totalWordCount,
                characterCount = plannedPart.totalCharacterCount
            )
            dbParts.add(partEntity)

            plannedPart.chunks.forEach { plannedChunk ->
                val chunkEntity = ChunkEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    documentId = document.id,
                    partId = partId,
                    sequenceIndex = plannedChunk.sequenceIndex,
                    sourceText = plannedChunk.sourceText,
                    sourceTextHash = plannedChunk.sourceTextHash,
                    normalizedText = plannedChunk.normalizedText,
                    normalizedTextHash = plannedChunk.normalizedTextHash,
                    engineId = "kokoro-82m",
                    modelId = "kokoro-v1.0",
                    voiceId = "af_heart",
                    language = plannedChunk.language,
                    status = ChunkStatus.PENDING,
                    durationMs = plannedChunk.estimatedDurationMs,
                    startOffset = plannedChunk.startOffset,
                    endOffset = plannedChunk.endOffset,
                    paragraphIndex = plannedChunk.paragraphIndex,
                    sentenceIndex = plannedChunk.sentenceIndex,
                    estimatedTokens = plannedChunk.estimatedTokenCount,
                    createdAt = currentTime,
                    updatedAt = currentTime
                )
                dbChunks.add(chunkEntity)
            }
        }

        // Delete existing Parts and Chunks
        partDao.deletePartsForProject(projectId)
        chunkDao.deleteChunksForProject(projectId)

        // Insert Parts
        partDao.insertParts(dbParts)

        // Batch insert Chunks in chunks of 500 to maintain low SQLite memory footprint
        dbChunks.chunked(500).forEach { chunkBatch ->
            chunkDao.insertChunks(chunkBatch)
        }

        // Update Document plan status
        documentDao.updateDocumentPlan(
            documentId = document.id,
            planStatus = "PLANNED",
            planVersion = plan.planVersion,
            chunkCount = dbChunks.size,
            partCount = dbParts.size
        )

        // Update Project stats
        projectDao.updateProjectStats(
            id = projectId,
            estimatedDurationMs = plan.estimatedDurationMs,
            partCount = dbParts.size,
            chunkCount = dbChunks.size,
            updatedAt = currentTime
        )
        projectDao.updateProjectStatus(projectId, "PLANNED", currentTime)
    }

    override suspend fun invalidatePlan(projectId: String) = withContext(Dispatchers.IO) {
        val document = documentDao.getDocumentForProject(projectId) ?: return@withContext
        documentDao.updateDocumentPlan(
            documentId = document.id,
            planStatus = "PLAN_STALE",
            planVersion = document.planVersion,
            chunkCount = document.chunkCount,
            partCount = document.partCount
        )
        projectDao.updateProjectStatus(projectId, "PLAN_STALE", System.currentTimeMillis())
    }

    override suspend fun deleteProjectSafely(projectId: String) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        projectDao.updateProjectStatus(projectId, "DELETING", currentTime)
        storageManager.deleteProjectAudioAsync(projectId)
        projectDao.deleteProjectById(projectId)
    }

    override suspend fun resetChunkAudio(chunkId: String) = withContext(Dispatchers.IO) {
        chunkDao.resetChunkAudio(chunkId)
    }
}
