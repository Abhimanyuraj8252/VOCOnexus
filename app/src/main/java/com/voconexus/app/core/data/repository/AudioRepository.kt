package com.voconexus.app.core.data.repository

import com.voconexus.app.core.data.dao.AudioAssetDao
import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.db.AudioAssetEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.dao.ProjectAudioSummary
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

enum class AssetIntegrityStatus {
    AVAILABLE,
    MISSING,
    CORRUPTED
}

interface AudioRepository {
    fun getAssetsForProjectFlow(projectId: String): Flow<List<AudioAssetEntity>>
    fun getAssetsForPartFlow(partId: String): Flow<List<AudioAssetEntity>>
    suspend fun getProjectAudioSummary(projectId: String): ProjectAudioSummary
    suspend fun validateAssetIntegrity(assetId: String): AssetIntegrityStatus
    suspend fun deleteAudioAssets(chunkIds: List<String>): Int
}

class AudioRepositoryImpl(
    private val audioAssetDao: AudioAssetDao,
    private val chunkDao: ChunkDao,
    private val storageManager: AudioStorageManager,
    private val audioValidator: AudioValidator = AudioValidator()
) : AudioRepository {

    override fun getAssetsForProjectFlow(projectId: String): Flow<List<AudioAssetEntity>> {
        return audioAssetDao.getAudioAssetsForProjectFlow(projectId)
    }

    override fun getAssetsForPartFlow(partId: String): Flow<List<AudioAssetEntity>> {
        return audioAssetDao.getAudioAssetsForPartFlow(partId)
    }

    override suspend fun getProjectAudioSummary(projectId: String): ProjectAudioSummary = withContext(Dispatchers.IO) {
        audioAssetDao.getProjectAudioSummary(projectId) ?: ProjectAudioSummary()
    }

    override suspend fun validateAssetIntegrity(assetId: String): AssetIntegrityStatus = withContext(Dispatchers.IO) {
        val assets = audioAssetDao.getAllAudioAssets().filter { it.id == assetId }
        val asset = assets.firstOrNull() ?: return@withContext AssetIntegrityStatus.MISSING
        val file = File(asset.filePath)

        if (!file.exists()) {
            return@withContext AssetIntegrityStatus.MISSING
        }

        val validation = audioValidator.validateWavFile(file)
        if (!validation.isValid) {
            return@withContext AssetIntegrityStatus.CORRUPTED
        }

        return@withContext AssetIntegrityStatus.AVAILABLE
    }

    override suspend fun deleteAudioAssets(chunkIds: List<String>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        val now = System.currentTimeMillis()

        for (chunkId in chunkIds) {
            val asset = audioAssetDao.getAssetForChunk(chunkId)
            if (asset != null) {
                storageManager.deleteChunkAudio(asset.filePath)
                audioAssetDao.deleteAssetForChunk(chunkId)
            }

            val chunk = chunkDao.getChunkById(chunkId)
            if (chunk != null) {
                chunkDao.updateChunk(
                    chunk.copy(
                        status = ChunkStatus.PENDING,
                        audioPath = null,
                        durationMs = 0,
                        fileSizeBytes = 0,
                        updatedAt = now
                    )
                )
                deletedCount++
            }
        }

        return@withContext deletedCount
    }
}
