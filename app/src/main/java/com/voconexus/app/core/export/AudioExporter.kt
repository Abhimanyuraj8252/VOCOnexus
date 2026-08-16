package com.voconexus.app.core.export

import android.content.Context
import android.net.Uri
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class AudioExporter(
    private val context: Context,
    private val storageManager: AudioStorageManager,
    private val audioCombiner: AudioCombiner = AudioCombiner(),
    private val audioValidator: AudioValidator = AudioValidator()
) {

    fun sanitizeFilename(rawName: String): String {
        return rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    suspend fun exportSingleFileToUri(
        sourceFile: File,
        targetUri: Uri,
        onProgress: (progressFraction: Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext false

        try {
            val totalBytes = sourceFile.length()
            var copiedBytes = 0L
            val buffer = ByteArray(8192)

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(copiedBytes.toFloat() / totalBytes.toFloat())
                        }
                    }
                    outputStream.flush()
                }
            } ?: return@withContext false

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun combineAndExportToUri(
        sourceFiles: List<File>,
        targetUri: Uri,
        onProgress: (progressFraction: Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (sourceFiles.isEmpty()) return@withContext false

        val tempCombinedFile = File(context.cacheDir, "temp_combine_${System.currentTimeMillis()}.wav")

        try {
            val combinedSuccess = audioCombiner.combineWavFiles(
                sourceFiles = sourceFiles,
                targetOutputFile = tempCombinedFile,
                onProgress = { progress -> onProgress(progress * 0.7f) }
            )

            if (!combinedSuccess || !tempCombinedFile.exists()) {
                return@withContext false
            }

            val exportSuccess = exportSingleFileToUri(
                sourceFile = tempCombinedFile,
                targetUri = targetUri,
                onProgress = { progress -> onProgress(0.7f + (progress * 0.3f)) }
            )

            return@withContext exportSuccess
        } finally {
            if (tempCombinedFile.exists()) {
                tempCombinedFile.delete()
            }
        }
    }
}
