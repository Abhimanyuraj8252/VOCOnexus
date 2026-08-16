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
import com.voconexus.app.core.generation.engine.GenerationCoordinator
import com.voconexus.app.core.generation.queue.GenerationQueue
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
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
class GenerationCoordinatorTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var coordinator: GenerationCoordinator
    private lateinit var storageManager: AudioStorageManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
        storageManager = AudioStorageManager(context)
        val queue = GenerationQueue(database.chunkDao())
        val registry = TtsEngineRegistry(context)
        val validator = AudioValidator()

        val modelStorageManager = com.voconexus.app.core.tts.installer.ModelStorageManager(context)
        val modelInstaller = com.voconexus.app.core.tts.installer.ModelInstaller(modelStorageManager)
        val deviceEvaluator = com.voconexus.app.core.tts.device.DeviceProfileEvaluator(context)

        coordinator = GenerationCoordinator(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            jobDao = database.generationJobDao(),
            audioAssetDao = database.audioAssetDao(),
            queue = queue,
            engineRegistry = registry,
            storageManager = storageManager,
            audioValidator = validator,
            context = context,
            ttsRepository = com.voconexus.app.core.data.repository.TtsRepositoryImpl(
                modelDao = database.ttsModelDao(),
                voiceDao = database.ttsVoiceDao(),
                storageManager = modelStorageManager,
                modelInstaller = modelInstaller,
                deviceEvaluator = deviceEvaluator,
                engineRegistry = registry
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testJobExecutionAndChunkSynthesis() {
        runBlocking {
            val now = System.currentTimeMillis()
            val project = ProjectEntity(
                id = "proj-1",
                title = "Test Project",
                description = "Desc",
                createdAt = now,
                updatedAt = now,
                status = "DRAFT"
            )
            val document = DocumentEntity(
                id = "doc-1",
                projectId = "proj-1",
                title = "Doc 1",
                rawText = "Hello world text.",
                textHash = "hash",
                partCount = 1,
                wordCount = 3,
                characterCount = 17,
                createdAt = now,
                planStatus = "VALID",
                planVersion = "1.0",
                chunkCount = 1
            )
            val part = PartEntity(
                id = "part-1",
                projectId = "proj-1",
                documentId = "doc-1",
                title = "Part 1",
                sequenceIndex = 0,
                chunkCount = 1,
                wordCount = 3,
                characterCount = 17
            )
            val chunk = ChunkEntity(
                id = "chunk-1",
                projectId = "proj-1",
                documentId = "doc-1",
                partId = "part-1",
                sequenceIndex = 0,
                sourceText = "Hello world text.",
                sourceTextHash = "hash1",
                normalizedText = "Hello world text.",
                normalizedTextHash = "norm1",
                engineId = "fake-tts",
                modelId = "fake-model-en",
                voiceId = "fake_voice_female",
                status = ChunkStatus.QUEUED,
                createdAt = now,
                updatedAt = now
            )
            val job = GenerationJobEntity(
                id = "job-1",
                projectId = "proj-1",
                documentId = "doc-1",
                engineId = "fake-tts",
                modelId = "fake-model-en",
                voiceId = "fake_voice_female",
                status = GenerationJobStatus.QUEUED,
                totalChunks = 1,
                startedAt = now
            )

            database.projectDao().insertProject(project)
            database.documentDao().insertDocument(document)
            database.partDao().insertPart(part)
            database.chunkDao().insertChunk(chunk)
            database.generationJobDao().insertJob(job)

            val success = coordinator.executeJob("job-1")
            assertTrue(success)

            val updatedJob = database.generationJobDao().getJobById("job-1")
            assertNotNull(updatedJob)
            assertEquals(GenerationJobStatus.COMPLETED, updatedJob?.status)

            val updatedChunk = database.chunkDao().getChunkById("chunk-1")
            assertNotNull(updatedChunk)
            assertEquals(ChunkStatus.COMPLETED, updatedChunk?.status)
            assertTrue(updatedChunk!!.durationMs > 0)
        }
    }
}
