package com.voconexus.app

import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.engine.ScriptPlannerEngine
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlannerDatabaseStressTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var storageManager: AudioStorageManager
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var scriptPlannerEngine: ScriptPlannerEngine

    @Before
    fun setUp() {
        val context = TestContextUtil.createMockContext()
        database = VocoNexusDatabase.createInMemory(context)
        storageManager = AudioStorageManager(context)

        val tokenEstimator = HeuristicTokenEstimator()
        val durationEstimator = StandardDurationEstimator()
        val segmenter = RuleBasedSentenceSegmenter()
        val chunkPlanner = ChunkPlanner(tokenEstimator)
        val partBuilder = PartBuilder()
        val validator = PlanValidator()

        scriptPlannerEngine = ScriptPlannerEngine(
            sentenceSegmenter = segmenter,
            tokenEstimator = tokenEstimator,
            durationEstimator = durationEstimator,
            chunkPlanner = chunkPlanner,
            partBuilder = partBuilder,
            planValidator = validator
        )

        projectRepository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            scriptPlannerEngine = scriptPlannerEngine
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTenThousandChunksPlanGenerationAndBatchCommit() = runBlocking {
        val textBuilder = StringBuilder()
        // Generate large synthetic document with ~10,000 sentences
        for (i in 1..10000) {
            textBuilder.append("Sentence number $i in large stress test document. ")
            if (i % 10 == 0) {
                textBuilder.append("\n\n")
            }
        }

        val largeScriptText = textBuilder.toString()

        val projectId = projectRepository.createProject(
            title = "10k Stress Test Project",
            description = "Database stress test for 10000+ chunks",
            rawScriptText = largeScriptText
        )

        val plan = projectRepository.analyzeScript(projectId)

        assertTrue("Plan should contain at least 500 chunks", plan.totalChunkCount >= 500)
        assertTrue("Plan should contain multiple parts", plan.parts.isNotEmpty())

        val startTime = System.currentTimeMillis()
        projectRepository.commitScriptPlan(projectId, plan)
        val duration = System.currentTimeMillis() - startTime

        assertTrue("Batch DB insertion duration should be efficient (< 10 seconds)", duration < 10000)

        val project = projectRepository.getProjectById(projectId).first()
        assertNotNull(project)
        assertEquals("PLANNED", project?.status)
        assertEquals(plan.parts.size, project?.partCount)
        assertEquals(plan.totalChunkCount, project?.chunkCount)

        val dbChunks = projectRepository.getChunksPaged(projectId, limit = 50, offset = 0)
        assertEquals(50, dbChunks.size)
        assertEquals(0, dbChunks[0].sequenceIndex)
    }
}
