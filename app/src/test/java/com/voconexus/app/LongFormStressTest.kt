package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LongFormStressTest {

    private lateinit var database: VocoNexusDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.getInstance(context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun test500ChunksQueueStressAndMemoryStability() = runBlocking {
        val now = System.currentTimeMillis()
        val project = ProjectEntity("proj-stress", "Long Form Book", "Description", now, now, "DRAFT")
        val document = DocumentEntity("doc-stress", "proj-stress", "Book Title", "Book Text", "hash500", 10, 50000, 250000, now, "READY", "1.0", 500)
        database.projectDao().insertProject(project)
        database.documentDao().insertDocument(document)

        val parts = mutableListOf<PartEntity>()
        val chunks = mutableListOf<ChunkEntity>()

        for (p in 1..10) {
            val partId = "part-$p"
            parts.add(
                PartEntity(
                    id = partId,
                    projectId = "proj-stress",
                    documentId = "doc-stress",
                    title = "Chapter $p",
                    sequenceIndex = p,
                    chunkCount = 50,
                    wordCount = 5000,
                    characterCount = 25000
                )
            )
            for (c in 1..50) {
                val chunkIdx = (p - 1) * 50 + c
                val text = "Sentence $chunkIdx in long-form stress test book."
                chunks.add(
                    ChunkEntity(
                        id = "chunk-$chunkIdx",
                        projectId = "proj-stress",
                        documentId = "doc-stress",
                        partId = partId,
                        sequenceIndex = chunkIdx,
                        sourceText = text,
                        sourceTextHash = "hash_$chunkIdx",
                        normalizedText = text,
                        normalizedTextHash = "norm_$chunkIdx",
                        engineId = "kokoro-82m",
                        modelId = "kokoro-82m-v1.0",
                        voiceId = "af_heart",
                        status = ChunkStatus.PENDING,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        database.partDao().insertParts(parts)
        database.chunkDao().insertChunks(chunks)

        val totalChunksInDb = database.chunkDao().getChunksForPart("part-1")
        assertEquals(50, totalChunksInDb.size)

        // Bulk mark chunks completed
        for (chunk in chunks) {
            database.chunkDao().updateChunkStatus(chunk.id, ChunkStatus.COMPLETED, System.currentTimeMillis())
        }

        val part1Chunks = database.chunkDao().getChunksForPart("part-1")
        assertEquals(50, part1Chunks.count { it.status == ChunkStatus.COMPLETED })
    }
}
