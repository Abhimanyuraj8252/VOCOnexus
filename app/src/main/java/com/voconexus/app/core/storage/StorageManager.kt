package com.voconexus.app.core.storage

import android.content.Context
import java.io.File

data class StorageBreakdown(
    val modelsSizeBytes: Long,
    val generatedAudioSizeBytes: Long,
    val tempCacheSizeBytes: Long,
    val totalAppSizeBytes: Long,
    val freeSystemStorageBytes: Long
)

enum class StorageHealthStatus {
    HEALTHY,
    WARNING,
    CRITICAL
}

data class StoragePreflightCheck(
    val status: StorageHealthStatus,
    val requiredBytes: Long,
    val availableBytes: Long,
    val isEnoughStorage: Boolean,
    val warningMessage: String?
)

class StorageManager(private val context: Context) {

    fun getStorageBreakdown(): StorageBreakdown {
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir

        val modelsDir = File(filesDir, "models")
        val audioDir = File(filesDir, "audio")
        val tempDir = File(filesDir, "temp")

        val modelsSize = calculateDirectorySize(modelsDir)
        val audioSize = calculateDirectorySize(audioDir)
        val tempSize = calculateDirectorySize(tempDir) + calculateDirectorySize(cacheDir)
        val totalAppSize = modelsSize + audioSize + tempSize
        val freeSystemSpace = filesDir.usableSpace

        return StorageBreakdown(
            modelsSizeBytes = modelsSize,
            generatedAudioSizeBytes = audioSize,
            tempCacheSizeBytes = tempSize,
            totalAppSizeBytes = totalAppSize,
            freeSystemStorageBytes = freeSystemSpace
        )
    }

    fun checkStoragePreflight(requiredBytes: Long, safetyMarginFactor: Float = 1.2f): StoragePreflightCheck {
        val usableSpace = context.filesDir.usableSpace
        val requiredWithMargin = (requiredBytes * safetyMarginFactor).toLong()
        val isEnough = usableSpace >= requiredWithMargin

        val status = when {
            usableSpace < 500 * 1024 * 1024L || usableSpace < requiredBytes -> StorageHealthStatus.CRITICAL
            usableSpace < requiredWithMargin || usableSpace < 2 * 1024 * 1024 * 1024L -> StorageHealthStatus.WARNING
            else -> StorageHealthStatus.HEALTHY
        }

        val warningMessage = when (status) {
            StorageHealthStatus.CRITICAL -> "Insufficient storage available (${usableSpace / (1024 * 1024)} MB available, ${requiredBytes / (1024 * 1024)} MB required)."
            StorageHealthStatus.WARNING -> "Storage space is getting low (${usableSpace / (1024 * 1024)} MB available)."
            StorageHealthStatus.HEALTHY -> null
        }

        return StoragePreflightCheck(
            status = status,
            requiredBytes = requiredBytes,
            availableBytes = usableSpace,
            isEnoughStorage = isEnough,
            warningMessage = warningMessage
        )
    }

    fun estimateAudioExportSize(durationSeconds: Long, bitrateKbps: Int = 192): Long {
        // Output size estimate = duration * (bitrate in bits/sec / 8)
        return (durationSeconds * (bitrateKbps * 1000L / 8L))
    }

    fun clearTemporaryCache(): Long {
        val cacheDir = context.cacheDir
        val tempDir = File(context.filesDir, "temp")
        val freedBytes = deleteDirectoryContents(cacheDir) + deleteDirectoryContents(tempDir)
        return freedBytes
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return total
    }

    private fun deleteDirectoryContents(dir: File): Long {
        if (!dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                freed += deleteDirectoryContents(file)
                file.delete()
            } else {
                freed += file.length()
                file.delete()
            }
        }
        return freed
    }
}
