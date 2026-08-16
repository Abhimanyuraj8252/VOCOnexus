package com.voconexus.app.core.tts.installer

import android.content.Context
import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.core.tts.catalog.ModelDescriptor
import com.voconexus.app.core.tts.catalog.toTtsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

enum class DownloadStatus {
    NOT_STARTED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLING,
    READY,
    FAILED,
    CANCELLED,
    CORRUPTED
}

data class DownloadProgress(
    val modelId: String,
    val status: DownloadStatus = DownloadStatus.NOT_STARTED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Float = 0f,
    val downloadSpeedMbPerSec: Float = 0f,
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null
)

class ModelDownloadManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val modelInstaller: ModelInstaller
) {

    private val _downloadState = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadProgress>> = _downloadState.asStateFlow()

    private val pausedFlags = mutableSetOf<String>()
    private val cancelledFlags = mutableSetOf<String>()

    fun getProgress(modelId: String): DownloadProgress {
        return _downloadState.value[modelId] ?: DownloadProgress(modelId = modelId)
    }

    suspend fun downloadModel(
        descriptor: ModelDescriptor
    ) = withContext(Dispatchers.IO) {
        val modelId = descriptor.modelId

        // 1. Storage Preflight Check
        val storageCheck = storageManager.checkStoragePreflight(requiredBytes = descriptor.fileSizeBytes)
        if (!storageCheck.isEnoughStorage) {
            updateProgress(modelId, DownloadStatus.FAILED, errorMessage = storageCheck.warningMessage ?: "Insufficient storage.")
            return@withContext
        }

        pausedFlags.remove(modelId)
        cancelledFlags.remove(modelId)

        updateProgress(modelId, DownloadStatus.DOWNLOADING, totalBytes = descriptor.fileSizeBytes)

        val tempDir = File(context.filesDir, "models/staging/$modelId").also { it.mkdirs() }
        val targetFile = File(tempDir, "archive.zip")

        var bytesDownloaded = if (targetFile.exists()) targetFile.length() else 0L

        try {
            if (descriptor.downloadSourceUrl.startsWith("http")) {
                val url = URL(descriptor.downloadSourceUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                if (bytesDownloaded > 0) {
                    connection.setRequestProperty("Range", "bytes=$bytesDownloaded-")
                }

                connection.connect()

                val responseCode = connection.responseCode
                val totalLength = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    bytesDownloaded + connection.contentLengthLong
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    bytesDownloaded = 0L // Server restarted from beginning
                    connection.contentLengthLong
                } else {
                    descriptor.fileSizeBytes
                }

                val input = connection.inputStream
                val output = RandomAccessFile(targetFile, "rw")
                output.seek(bytesDownloaded)

                val buffer = ByteArray(16384)
                var bytesRead = input.read(buffer)
                var startTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L

                while (bytesRead != -1) {
                    if (cancelledFlags.contains(modelId)) {
                        output.close()
                        input.close()
                        targetFile.delete()
                        updateProgress(modelId, DownloadStatus.CANCELLED)
                        return@withContext
                    }

                    if (pausedFlags.contains(modelId)) {
                        output.close()
                        input.close()
                        updateProgress(modelId, DownloadStatus.PAUSED, bytesDownloaded = bytesDownloaded, totalBytes = totalLength)
                        return@withContext
                    }

                    output.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    bytesSinceLastCalc += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = (now - startTime).coerceAtLeast(1L)
                    val speed = (bytesSinceLastCalc.toFloat() / (1024 * 1024)) / (timeDiff / 1000f)
                    val remainingBytes = totalLength - bytesDownloaded
                    val eta = if (speed > 0) (remainingBytes / (speed * 1024 * 1024)).toLong() else 0L

                    val percent = (bytesDownloaded.toFloat() / totalLength.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    updateProgress(
                        modelId = modelId,
                        status = DownloadStatus.DOWNLOADING,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalLength,
                        progressPercent = percent,
                        speed = speed,
                        eta = eta
                    )

                    if (timeDiff > 1000) {
                        startTime = now
                        bytesSinceLastCalc = 0L
                    }

                    bytesRead = input.read(buffer)
                }

                output.close()
                input.close()
            }

            // 2. Verifying & Installing
            updateProgress(modelId, DownloadStatus.VERIFYING, bytesDownloaded = bytesDownloaded, totalBytes = bytesDownloaded)

            val ttsModel = descriptor.toTtsModel()
            modelInstaller.downloadAndInstallModel(ttsModel)

            updateProgress(modelId, DownloadStatus.READY, bytesDownloaded = bytesDownloaded, totalBytes = bytesDownloaded, progressPercent = 1.0f)
        } catch (e: Exception) {
            updateProgress(modelId, DownloadStatus.FAILED, errorMessage = e.message ?: "Download failed.")
        }
    }

    fun pauseDownload(modelId: String) {
        pausedFlags.add(modelId)
    }

    fun cancelDownload(modelId: String) {
        cancelledFlags.add(modelId)
    }

    private fun updateProgress(
        modelId: String,
        status: DownloadStatus,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = 0L,
        progressPercent: Float = 0f,
        speed: Float = 0f,
        eta: Long = 0L,
        errorMessage: String? = null
    ) {
        val currentMap = _downloadState.value.toMutableMap()
        currentMap[modelId] = DownloadProgress(
            modelId = modelId,
            status = status,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            progressPercent = progressPercent,
            downloadSpeedMbPerSec = speed,
            etaSeconds = eta,
            errorMessage = errorMessage
        )
        _downloadState.value = currentMap
    }
}
