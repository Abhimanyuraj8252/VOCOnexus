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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VocoNexusDatabaseTest {

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
    fun testInsertAndRetrieveProjectStructure() = runBlocking {
        val currentTime = System.currentTimeMillis()

        val project = ProjectEntity(
            id = "proj-1",
            title = "Test Audiobook",
            description = "A test description",
            createdAt = currentTime,
            updatedAt = currentTime,
            status = "DRAFT",
            partCount = 1,
            chunkCount = 2
        )

        database.projectDao().insertProject(project)

        val document = DocumentEntity(
            id = "doc-1",
            projectId = "proj-1",
            title = "Document 1",
            rawText = "Sentence 1. Sentence 2.",
            textHash = "dummyhash",
            partCount = 1,
            wordCount = 4,
            characterCount = 22,
            createdAt = currentTime
        )
        database.documentDao().insertDocument(document)

        val part = PartEntity(
            id = "part-1",
            projectId = "proj-1",
            documentId = "doc-1",
            title = "Part 1",
            sequenceIndex = 0,
            chunkCount = 2,
            wordCount = 4,
            characterCount = 22
        )
        database.partDao().insertPart(part)

        val chunk1 = ChunkEntity(
            id = "chunk-1",
            projectId = "proj-1",
            documentId = "doc-1",
            partId = "part-1",
            sequenceIndex = 0,
            sourceText = "Sentence 1.",
            sourceTextHash = "hash1",
            normalizedText = "sentence 1",
            normalizedTextHash = "normhash1",
            engineId = "kokoro-82m",
            modelId = "kokoro-v1.0",
            voiceId = "af_heart",
            status = ChunkStatus.PENDING,
            createdAt = currentTime,
            updatedAt = currentTime
        )
        val chunk2 = chunk1.copy(id = "chunk-2", sequenceIndex = 1, sourceText = "Sentence 2.")

        database.chunkDao().insertChunks(listOf(chunk1, chunk2))

        val fetchedProject = database.projectDao().getProjectById("proj-1")
        assertNotNull(fetchedProject)
        assertEquals("Test Audiobook", fetchedProject?.title)

        val chunksList = database.chunkDao().getChunksForProject("proj-1")
        assertEquals(2, chunksList.size)
        assertEquals(ChunkStatus.PENDING, chunksList[0].status)
    }
}
