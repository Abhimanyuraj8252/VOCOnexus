package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseStressTest {

    private lateinit var database: VocoNexusDatabase

    @Before
    fun setUp() {
        database = VocoNexusDatabase.createInMemory(
            ApplicationProvider.getApplicationContext()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test10000ChunkInsertionAndAggregationPerformance() = runBlocking {
        val currentTime = System.currentTimeMillis()
        val projectId = "stress-proj-1"

        val project = ProjectEntity(
            id = projectId,
            title = "10,000 Chunk Stress Audiobook",
            description = "High volume scale stress test",
            createdAt = currentTime,
            updatedAt = currentTime,
            status = "DRAFT",
            partCount = 500,
            chunkCount = 10000
        )
        database.projectDao().insertProject(project)

        val document = DocumentEntity(
            id = "stress-doc-1",
            projectId = projectId,
            title = "Stress Doc",
            rawText = "Stress test text",
            textHash = "hash10k",
            partCount = 500,
            wordCount = 100000,
            characterCount = 600000,
            createdAt = currentTime
        )
        database.documentDao().insertDocument(document)

        val parts = (0 until 500).map { i ->
            PartEntity(
                id = "part-$i",
                projectId = projectId,
                documentId = "stress-doc-1",
                title = "Part ${i + 1}",
                sequenceIndex = i,
                chunkCount = 20,
                wordCount = 200,
                characterCount = 1200
            )
        }
        database.partDao().insertParts(parts)

        val totalChunks = 10000
        val batchSize = 1000

        val insertDuration = measureTimeMillis {
            for (b in 0 until totalChunks / batchSize) {
                val batch = (0 until batchSize).map { idx ->
                    val globalIdx = b * batchSize + idx
                    val partIdx = globalIdx / 20
                    val status = when (globalIdx % 5) {
                        0 -> ChunkStatus.COMPLETED
                        1 -> ChunkStatus.FAILED
                        2 -> ChunkStatus.GENERATING
                        3 -> ChunkStatus.QUEUED
                        else -> ChunkStatus.PENDING
                    }

                    ChunkEntity(
                        id = "chunk-$globalIdx",
                        projectId = projectId,
                        documentId = "stress-doc-1",
                        partId = "part-$partIdx",
                        sequenceIndex = globalIdx,
                        sourceText = "This is stress test sentence number $globalIdx for performance verification.",
                        sourceTextHash = "src_hash_$globalIdx",
                        normalizedText = "this is stress test sentence number $globalIdx for performance verification",
                        normalizedTextHash = "norm_hash_$globalIdx",
                        engineId = "kokoro-82m",
                        modelId = "kokoro-v1.0",
                        voiceId = "af_heart",
                        status = status,
                        durationMs = if (status == ChunkStatus.COMPLETED) 2500L else 0L,
                        fileSizeBytes = if (status == ChunkStatus.COMPLETED) 120000L else 0L,
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                }
                database.chunkDao().insertChunks(batch)
            }
        }

        println("Inserted 10,000 Chunks into Room in ${insertDuration}ms")
        assertTrue("Batch insertion of 10,000 chunks should finish under 10000ms", insertDuration < 10000)

        // Test Pagination Performance
        val pageDuration = measureTimeMillis {
            val pagedChunks = database.chunkDao().getChunksPaged(projectId, limit = 50, offset = 5000)
            assertEquals(50, pagedChunks.size)
            assertEquals(5000, pagedChunks[0].sequenceIndex)
        }
        assertTrue("Paginated query offset 5000 should finish under 500ms", pageDuration < 500)

        // Test SQL Aggregation Performance
        val aggDuration = measureTimeMillis {
            val completedDuration = database.chunkDao().getCompletedDurationForProject(projectId)
            assertEquals(2000 * 2500L, completedDuration)
        }
        assertTrue("SQL Aggregation on 10,000 chunks should finish under 500ms", aggDuration < 500)
    }
}
