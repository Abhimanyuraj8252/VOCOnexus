package com.voconexus.app.core.generation.engine

import com.voconexus.app.core.data.dao.AudioAssetDao
import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.dao.DocumentDao
import com.voconexus.app.core.data.dao.GenerationJobDao
import com.voconexus.app.core.data.dao.ProjectDao
import com.voconexus.app.core.data.db.AudioAssetEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.generation.audio.WavAudioSink
import com.voconexus.app.core.generation.queue.GenerationQueue
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.tts.SynthesisSettings
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class GenerationCoordinator(
    private val projectDao: ProjectDao,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val jobDao: GenerationJobDao,
    private val audioAssetDao: AudioAssetDao,
    private val queue: GenerationQueue,
    private val engineRegistry: TtsEngineRegistry,
    private val storageManager: AudioStorageManager,
    private val ttsRepository: com.voconexus.app.core.data.repository.TtsRepository,
    private val audioValidator: AudioValidator = AudioValidator(),
    private val durationHistoryStore: com.voconexus.app.core.domain.duration.DurationHistoryStore? = null,
    private val userPreferencesManager: com.voconexus.app.core.preferences.UserPreferencesManager? = null,
    private val context: android.content.Context
) {

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _activeJobId = MutableStateFlow<String?>(null)
    val activeJobId: StateFlow<String?> = _activeJobId.asStateFlow()



    suspend fun executeJob(jobId: String): Boolean = withContext(Dispatchers.IO) {
        val job = jobDao.getJobById(jobId) ?: return@withContext false
        val document = if (job.documentId.isNotBlank()) {
            documentDao.getDocumentById(job.documentId)
        } else {
            documentDao.getDocumentForProject(job.projectId)
        }

        // Check Stale Plan Protection if document exists
        if (document != null && (document.planStatus == "STALE")) {
            jobDao.updateJobStatus(jobId, GenerationJobStatus.FAILED, null)
            jobDao.updateJob(job.copy(errorMessage = "Script structure is stale. Please re-analyze before generating."))
            return@withContext false
        }

        // Validate storage reserve (min 100 MB free)
        if (storageManager.getAvailableStorageBytes() < 100 * 1024 * 1024L) {
            jobDao.updateJobStatus(jobId, GenerationJobStatus.FAILED, null)
            jobDao.updateJob(job.copy(errorMessage = "Insufficient free storage. At least 100 MB free space required."))
            return@withContext false
        }

        _isGenerating.value = true
        _activeJobId.value = jobId
        jobDao.updateJobStatus(jobId, GenerationJobStatus.RUNNING, null)

        val engine = engineRegistry.getRequiredEngine(job.engineId)

        try {
            // Load engine model session with proper path
            val model = ttsRepository.getModelById(job.modelId)
            val modelPath = model?.installedPath ?: ""
            engine.loadModel(job.modelId, modelPath)

            var completedInThisRun = 0
            var failedInThisRun = 0

            while (_isGenerating.value) {
                // Refresh job status for cancellation / pause requests
                val currentJob = jobDao.getJobById(jobId) ?: break
                if (currentJob.status == GenerationJobStatus.PAUSE_REQUESTED) {
                    jobDao.updateJobStatus(jobId, GenerationJobStatus.PAUSED, null)
                    break
                }
                if (currentJob.status == GenerationJobStatus.STOP_REQUESTED) {
                    jobDao.updateJobStatus(jobId, GenerationJobStatus.STOPPED, null)
                    break
                }
                if (currentJob.status == GenerationJobStatus.CANCEL_REQUESTED) {
                    jobDao.updateJobStatus(jobId, GenerationJobStatus.CANCELLED, null)
                    break
                }

                val chunk = queue.claimNextChunk(job.projectId) ?: break // Queue empty
                jobDao.updateJobStatus(jobId, GenerationJobStatus.RUNNING, chunk.id)

                val chunkFingerprint = GenerationFingerprint.computeChunkFingerprint(
                    normalizedTextHash = chunk.normalizedTextHash,
                    engineId = job.engineId,
                    modelId = job.modelId,
                    voiceId = job.voiceId,
                    speed = job.speed,
                    pitch = job.pitch
                )

                // Skip Regeneration Check
                if (chunk.status == ChunkStatus.COMPLETED && !chunk.audioPath.isNullOrBlank()) {
                    val existingFile = File(chunk.audioPath)
                    val valResult = audioValidator.validateWavFile(existingFile)
                    if (valResult.isValid && chunk.generationFingerprint == chunkFingerprint) {
                        // Audio valid and fingerprint matches -> SKIP REGENERATION!
                        jobDao.incrementCompletedChunks(jobId)
                        continue
                    }
                }

                // Temporary audio file & final file
                val tempFile = storageManager.getTempAudioFile(job.projectId, job.documentId, chunk.partId, chunk.id)
                val finalFile = storageManager.getChunkAudioFile(job.projectId, job.documentId, chunk.partId, chunk.id)
                val oldBackupFile = File(finalFile.parentFile, "${chunk.id}.old")

                val audioSink = WavAudioSink()

                try {
                    val preset = userPreferencesManager?.preferences?.value?.qualityPreset
                    val targetSampleRate = preset?.let {
                        when (it) {
                            com.voconexus.app.core.preferences.QualityPreset.HIGH_QUALITY -> 44100
                            com.voconexus.app.core.preferences.QualityPreset.BALANCED -> 24000
                            com.voconexus.app.core.preferences.QualityPreset.STORAGE_EFFICIENT -> 16000
                        }
                    } ?: 24000

                    val settings = SynthesisSettings(speed = job.speed, pitch = job.pitch, sampleRate = targetSampleRate)
                    val synthesizedAudio = engine.synthesize(chunk.normalizedText, job.voiceId, settings)

                    if (synthesizedAudio.encoding == com.voconexus.app.core.tts.AudioEncoding.MP3) {
                        tempFile.writeBytes(synthesizedAudio.pcmData)
                    } else {
                        audioSink.open(tempFile, targetSampleRate, 1)
                        audioSink.writePcm(synthesizedAudio.pcmData)
                        audioSink.flushAndClose()
                    }

                    // Validate generated temporary audio
                    val valResult = audioValidator.validateWavFile(tempFile)
                    if (!valResult.isValid) {
                        throw Exception(valResult.errorMessage ?: "Generated audio validation failed")
                    }
                    val durationMs = valResult.durationMs

                    // Record measurement locally for adaptive duration estimation
                    durationHistoryStore?.recordMeasurement(
                        voiceId = job.voiceId,
                        modelId = job.modelId,
                        characterCount = chunk.normalizedText.length,
                        durationMs = durationMs,
                        speed = job.speed
                    )

                    // Safe Atomic Commit: Preserve old file if replacing
                    if (finalFile.exists()) {
                        finalFile.renameTo(oldBackupFile)
                    }

                    val moved = tempFile.renameTo(finalFile)
                    if (!moved) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }

                    if (oldBackupFile.exists()) {
                        oldBackupFile.delete()
                    }

                    val checksum = try {
                        GenerationFingerprint.sha256Stream(finalFile.inputStream())
                    } catch (_: Exception) {
                        ""
                    }

                    // Update database
                    queue.updateChunkCompleted(
                        chunkId = chunk.id,
                        audioPath = finalFile.absolutePath,
                        durationMs = durationMs,
                        fileSizeBytes = finalFile.length(),
                        fingerprint = chunkFingerprint,
                        checksum = checksum
                    )

                    // Insert/Update AudioAsset
                    audioAssetDao.insertAsset(
                        AudioAssetEntity(
                            id = UUID.randomUUID().toString(),
                            chunkId = chunk.id,
                            filePath = finalFile.absolutePath,
                            fileFormat = "WAV",
                            mimeType = "audio/wav",
                            sampleRate = valResult.sampleRate,
                            channels = valResult.channels,
                            bitrate = valResult.sampleRate * valResult.channels * 16,
                            durationMs = durationMs,
                            fileSizeBytes = finalFile.length(),
                            checksum = checksum,
                            createdAt = System.currentTimeMillis()
                        )
                    )

                    jobDao.incrementCompletedChunks(jobId)
                    completedInThisRun++
                } catch (e: Exception) {
                    // Safe Failure Handling: Restore old backup audio if regeneration failed
                    if (oldBackupFile.exists()) {
                        oldBackupFile.renameTo(finalFile)
                    }
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }

                    val newAttemptCount = chunk.attemptCount + 1
                    val newStatus = if (newAttemptCount >= 3) ChunkStatus.FAILED else ChunkStatus.QUEUED

                    chunkDao.updateChunk(
                        chunk.copy(
                            status = newStatus,
                            attemptCount = newAttemptCount,
                            errorCode = "ERR_SYNTHESIS",
                            errorMessage = e.message ?: "Synthesis failed",
                            updatedAt = System.currentTimeMillis()
                        )
                    )

                    jobDao.incrementFailedChunks(jobId)
                    failedInThisRun++
                }
            }

            // Final Job Status Evaluation
            val finalJobState = jobDao.getJobById(jobId)
            if (finalJobState != null) {
                when (finalJobState.status) {
                    GenerationJobStatus.RUNNING -> {
                        val status = if (finalJobState.failedChunks > 0) GenerationJobStatus.COMPLETED_WITH_ERRORS else GenerationJobStatus.COMPLETED
                        jobDao.updateJobStatus(jobId, status, null)
                    }
                    GenerationJobStatus.PAUSE_REQUESTED -> {
                        jobDao.updateJobStatus(jobId, GenerationJobStatus.PAUSED, null)
                    }
                    GenerationJobStatus.STOP_REQUESTED -> {
                        jobDao.updateJobStatus(jobId, GenerationJobStatus.STOPPED, null)
                    }
                    GenerationJobStatus.CANCEL_REQUESTED -> {
                        jobDao.updateJobStatus(jobId, GenerationJobStatus.CANCELLED, null)
                    }
                    else -> {} // Do nothing
                }
            }

            return@withContext true
        } catch (e: Exception) {
            jobDao.updateJobStatus(jobId, GenerationJobStatus.FAILED, null)
            return@withContext false
        } finally {
            engine.unloadModel()
            _isGenerating.value = false
            _activeJobId.value = null
        }
    }

    suspend fun requestPause(jobId: String) {
        jobDao.updateJobStatus(jobId, GenerationJobStatus.PAUSE_REQUESTED, null)
        _isGenerating.value = false
    }

    suspend fun requestStop(jobId: String) {
        jobDao.updateJobStatus(jobId, GenerationJobStatus.STOP_REQUESTED, null)
        _isGenerating.value = false
    }
}
