package com.voconexus.app

import com.voconexus.app.core.data.dao.ProjectStats
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.db.TtsModelEntity
import com.voconexus.app.core.data.db.TtsVoiceEntity
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.data.repository.VoiceRepository
import com.voconexus.app.core.planner.engine.ScriptAnalysisPlan
import com.voconexus.app.core.planner.model.ChunkingConfig
import com.voconexus.app.ui.screens.multilingual.LanguageVoiceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageVoiceViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testChunk = ChunkEntity(
        id = "c1",
        projectId = "p1",
        documentId = "d1",
        partId = "part1",
        sequenceIndex = 0,
        sourceText = "नमस्ते, Hello world.",
        sourceTextHash = "hash1",
        normalizedText = "नमस्ते. Hello world.",
        normalizedTextHash = "hash2",
        engineId = "kokoro-82m",
        modelId = "kokoro-82m-v1.0",
        voiceId = "af_heart",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val fakeProjectRepo = object : ProjectRepository {
        override fun getAllProjects(): Flow<List<ProjectEntity>> = flowOf(emptyList())
        override fun getProjectById(id: String): Flow<ProjectEntity?> = flowOf(null)
        override fun getPartsForProject(projectId: String): Flow<List<PartEntity>> = flowOf(emptyList())
        override fun getChunksForProject(projectId: String): Flow<List<ChunkEntity>> = flowOf(listOf(testChunk))
        override fun getChunksForPartFlow(partId: String): Flow<List<ChunkEntity>> = flowOf(emptyList())
        override suspend fun getChunksForProjectDirect(projectId: String): List<ChunkEntity> = listOf(testChunk)
        override suspend fun getChunksPaged(projectId: String, limit: Int, offset: Int): List<ChunkEntity> = listOf(testChunk)
        override suspend fun getProjectStats(projectId: String): ProjectStats = ProjectStats(1, 1, 0, 0, 20L, 20L, 100L)
        override suspend fun createProject(title: String, description: String, rawScriptText: String, engineId: String, modelId: String, voiceId: String): String = "p1"
        override suspend fun renameProject(projectId: String, newTitle: String) {}
        override suspend fun updateProjectVoice(projectId: String, voiceId: String, engineId: String) {}
        override suspend fun updateProjectSpeechSettings(id: String, speed: Float, pitch: Float, targetDurationMs: Long, durationMode: String) {}
        override suspend fun updateDocumentScript(projectId: String, newScriptText: String) {}
        override suspend fun analyzeScript(projectId: String, config: ChunkingConfig): ScriptAnalysisPlan = throw NotImplementedError()
        override suspend fun commitScriptPlan(projectId: String, plan: ScriptAnalysisPlan) {}
        override suspend fun invalidatePlan(projectId: String) {}
        override suspend fun deleteProjectSafely(projectId: String) {}
    }

    private val fakeVoiceRepo = object : VoiceRepository {
        override fun getAllVoices(): Flow<List<TtsVoiceEntity>> = flowOf(emptyList())
        override fun getAllModels(): Flow<List<TtsModelEntity>> = flowOf(emptyList())
        override suspend fun seedDefaultCatalog() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModelStateAnalysis() = runTest(testDispatcher) {
        val viewModel = LanguageVoiceViewModel("p1", fakeProjectRepo, fakeVoiceRepo, ioDispatcher = testDispatcher)

        val state = viewModel.uiState.value
        assertEquals("p1", state.projectId)
        assertNotNull(state.languageDistributions)
        assertEquals(4, state.availableVoices.size)
    }
}
