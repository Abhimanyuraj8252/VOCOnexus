package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProjectRepositoryTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var storageManager: AudioStorageManager
    private lateinit var repository: ProjectRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        storageManager = AudioStorageManager(context)
        repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testProjectCreateRenameAndDelete() = runBlocking {
        val scriptText = "Paragraph 1 sentence 1. Paragraph 1 sentence 2.\n\nParagraph 2 sentence 1."
        val projectId = repository.createProject(
            title = "  My Long Audiobook  ",
            description = "Test description",
            rawScriptText = scriptText
        )

        val project = repository.getProjectById(projectId).first()
        assertNotNull(project)
        assertEquals("My Long Audiobook", project?.title)
        assertTrue(project?.partCount!! >= 1)
        assertEquals(2, project?.chunkCount)

        // Test rename
        repository.renameProject(projectId, "Renamed Audiobook")
        val renamedProject = repository.getProjectById(projectId).first()
        assertEquals("Renamed Audiobook", renamedProject?.title)

        // Test safe delete
        repository.deleteProjectSafely(projectId)
        val deletedProject = repository.getProjectById(projectId).first()
        assertNull(deletedProject)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlankTitleRejected() = runBlocking {
        repository.createProject(
            title = "   ",
            description = "",
            rawScriptText = "Sample script"
        )
        Unit
    }
}
