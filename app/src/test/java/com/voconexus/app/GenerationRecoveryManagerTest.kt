package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.GenerationJobEntity
import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.generation.recovery.GenerationRecoveryManager
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GenerationRecoveryManagerTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var recoveryManager: GenerationRecoveryManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        val storageManager = AudioStorageManager(context)
        val validator = AudioValidator()

        recoveryManager = GenerationRecoveryManager(
            jobDao = database.generationJobDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            audioValidator = validator
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testStaleJobAndGeneratingChunkRecovery() {
        runBlocking {
            val now = System.currentTimeMillis()
            val project = ProjectEntity("proj-1", "P1", "D", now, now, "DRAFT")
            val document = DocumentEntity("doc-1", "proj-1", "D1", "Text.", "h", 1, 1, 5, now, "VALID", "1.0", 1)
            val part = PartEntity("part-1", "proj-1", "doc-1", "P1", 0, 1, 1, 5)

            database.projectDao().insertProject(project)
            database.documentDao().insertDocument(document)
            database.partDao().insertPart(part)

            val job = GenerationJobEntity(
                id = "stale-job",
                projectId = "proj-1",
                status = GenerationJobStatus.RUNNING,
                totalChunks = 5,
                startedAt = now
            )
            val chunk = ChunkEntity(
                id = "stale-chunk",
                projectId = "proj-1",
                documentId = "doc-1",
                partId = "part-1",
                sequenceIndex = 0,
                sourceText = "Text",
                sourceTextHash = "h",
                normalizedText = "Text",
                normalizedTextHash = "nh",
                engineId = "kokoro-82m",
                modelId = "kokoro-82m-v1.0",
                voiceId = "af_heart",
                status = ChunkStatus.GENERATING,
                createdAt = now,
                updatedAt = now
            )

            database.generationJobDao().insertJob(job)
            database.chunkDao().insertChunk(chunk)

            recoveryManager.performStartupRecoveryCheck("proj-1")

            val recoveredJob = database.generationJobDao().getJobById("stale-job")
            assertNotNull(recoveredJob)
            assertEquals(GenerationJobStatus.PAUSED, recoveredJob?.status)

            val recoveredChunk = database.chunkDao().getChunkById("stale-chunk")
            assertNotNull(recoveredChunk)
            assertEquals(ChunkStatus.QUEUED, recoveredChunk?.status)
        }
    }
}
