package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.generation.queue.GenerationQueue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GenerationQueueTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var queue: GenerationQueue

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        queue = GenerationQueue(database.chunkDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAtomicChunkClaimAndQueueOrdering() {
        runBlocking {
            val now = System.currentTimeMillis()
            val project = ProjectEntity("proj-1", "P1", "D", now, now, "DRAFT")
            val document = DocumentEntity("doc-1", "proj-1", "D1", "Text.", "h", 1, 1, 5, now, "VALID", "1.0", 2)
            val part = PartEntity("part-1", "proj-1", "doc-1", "P1", 0, 2, 2, 10)

            database.projectDao().insertProject(project)
            database.documentDao().insertDocument(document)
            database.partDao().insertPart(part)

            val chunk1 = ChunkEntity(
                id = "chunk-1",
                projectId = "proj-1",
                documentId = "doc-1",
                partId = "part-1",
                sequenceIndex = 0,
                sourceText = "First sentence.",
                sourceTextHash = "hash1",
                normalizedText = "First sentence.",
                normalizedTextHash = "norm1",
                engineId = "kokoro-82m",
                modelId = "kokoro-82m-v1.0",
                voiceId = "af_heart",
                status = ChunkStatus.QUEUED,
                createdAt = now,
                updatedAt = now
            )
            val chunk2 = ChunkEntity(
                id = "chunk-2",
                projectId = "proj-1",
                documentId = "doc-1",
                partId = "part-1",
                sequenceIndex = 1,
                sourceText = "Second sentence.",
                sourceTextHash = "hash2",
                normalizedText = "Second sentence.",
                normalizedTextHash = "norm2",
                engineId = "kokoro-82m",
                modelId = "kokoro-82m-v1.0",
                voiceId = "af_heart",
                status = ChunkStatus.QUEUED,
                createdAt = now,
                updatedAt = now
            )

            database.chunkDao().insertChunks(listOf(chunk1, chunk2))

            val claimed1 = queue.claimNextChunk("proj-1")
            assertNotNull(claimed1)
            assertEquals("chunk-1", claimed1?.id)
            assertEquals(ChunkStatus.GENERATING, claimed1?.status)

            val claimed2 = queue.claimNextChunk("proj-1")
            assertNotNull(claimed2)
            assertEquals("chunk-2", claimed2?.id)
            assertEquals(ChunkStatus.GENERATING, claimed2?.status)

            val claimed3 = queue.claimNextChunk("proj-1")
            assertNull(claimed3)
        }
    }
}
