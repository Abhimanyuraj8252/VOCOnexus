package com.voconexus.app.ui.screens.projectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.repository.GenerationRepository
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.generation.GenerationJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import com.voconexus.app.core.data.dao.AudioAssetDao
import com.voconexus.app.core.data.db.AudioAssetEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProjectDetailViewModel(
    val projectId: String,
    private val projectRepository: ProjectRepository,
    private val generationRepository: GenerationRepository,
    private val audioAssetDao: AudioAssetDao? = null,
    private val voiceRepository: com.voconexus.app.core.data.repository.VoiceRepository? = null,
    private val userPrefsManager: com.voconexus.app.core.preferences.UserPreferencesManager? = null
) : ViewModel() {

    val projectState: StateFlow<ProjectEntity?> = projectRepository.getProjectById(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val partsState: StateFlow<List<PartEntity>> = projectRepository.getPartsForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chunksState: StateFlow<List<ChunkEntity>> = projectRepository.getChunksForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val audioAssetsState: StateFlow<List<AudioAssetEntity>> = if (audioAssetDao != null) {
        audioAssetDao.getAudioAssetsForProjectFlow(projectId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    } else {
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }

    val allVoicesState: StateFlow<List<com.voconexus.app.core.data.db.TtsVoiceEntity>> = if (voiceRepository != null) {
        voiceRepository.getAllVoices().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    } else {
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }

    val defaultEngineIdState: StateFlow<String> = if (userPrefsManager != null) {
        userPrefsManager.preferences.map { it.defaultEngineId }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = "kokoro-82m"
            )
    } else {
        kotlinx.coroutines.flow.MutableStateFlow("kokoro-82m")
    }

    val jobState: StateFlow<GenerationJob?> = generationRepository.getLatestJobFlow(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateProjectVoice(voiceId: String, engineId: String = "kokoro-82m") {
        viewModelScope.launch {
            try {
                userPrefsManager?.setDefaultEngine(engineId)
                projectRepository.updateProjectVoice(projectId, voiceId, engineId)
            } catch (_: Exception) {}
        }
    }

    fun startGeneration(
        documentId: String = "",
        selectedChunkIds: List<String> = emptyList(),
        selectedPartIds: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                val proj = projectState.value
                val speed = proj?.speed ?: 1.0f
                val pitch = proj?.pitch ?: 0.0f
                val firstChunk = chunksState.value.firstOrNull()
                val targetVoiceId = firstChunk?.voiceId?.ifBlank { null } ?: "hf_alpha"

                val isKokoroVoice = targetVoiceId.startsWith("hf_") || targetVoiceId.startsWith("hm_") ||
                    targetVoiceId.startsWith("af_") || targetVoiceId.startsWith("am_") ||
                    targetVoiceId.startsWith("bf_") || targetVoiceId.startsWith("bm_") ||
                    targetVoiceId.startsWith("ff_") || targetVoiceId.startsWith("ef_") ||
                    targetVoiceId.startsWith("em_") || targetVoiceId.startsWith("if_") ||
                    targetVoiceId.startsWith("jf_") || targetVoiceId.startsWith("zf_") || targetVoiceId.startsWith("zm_") ||
                    targetVoiceId.contains("alpha", ignoreCase = true) || targetVoiceId.contains("beta", ignoreCase = true) ||
                    targetVoiceId.contains("omega", ignoreCase = true) || targetVoiceId.contains("psi", ignoreCase = true)

                val targetEngineId = if (isKokoroVoice) "kokoro-v1.0" else (firstChunk?.engineId?.ifBlank { null } ?: (userPrefsManager?.preferences?.value?.defaultEngineId ?: "edge-tts"))
                val targetModelId = if (isKokoroVoice) "kokoro-v1.0" else "edge-tts"

                generationRepository.createAndStartJob(
                    projectId = projectId,
                    documentId = documentId,
                    engineId = targetEngineId,
                    modelId = targetModelId,
                    voiceId = targetVoiceId,
                    speed = speed,
                    pitch = pitch,
                    selectedChunkIds = selectedChunkIds,
                    selectedPartIds = selectedPartIds
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pauseJob() {
        viewModelScope.launch {
            try {
                generationRepository.pauseActiveJob(projectId)
            } catch (_: Exception) {}
        }
    }

    fun cancelJob() {
        viewModelScope.launch {
            try {
                generationRepository.cancelActiveJob(projectId)
            } catch (_: Exception) {}
        }
    }

    fun deleteAudioAssets(assetIds: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            assetIds.forEach { id ->
                try {
                    audioAssetDao?.deleteAssetById(id)
                } catch (_: Exception) {}
            }
        }
    }

    fun deleteChunkAudios(chunkIds: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            chunkIds.forEach { chunkId ->
                try {
                    val chunk = chunksState.value.find { it.id == chunkId }
                    if (chunk != null) {
                        val path = chunk.audioPath
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists()) f.delete()
                        }
                    }
                    projectRepository.resetChunkAudio(chunkId)
                } catch (_: Exception) {}
            }
        }
    }

    fun exportSingleAudioFile(context: android.content.Context, sourcePath: String, fileName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val srcFile = File(sourcePath)
                if (srcFile.exists()) {
                    val projTitle = projectState.value?.title ?: "Project"
                    val sanitizedTitle = projTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                    val projectMusicDir = File("/storage/emulated/0/Music/VocoNexus/${sanitizedTitle}").also { if (!it.exists()) it.mkdirs() }
                    val destFile = File(projectMusicDir, "${sanitizedTitle}_${fileName}")
                    srcFile.copyTo(destFile, overwrite = true)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Saved to Music/VocoNexus/${sanitizedTitle}/${destFile.name}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun combineAndExportAudio(
        selectedChunks: List<ChunkEntity>,
        projectTitle: String
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val validChunksWithAudio = selectedChunks.filter { it.audioPath.hasValidAudioFile() }
        val sanitizedTitle = projectTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val projectMusicDir = File("/storage/emulated/0/Music/VocoNexus/${sanitizedTitle}").also { if (!it.exists()) it.mkdirs() }

        val seqNumbers = validChunksWithAudio.map { it.sequenceIndex + 1 }.distinct().sorted()
        val numLabel = if (seqNumbers.size == 1) "Part_${seqNumbers.first()}" else "Parts_${seqNumbers.joinToString("_")}"
        val outputFile = File(projectMusicDir, "${sanitizedTitle}_${numLabel}.wav")

        var totalPcmSize = 0L
        val sampleRate = 24000
        val channels = 1

        val pcmStreams = mutableListOf<ByteArray>()

        validChunksWithAudio.forEach { chunk ->
            try {
                val file = File(chunk.audioPath!!)
                if (file.exists() && file.length() > 44) {
                    val bytes = FileInputStream(file).use { it.readBytes() }
                    val pcmOnly = bytes.copyOfRange(44, bytes.size)
                    pcmStreams.add(pcmOnly)
                    totalPcmSize += pcmOnly.size
                }
            } catch (_: Exception) {}
        }

        FileOutputStream(outputFile).use { out ->
            val totalDataLen = totalPcmSize + 36
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
                putInt(totalDataLen.toInt())
                put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())
                put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
                putInt(16)
                putShort(1.toShort())
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(sampleRate * channels * 2)
                putShort((channels * 2).toShort())
                putShort(16.toShort())
                put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
                putInt(totalPcmSize.toInt())
            }.array()

            out.write(header)
            pcmStreams.forEach { pcm ->
                out.write(pcm)
            }
        }

        // Save to audioAssetDao
        if (outputFile.exists() && audioAssetDao != null) {
            try {
                val newAsset = AudioAssetEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    chunkId = validChunksWithAudio.firstOrNull()?.id ?: "",
                    filePath = outputFile.absolutePath,
                    fileFormat = "WAV",
                    mimeType = "audio/wav",
                    sampleRate = sampleRate,
                    channels = channels,
                    bitrate = sampleRate * channels * 16,
                    durationMs = (totalPcmSize / (sampleRate * 2 / 1000)),
                    fileSizeBytes = outputFile.length(),
                    checksum = "",
                    createdAt = System.currentTimeMillis()
                )
                audioAssetDao.insertAsset(newAsset)
            } catch (_: Exception) {}
        }

        return@withContext outputFile.absolutePath
    }

    private fun String?.hasValidAudioFile(): Boolean {
        if (this.isNullOrBlank()) return false
        return File(this).exists() && File(this).length() > 44
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    class Factory(
        private val projectId: String,
        private val projectRepository: ProjectRepository,
        private val generationRepository: GenerationRepository,
        private val audioAssetDao: AudioAssetDao? = null,
        private val voiceRepository: com.voconexus.app.core.data.repository.VoiceRepository? = null,
        private val userPrefsManager: com.voconexus.app.core.preferences.UserPreferencesManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectDetailViewModel(projectId, projectRepository, generationRepository, audioAssetDao, voiceRepository, userPrefsManager) as T
        }
    }
}
