package com.voconexus.app

import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.preprocessing.TextPreprocessingEngine
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.storage.FileImportManager
import com.voconexus.app.ui.screens.scripteditor.ScriptEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScriptEditorViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var storageManager: AudioStorageManager
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var viewModel: ScriptEditorViewModel
    private lateinit var projectId: String

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = TestContextUtil.createMockContext()
        database = VocoNexusDatabase.createInMemory(context)
        storageManager = AudioStorageManager(context)
        projectRepository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager
        )

        projectId = projectRepository.createProject(
            title = "Test Script Project",
            description = "Test description",
            rawScriptText = "Initial line 1. Initial line 2."
        )

        viewModel = ScriptEditorViewModel(
            projectId = projectId,
            projectRepository = projectRepository,
            fileImportManager = FileImportManager(context),
            preprocessingEngine = TextPreprocessingEngine(),
            durationEstimator = StandardDurationEstimator()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testInitialScriptTextAndDirtyState() = runBlocking {
        val currentText = viewModel.scriptText.first()
        assertTrue(currentText.contains("Initial line 1."))
        assertFalse(viewModel.isDirty.first())
    }

    @Test
    fun testTextEditTriggersDirtyStateAndStats() = runBlocking {
        val updatedText = "New updated script text for testing."
        viewModel.onScriptTextChanged(updatedText)

        assertTrue(viewModel.isDirty.first())
        assertEquals(updatedText, viewModel.scriptText.first())
    }

    @Test
    fun testManualSaveResetsDirtyState() = runBlocking {
        val updatedText = "Updated script line 1. Updated script line 2."
        viewModel.onScriptTextChanged(updatedText)
        assertTrue(viewModel.isDirty.first())

        viewModel.saveScript()
        delay(500)

        assertFalse(viewModel.isDirty.first())
        val dbChunks = projectRepository.getChunksForProjectDirect(projectId)
        assertTrue(dbChunks.isNotEmpty())
        assertTrue(dbChunks[0].sourceText.contains("Updated script"))
    }
}
