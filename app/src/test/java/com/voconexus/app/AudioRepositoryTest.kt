package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.AudioAssetEntity
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.AssetIntegrityStatus
import com.voconexus.app.core.data.repository.AudioRepositoryImpl
import com.voconexus.app.core.generation.audio.WavAudioSink
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioRepositoryTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var repository: AudioRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = AudioStorageManager(context)
        repository = AudioRepositoryImpl(database.audioAssetDao(), database.chunkDao(), storageManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAssetIntegrityAndDeletion() {
        runBlocking {
            val now = System.currentTimeMillis()
            val project = ProjectEntity("proj-1", "P1", "D", now, now, "DRAFT")
            val document = DocumentEntity("doc-1", "proj-1", "D1", "Text.", "h", 1, 1, 5, now, "VALID", "1.0", 1)
            val part = PartEntity("part-1", "proj-1", "doc-1", "P1", 0, 1, 1, 5)

            database.projectDao().insertProject(project)
            database.documentDao().insertDocument(document)
            database.partDao().insertPart(part)

            val audioFile = File(System.getProperty("java.io.tmpdir"), "audio_test_${now}.wav")
            val sink = WavAudioSink()
            sink.open(audioFile, 24000, 1)
            sink.writePcm(ByteArray(24000))
            sink.flushAndClose()

            val chunk = ChunkEntity("chunk-1", "proj-1", "doc-1", "part-1", 0, "Text", "h", "Text", "nh", "engine", "model", "voice", status = ChunkStatus.COMPLETED, audioPath = audioFile.absolutePath, createdAt = now, updatedAt = now)
            val asset = AudioAssetEntity("asset-1", "chunk-1", audioFile.absolutePath, "WAV", "audio/wav", 24000, 1, 384000, 1000, audioFile.length(), "sha", now)

            database.chunkDao().insertChunk(chunk)
            database.audioAssetDao().insertAsset(asset)

            val status1 = repository.validateAssetIntegrity("asset-1")
            assertEquals(AssetIntegrityStatus.AVAILABLE, status1)

            val deletedCount = repository.deleteAudioAssets(listOf("chunk-1"))
            assertEquals(1, deletedCount)

            val updatedChunk = database.chunkDao().getChunkById("chunk-1")
            assertEquals(ChunkStatus.PENDING, updatedChunk?.status)
        }
    }
}
