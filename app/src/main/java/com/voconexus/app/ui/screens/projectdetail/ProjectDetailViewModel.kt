package com.voconexus.app.ui.screens.projectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.repository.GenerationRepository
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.generation.audio.AudioValidator
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

    fun stopJob() {
        viewModelScope.launch {
            try {
                generationRepository.stopActiveJob(projectId)
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
            val activeJob = jobState.value
            if (activeJob != null && (activeJob.status == com.voconexus.app.core.data.db.GenerationJobStatus.RUNNING ||
                        activeJob.status == com.voconexus.app.core.data.db.GenerationJobStatus.QUEUED ||
                        activeJob.status == com.voconexus.app.core.data.db.GenerationJobStatus.STARTING)) {
                try {
                    generationRepository.stopActiveJob(projectId)
                } catch (_: Exception) {}
            }
            assetIds.forEach { id ->
                try {
                    audioAssetDao?.deleteAssetById(id)
                } catch (_: Exception) {}
            }
        }
    }

    fun deleteChunkAudios(chunkIds: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val activeJob = jobState.value
            if (activeJob != null && (activeJob.status == com.voconexus.app.core.data.db.GenerationJobStatus.RUNNING ||
                        activeJob.status == com.voconexus.app.core.data.db.GenerationJobStatus.QUEUED ||
                        activeJob.status == com.voconexus.app.core.data.db.GenerationJobStatus.STARTING)) {
                try {
                    generationRepository.stopActiveJob(projectId)
                } catch (_: Exception) {}
            }
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

    private fun getExportDirectory(context: android.content.Context?, projectTitle: String): File {
        val sanitizedTitle = projectTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        
        // 1. Try public Music/VocoNexus folder if directly writable
        try {
            val publicDir = File(File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "VocoNexus"), sanitizedTitle)
            if (!publicDir.exists()) publicDir.mkdirs()
            val testFile = File(publicDir, ".test_${System.currentTimeMillis()}")
            if (testFile.createNewFile()) {
                testFile.delete()
                return publicDir
            }
        } catch (e: Throwable) {
            android.util.Log.w("VocoNexusExport", "Public Music directory not directly writable (${e.message}), using fallback storage")
        }

        // 2. Try App's external Music directory (always writable on Android 10+)
        if (context != null) {
            val appExtMusic = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            if (appExtMusic != null) {
                val fallbackDir = File(File(appExtMusic, "VocoNexus"), sanitizedTitle)
                if (!fallbackDir.exists()) fallbackDir.mkdirs()
                return fallbackDir
            }
        }

        // 3. Try App Cache directory
        if (context != null) {
            val cacheDir = File(File(context.cacheDir, "VocoNexus"), sanitizedTitle)
            if (!cacheDir.exists()) cacheDir.mkdirs()
            return cacheDir
        }

        // 4. System Temp directory fallback
        val tempFallback = File(File(System.getProperty("java.io.tmpdir") ?: "/tmp", "VocoNexus"), sanitizedTitle)
        if (!tempFallback.exists()) tempFallback.mkdirs()
        return tempFallback
    }

    private fun saveToPublicMusicFolder(
        context: android.content.Context?,
        sourceFile: File,
        fileName: String,
        projectTitle: String,
        mimeType: String
    ): String {
        val sanitizedTitle = projectTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        fun logExp(msg: String) {
            try { android.util.Log.d("VocoNexusExport", msg) } catch (_: Throwable) { println("[VocoNexusExport] $msg") }
        }

        if (context == null) {
            logExp("Context is null, returning source file path: ${sourceFile.absolutePath}")
            return sourceFile.absolutePath
        }

        // 1. Android 10+ (API 29+) MediaStore API (Scoped Storage compliant)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/VocoNexus/$sanitizedTitle")
                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val collection = android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    logExp("Successfully saved via MediaStore to Music/VocoNexus/$sanitizedTitle/$fileName (URI: $uri)")
                    
                    val publicPath = "/storage/emulated/0/Music/VocoNexus/$sanitizedTitle/$fileName"
                    return publicPath
                }
            } catch (e: Throwable) {
                logExp("MediaStore insertion failed: ${e.message}, attempting direct public copy fallback")
            }
        }

        // 2. Direct Public Music Copy (Android 9 or legacy)
        try {
            val publicDir = File(File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "VocoNexus"), sanitizedTitle)
            if (!publicDir.exists()) publicDir.mkdirs()
            val destFile = File(publicDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            logExp("Successfully copied to public music dir: ${destFile.absolutePath}")
            try {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            } catch (_: Throwable) {}
            return destFile.absolutePath
        } catch (e: Throwable) {
            logExp("Direct public music copy failed: ${e.message}")
        }

        // 3. App-specific external Music directory fallback
        try {
            val appExtMusic = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            if (appExtMusic != null) {
                val fallbackDir = File(File(appExtMusic, "VocoNexus"), sanitizedTitle)
                if (!fallbackDir.exists()) fallbackDir.mkdirs()
                val destFile = File(fallbackDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                logExp("Copied to app external music fallback: ${destFile.absolutePath}")
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
                } catch (_: Throwable) {}
                return destFile.absolutePath
            }
        } catch (e: Throwable) {
            logExp("App external music copy failed: ${e.message}")
        }

        return sourceFile.absolutePath
    }

    fun exportSingleAudioFile(context: android.content.Context, sourcePath: String, fileName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val srcFile = File(sourcePath)
                if (srcFile.exists()) {
                    val projTitle = projectState.value?.title ?: "Project"
                    val mimeType = if (fileName.lowercase().endsWith(".mp3")) "audio/mpeg" else "audio/wav"
                    val savedPath = saveToPublicMusicFolder(context, srcFile, fileName, projTitle, mimeType)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Saved to Music/VocoNexus/${projTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}/${fileName}", android.widget.Toast.LENGTH_LONG).show()
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
        projectTitle: String,
        exportFormat: String = "MP3",
        context: android.content.Context? = null
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        fun logExport(message: String) {
            try {
                android.util.Log.d("VocoNexusExport", message)
            } catch (_: Throwable) {
                println("[VocoNexusExport] $message")
            }
        }

        logExport("=== Starting Audio Export Pipeline ===")
        logExport("Selected chunks count: ${selectedChunks.size}, Target format: $exportFormat, Project title: '$projectTitle'")

        val validChunksWithAudio = selectedChunks
            .filter { it.audioPath.hasValidAudioFile() }
            .sortedBy { it.sequenceIndex }

        logExport("Valid audio chunks found: ${validChunksWithAudio.size} / ${selectedChunks.size}")

        if (validChunksWithAudio.isEmpty()) {
            logExport("Export failed: No valid generated audio chunks found")
            throw IllegalStateException("No valid audio files found for export. Please generate audio for this part first.")
        }

        val sanitizedTitle = projectTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")

        // Isolated temporary processing folder inside app cache (100% writable by POSIX C/C++ & FFmpeg)
        val tempProcessingDir = File(
            context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"),
            "export_processing_${System.currentTimeMillis()}"
        )
        if (!tempProcessingDir.exists()) tempProcessingDir.mkdirs()

        val seqNumbers = validChunksWithAudio.map { it.sequenceIndex + 1 }.distinct().sorted()
        val numLabel = if (seqNumbers.size == 1) "Part_${seqNumbers.first()}" else "Parts_${seqNumbers.first()}_to_${seqNumbers.last()}"

        val formatExt = exportFormat.lowercase()
        val tempWavFile = File(tempProcessingDir, "temp_combined.wav")
        val tempMp3File = File(tempProcessingDir, "temp_export.mp3")
        val silenceFile = File(tempProcessingDir, "temp_silence.wav")

        try {
            val combiner = com.voconexus.app.core.export.AudioCombiner()
            val firstFile = File(validChunksWithAudio.first().audioPath!!)
            val headerInfo = combiner.parseWavHeader(firstFile)
            val sampleRate = headerInfo?.sampleRate ?: 24000
            val channels = headerInfo?.channels ?: 1

            logExport("Detected primary audio format: sampleRate=$sampleRate Hz, channels=$channels, bitsPerSample=${headerInfo?.bitsPerSample ?: 16}")

            // 20ms silence padding file
            val silencePaddingMs = 20L
            val silenceSampleCount = ((sampleRate * silencePaddingMs) / 1000L).toInt()
            val silenceBytes = ByteArray(silenceSampleCount * channels * 2)

            val silenceSink = com.voconexus.app.core.generation.audio.WavAudioSink()
            silenceSink.open(silenceFile, sampleRate, channels)
            silenceSink.writePcm(silenceBytes)
            silenceSink.flushAndClose()

            val sourceFiles = mutableListOf<File>()
            validChunksWithAudio.forEachIndexed { index, chunk ->
                val chunkFile = File(chunk.audioPath!!)
                sourceFiles.add(chunkFile)
                logExport("  Chunk #${index + 1} (seq ${chunk.sequenceIndex + 1}): ${chunkFile.name} (size: ${chunkFile.length()} bytes)")
                if (index < validChunksWithAudio.size - 1) {
                    sourceFiles.add(silenceFile)
                }
            }

            // Attempt 1: Fast stream combining with AudioCombiner
            logExport("Combining ${validChunksWithAudio.size} chunk files using AudioCombiner stream processing...")
            var combineSuccess = combiner.combineWavFiles(sourceFiles, tempWavFile)

            // Attempt 2: If AudioCombiner fails (e.g. non-WAV input or sample rate mismatch), fallback to FFmpeg Concat
            if (!combineSuccess || !tempWavFile.exists() || tempWavFile.length() <= 44) {
                logExport("AudioCombiner produced invalid output. Falling back to FFmpeg concat demuxer...")
                val concatListFile = File(tempProcessingDir, "concat_list.txt")
                try {
                    val concatContent = sourceFiles.joinToString("\n") { file ->
                        val escapedPath = file.absolutePath.replace("'", "'\\''")
                        "file '$escapedPath'"
                    }
                    concatListFile.writeText(concatContent)

                    val ffmpegConcatCmd = "-y -f concat -safe 0 -i \"%s\" -c:a pcm_s16le -ar %d -ac %d \"%s\""
                        .format(java.util.Locale.US, concatListFile.absolutePath, sampleRate, channels, tempWavFile.absolutePath)
                    logExport("Executing FFmpeg Concat command: $ffmpegConcatCmd")

                    val session = com.arthenica.ffmpegkit.FFmpegKit.execute(ffmpegConcatCmd)
                    combineSuccess = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && tempWavFile.exists() && tempWavFile.length() > 44
                    logExport("FFmpeg Concat result: success=$combineSuccess, returnCode=${session.returnCode}")
                } catch (e: Throwable) {
                    logExport("FFmpeg Concat exception: ${e.message}")
                } finally {
                    if (concatListFile.exists()) concatListFile.delete()
                }
            }

            if (!combineSuccess || !tempWavFile.exists() || tempWavFile.length() <= 44) {
                logExport("All WAV combining attempts failed.")
                throw IllegalStateException("Failed to stitch audio chunks into valid audio format.")
            }

            logExport("WAV combination succeeded! Raw WAV size: ${tempWavFile.length()} bytes (${tempWavFile.length() / (1024 * 1024)} MB)")

            val processedFileToExport: File = if (formatExt == "mp3") {
                logExport("Converting combined WAV to MP3 in cache using FFmpeg...")
                val mp3Cmd = "-y -i \"%s\" -c:a mp3 -b:a 192k \"%s\""
                    .format(java.util.Locale.US, tempWavFile.absolutePath, tempMp3File.absolutePath)
                logExport("Executing FFmpeg MP3 command: $mp3Cmd")

                val session = com.arthenica.ffmpegkit.FFmpegKit.execute(mp3Cmd)
                val returnCode = session.returnCode
                val logs = session.logsAsString ?: ""

                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode) && tempMp3File.exists() && tempMp3File.length() > 0) {
                    logExport("MP3 conversion successful! Compressed size: ${tempMp3File.length()} bytes (${tempMp3File.length() / 1024} KB)")
                    tempMp3File
                } else {
                    logExport("FFmpeg MP3 conversion failed with return code $returnCode. Logs: ${logs.takeLast(300)}. Falling back to WAV.")
                    tempWavFile
                }
            } else {
                logExport("Exporting raw WAV format directly. Size: ${tempWavFile.length()} bytes")
                tempWavFile
            }

            val finalFileName = "${sanitizedTitle}_${numLabel}.${processedFileToExport.extension}"
            val mimeType = if (processedFileToExport.extension.lowercase() == "mp3") "audio/mpeg" else "audio/wav"

            // Export to public storage via Scoped Storage MediaStore API
            logExport("Saving processed audio file to public storage via MediaStore API...")
            val finalSavedPath = saveToPublicMusicFolder(context, processedFileToExport, finalFileName, projectTitle, mimeType)
            logExport("Export saved to path: $finalSavedPath")

            // Save asset entity to Database
            if (audioAssetDao != null) {
                try {
                    val savedFile = File(finalSavedPath)
                    val fileSize = if (savedFile.exists()) savedFile.length() else processedFileToExport.length()
                    val durationMs = headerInfo?.let {
                        val totalPcm = (tempWavFile.length() - 44).coerceAtLeast(0)
                        (totalPcm / (sampleRate.toLong() * channels * 2 / 1000L))
                    } ?: 0L

                    val newAsset = AudioAssetEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        chunkId = validChunksWithAudio.firstOrNull()?.id ?: "",
                        filePath = finalSavedPath,
                        fileFormat = processedFileToExport.extension.uppercase(),
                        mimeType = mimeType,
                        sampleRate = sampleRate,
                        channels = channels,
                        bitrate = if (processedFileToExport.extension.lowercase() == "mp3") 192000 else (sampleRate * channels * 16),
                        durationMs = durationMs,
                        fileSizeBytes = fileSize,
                        checksum = "",
                        createdAt = System.currentTimeMillis()
                    )
                    audioAssetDao.insertAsset(newAsset)
                    logExport("Inserted exported asset entity into DB: ${newAsset.id}")
                } catch (e: Exception) {
                    logExport("Warning: Failed to insert audio asset into DB: ${e.message}")
                }
            }

            logExport("=== Audio Export Pipeline Completed Successfully ===")
            return@withContext finalSavedPath
        } catch (e: Exception) {
            logExport("=== Audio Export Pipeline Failed: ${e.message} ===")
            throw e
        } finally {
            try {
                tempProcessingDir.deleteRecursively()
            } catch (_: Throwable) {}
        }
    }


    private fun String?.hasValidAudioFile(): Boolean {
        if (this.isNullOrBlank()) return false
        val file = File(this)
        return file.exists() && file.length() > 100
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
