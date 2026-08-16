package com.voconexus.app.core.domain

import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import java.io.File

data class IntegrityScanResult(
    val validCompletedChunks: Int = 0,
    val missingAudioChunks: List<ChunkEntity> = emptyList(),
    val orphanFiles: List<File> = emptyList(),
    val staleTempFiles: List<File> = emptyList()
)

class FileIntegrityValidator(
    private val chunkDao: ChunkDao
) {
    suspend fun validateChunkFileIntegrity(chunk: ChunkEntity): Boolean {
        if (chunk.status != ChunkStatus.COMPLETED) return true
        val path = chunk.audioPath ?: return false
        val file = File(path)
        return file.exists() && file.isFile && file.length() > 0L && file.canRead()
    }

    suspend fun scanAndReconcileProject(projectId: String, projectAudioDir: File): IntegrityScanResult {
        val chunks = chunkDao.getChunksForProject(projectId)
        val completedChunks = chunks.filter { it.status == ChunkStatus.COMPLETED }

        val missingAudioChunks = mutableListOf<ChunkEntity>()
        var validCount = 0

        completedChunks.forEach { chunk ->
            if (!validateChunkFileIntegrity(chunk)) {
                missingAudioChunks.add(chunk)
                chunkDao.updateChunkStatus(chunk.id, ChunkStatus.NEEDS_REGENERATION, System.currentTimeMillis())
            } else {
                validCount++
            }
        }

        val knownAudioPaths = chunks.mapNotNull { it.audioPath }.toSet()
        val orphanFiles = mutableListOf<File>()
        val staleTempFiles = mutableListOf<File>()
        val currentTime = System.currentTimeMillis()

        if (projectAudioDir.exists() && projectAudioDir.isDirectory) {
            projectAudioDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    if (file.name.endsWith(".tmp")) {
                        // Stale temp file older than 30 minutes
                        if (currentTime - file.lastModified() > 30 * 60 * 1000L) {
                            staleTempFiles.add(file)
                        }
                    } else if (file.name.startsWith("chunk_") && !knownAudioPaths.contains(file.absolutePath)) {
                        orphanFiles.add(file)
                    }
                }
            }
        }

        return IntegrityScanResult(
            validCompletedChunks = validCount,
            missingAudioChunks = missingAudioChunks,
            orphanFiles = orphanFiles,
            staleTempFiles = staleTempFiles
        )
    }

    fun purgeStaleTempFiles(staleFiles: List<File>): Int {
        var count = 0
        staleFiles.forEach { file ->
            if (file.exists() && file.name.endsWith(".tmp")) {
                if (file.delete()) count++
            }
        }
        return count
    }
}
