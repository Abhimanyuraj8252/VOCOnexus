package com.voconexus.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.domain.FileIntegrityValidator
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FileIntegrityValidatorTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var storageManager: AudioStorageManager
    private lateinit var validator: FileIntegrityValidator
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VocoNexusDatabase.createInMemory(context)
        storageManager = AudioStorageManager(context)
        validator = FileIntegrityValidator(database.chunkDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAtomicFileWritePipeline() = runBlocking {
        val targetFile = File(context.filesDir, "test_target.wav")
        val tempFile = File(context.filesDir, "test_temp.tmp")
        val dummyData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        val success = storageManager.writeAudioFileAtomically(targetFile, tempFile, dummyData)
        assertTrue(success)
        assertTrue(targetFile.exists())
        assertEquals(8L, targetFile.length())
        assertFalse(tempFile.exists())
    }

    @Test
    fun testMissingAudioFileDetection() = runBlocking {
        val chunk = ChunkEntity(
            id = "c1",
            projectId = "p1",
            documentId = "d1",
            partId = "part1",
            sequenceIndex = 0,
            sourceText = "Text",
            sourceTextHash = "h1",
            normalizedText = "text",
            normalizedTextHash = "nh1",
            engineId = "kokoro-82m",
            modelId = "kokoro-v1.0",
            voiceId = "af_heart",
            status = ChunkStatus.COMPLETED,
            audioPath = "/non_existent_directory/fake_audio.wav",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val isValid = validator.validateChunkFileIntegrity(chunk)
        assertFalse(isValid)
    }
}
