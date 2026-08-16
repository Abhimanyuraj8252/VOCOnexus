package com.voconexus.app.core.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioStorageManager(private val context: Context) {

    val rootProjectsDir: File
        get() {
            val dir = File(context.filesDir, "VocoNexus/projects")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getProjectDirectory(projectId: String): File {
        val dir = File(rootProjectsDir, projectId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getChunkAudioFile(
        projectId: String,
        documentId: String,
        partId: String,
        chunkId: String,
        formatExtension: String = "wav"
    ): File {
        val chunksDir = File(getProjectDirectory(projectId), "documents/$documentId/audio/parts/$partId/chunks")
        if (!chunksDir.exists()) {
            chunksDir.mkdirs()
        }
        return File(chunksDir, "chunk_$chunkId.$formatExtension")
    }

    fun getTempAudioFile(
        projectId: String,
        documentId: String,
        partId: String,
        chunkId: String
    ): File {
        val chunksDir = File(getProjectDirectory(projectId), "documents/$documentId/audio/parts/$partId/chunks")
        if (!chunksDir.exists()) {
            chunksDir.mkdirs()
        }
        return File(chunksDir, "chunk_$chunkId.tmp")
    }

    suspend fun writeAudioFileAtomically(
        targetFile: File,
        tempFile: File,
        audioData: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            FileOutputStream(tempFile).use { fos ->
                fos.write(audioData)
                fos.flush()
                fos.fd.sync()
            }

            if (!tempFile.exists() || tempFile.length() != audioData.size.toLong()) {
                return@withContext false
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                // Fallback copy & delete if atomic rename fails across partitions
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    fun getAvailableStorageBytes(): Long {
        return context.filesDir.usableSpace
    }

    fun getUsedStorageBytesForProject(projectId: String): Long {
        val projectDir = File(rootProjectsDir, projectId)
        if (!projectDir.exists()) return 0L
        return projectDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun deleteProjectAudioAsync(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val projectDir = File(rootProjectsDir, projectId)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        } else {
            true
        }
    }

    fun deleteChunkAudio(audioPath: String): Boolean {
        val file = File(audioPath)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    fun clearAllTempAudioFiles(): Long {
        var freed = 0L
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach {
                freed += it.length()
                it.delete()
            }
        }
        return freed
    }
}
