package com.voconexus.app.core.tts.installer

import android.content.Context
import java.io.File

class ModelStorageManager(private val context: Context) {

    val rootModelsDir: File
        get() = File(context.filesDir, "models").also { if (!it.exists()) it.mkdirs() }

    val installedModelsDir: File
        get() = File(rootModelsDir, "installed").also { if (!it.exists()) it.mkdirs() }

    val tempDownloadsDir: File
        get() = File(rootModelsDir, "temp").also { if (!it.exists()) it.mkdirs() }

    fun getInstalledModelDirectory(modelId: String): File {
        return File(installedModelsDir, modelId)
    }

    fun getTempDownloadDirectory(downloadId: String): File {
        return File(tempDownloadsDir, downloadId).also { if (!it.exists()) it.mkdirs() }
    }

    fun isModelInstalled(modelId: String): Boolean {
        val dir = getInstalledModelDirectory(modelId)
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    fun getAvailableStorageBytes(): Long {
        return context.filesDir.usableSpace
    }

    fun deleteModelDirectory(modelId: String): Boolean {
        val dir = getInstalledModelDirectory(modelId)
        return if (dir.exists()) {
            dir.deleteRecursively()
        } else false
    }

    fun clearTempDownloads() {
        if (tempDownloadsDir.exists()) {
            tempDownloadsDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
