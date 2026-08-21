package com.voconexus.app.di

import android.content.Context
import com.voconexus.app.core.audio.VocoNexusAudioPlayer
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.data.repository.GenerationRepository
import com.voconexus.app.core.data.repository.GenerationRepositoryImpl
import com.voconexus.app.core.data.repository.PreferencesRepository
import com.voconexus.app.core.data.repository.PreferencesRepositoryImpl
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.data.repository.ProjectRepositoryImpl
import com.voconexus.app.core.data.repository.TtsRepository
import com.voconexus.app.core.data.repository.TtsRepositoryImpl
import com.voconexus.app.core.data.repository.VoiceRepository
import com.voconexus.app.core.data.repository.VoiceRepositoryImpl
import com.voconexus.app.core.domain.DurationEstimator
import com.voconexus.app.core.domain.FileIntegrityValidator
import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.planner.chunking.ChunkPlanner
import com.voconexus.app.core.planner.engine.ScriptPlannerEngine
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.model.TokenEstimator
import com.voconexus.app.core.planner.part.PartBuilder
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import com.voconexus.app.core.planner.segmentation.SentenceSegmenter
import com.voconexus.app.core.planner.validation.PlanValidator
import com.voconexus.app.core.preprocessing.TextPreprocessingEngine
import com.voconexus.app.core.storage.AudioStorageManager
import com.voconexus.app.core.storage.FileImportManager
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import com.voconexus.app.core.tts.installer.ModelInstaller
import com.voconexus.app.core.tts.installer.ModelStorageManager
import com.voconexus.app.core.tts.preview.AudioPreviewPlayer
import com.voconexus.app.core.tts.preview.VoicePreviewManager

interface AppContainer {
    val database: VocoNexusDatabase
    val projectRepository: ProjectRepository
    val generationRepository: GenerationRepository
    val voiceRepository: VoiceRepository
    val preferencesRepository: PreferencesRepository
    val userPreferencesManager: com.voconexus.app.core.preferences.UserPreferencesManager
    val storageManager: AudioStorageManager
    val fileIntegrityValidator: FileIntegrityValidator
    val fileImportManager: FileImportManager
    val textPreprocessingEngine: TextPreprocessingEngine
    val durationEstimator: DurationEstimator
    val sentenceSegmenter: SentenceSegmenter
    val tokenEstimator: TokenEstimator
    val chunkPlanner: ChunkPlanner
    val partBuilder: PartBuilder
    val planValidator: PlanValidator
    val scriptPlannerEngine: ScriptPlannerEngine
    val audioPlayer: VocoNexusAudioPlayer
    val audioValidator: com.voconexus.app.core.generation.audio.AudioValidator
    val generationQueue: com.voconexus.app.core.generation.queue.GenerationQueue
    val generationCoordinator: com.voconexus.app.core.generation.engine.GenerationCoordinator
    val generationRecoveryManager: com.voconexus.app.core.generation.recovery.GenerationRecoveryManager
    val ttsEngineRegistry: TtsEngineRegistry
    val modelStorageManager: ModelStorageManager
    val modelInstaller: ModelInstaller
    val deviceEvaluator: DeviceProfileEvaluator
    val audioPreviewPlayer: AudioPreviewPlayer
    val voicePreviewManager: VoicePreviewManager
    val ttsRepository: TtsRepository
    val playbackController: com.voconexus.app.core.playback.PlaybackController
    val audioCombiner: com.voconexus.app.core.export.AudioCombiner
    val audioExporter: com.voconexus.app.core.export.AudioExporter
    val audioRepository: com.voconexus.app.core.data.repository.AudioRepository
    val durationHistoryStore: com.voconexus.app.core.domain.duration.DurationHistoryStore
    val targetDurationPlanner: com.voconexus.app.core.domain.duration.TargetDurationPlanner
    val wsolaAudioProcessor: com.voconexus.app.core.dsp.WsolaAudioProcessor
    val speechPreviewManager: com.voconexus.app.core.tts.preview.SpeechPreviewManager
    val apiVaultManager: com.voconexus.app.core.security.ApiVaultManager
    val dynamicCatalogFetcher: com.voconexus.app.core.network.DynamicCatalogFetcher
}

class AppContainerImpl(private val context: Context) : AppContainer {
    override val apiVaultManager: com.voconexus.app.core.security.ApiVaultManager by lazy {
        com.voconexus.app.core.security.ApiVaultManager(context)
    }

    override val dynamicCatalogFetcher: com.voconexus.app.core.network.DynamicCatalogFetcher by lazy {
        com.voconexus.app.core.network.DynamicCatalogFetcher(
            apiVaultManager = apiVaultManager,
            modelDao = database.ttsModelDao(),
            voiceDao = database.ttsVoiceDao()
        )
    }
    override val database: VocoNexusDatabase by lazy {
        VocoNexusDatabase.getInstance(context)
    }

    override val playbackController: com.voconexus.app.core.playback.PlaybackController by lazy {
        com.voconexus.app.core.playback.PlaybackController(context)
    }

    override val audioCombiner: com.voconexus.app.core.export.AudioCombiner by lazy {
        com.voconexus.app.core.export.AudioCombiner()
    }

    override val audioExporter: com.voconexus.app.core.export.AudioExporter by lazy {
        com.voconexus.app.core.export.AudioExporter(
            context = context,
            storageManager = storageManager,
            audioCombiner = audioCombiner,
            audioValidator = audioValidator
        )
    }

    override val audioRepository: com.voconexus.app.core.data.repository.AudioRepository by lazy {
        com.voconexus.app.core.data.repository.AudioRepositoryImpl(
            audioAssetDao = database.audioAssetDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            audioValidator = audioValidator
        )
    }

    override val durationHistoryStore: com.voconexus.app.core.domain.duration.DurationHistoryStore by lazy {
        com.voconexus.app.core.domain.duration.DurationHistoryStore(context)
    }

    override val targetDurationPlanner: com.voconexus.app.core.domain.duration.TargetDurationPlanner by lazy {
        com.voconexus.app.core.domain.duration.TargetDurationPlanner()
    }

    override val wsolaAudioProcessor: com.voconexus.app.core.dsp.WsolaAudioProcessor by lazy {
        com.voconexus.app.core.dsp.WsolaAudioProcessor(audioCombiner, audioValidator)
    }

    override val speechPreviewManager: com.voconexus.app.core.tts.preview.SpeechPreviewManager by lazy {
        com.voconexus.app.core.tts.preview.SpeechPreviewManager(
            context = context,
            audioPreviewPlayer = audioPreviewPlayer,
            engineRegistry = ttsEngineRegistry,
            dspProcessor = wsolaAudioProcessor,
            ttsRepository = ttsRepository
        )
    }

    override val storageManager: AudioStorageManager by lazy {
        AudioStorageManager(context)
    }

    override val fileImportManager: FileImportManager by lazy {
        FileImportManager(context)
    }

    override val textPreprocessingEngine: TextPreprocessingEngine by lazy {
        TextPreprocessingEngine()
    }

    override val durationEstimator: DurationEstimator by lazy {
        StandardDurationEstimator()
    }

    override val sentenceSegmenter: SentenceSegmenter by lazy {
        RuleBasedSentenceSegmenter()
    }

    override val tokenEstimator: TokenEstimator by lazy {
        HeuristicTokenEstimator()
    }

    override val chunkPlanner: ChunkPlanner by lazy {
        ChunkPlanner(tokenEstimator)
    }

    override val partBuilder: PartBuilder by lazy {
        PartBuilder()
    }

    override val planValidator: PlanValidator by lazy {
        PlanValidator()
    }

    override val scriptPlannerEngine: ScriptPlannerEngine by lazy {
        ScriptPlannerEngine(
            sentenceSegmenter = sentenceSegmenter,
            tokenEstimator = tokenEstimator,
            durationEstimator = durationEstimator,
            chunkPlanner = chunkPlanner,
            partBuilder = partBuilder,
            planValidator = planValidator
        )
    }

    override val projectRepository: ProjectRepository by lazy {
        ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            partDao = database.partDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            scriptPlannerEngine = scriptPlannerEngine
        )
    }

    override val voiceRepository: VoiceRepository by lazy {
        VoiceRepositoryImpl(
            modelDao = database.ttsModelDao(),
            voiceDao = database.ttsVoiceDao()
        )
    }

    override val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepositoryImpl(context)
    }
    
    override val userPreferencesManager: com.voconexus.app.core.preferences.UserPreferencesManager by lazy {
        com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context)
    }

    override val fileIntegrityValidator: FileIntegrityValidator by lazy {
        FileIntegrityValidator(database.chunkDao())
    }

    override val audioPlayer: VocoNexusAudioPlayer by lazy {
        VocoNexusAudioPlayer(context)
    }

    override val ttsEngineRegistry: TtsEngineRegistry by lazy {
        TtsEngineRegistry(context)
    }

    override val audioValidator: com.voconexus.app.core.generation.audio.AudioValidator by lazy {
        com.voconexus.app.core.generation.audio.AudioValidator()
    }

    override val generationQueue: com.voconexus.app.core.generation.queue.GenerationQueue by lazy {
        com.voconexus.app.core.generation.queue.GenerationQueue(database.chunkDao())
    }

    override val generationCoordinator: com.voconexus.app.core.generation.engine.GenerationCoordinator by lazy {
        val userPrefsManager = com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context)
        com.voconexus.app.core.generation.engine.GenerationCoordinator(
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            jobDao = database.generationJobDao(),
            audioAssetDao = database.audioAssetDao(),
            queue = generationQueue,
            engineRegistry = ttsEngineRegistry,
            storageManager = storageManager,
            ttsRepository = ttsRepository,
            audioValidator = audioValidator,
            durationHistoryStore = durationHistoryStore,
            userPreferencesManager = userPrefsManager,
            context = context
        )
    }

    override val generationRecoveryManager: com.voconexus.app.core.generation.recovery.GenerationRecoveryManager by lazy {
        com.voconexus.app.core.generation.recovery.GenerationRecoveryManager(
            jobDao = database.generationJobDao(),
            chunkDao = database.chunkDao(),
            storageManager = storageManager,
            audioValidator = audioValidator
        )
    }

    override val generationRepository: GenerationRepository by lazy {
        GenerationRepositoryImpl(
            context = context,
            projectDao = database.projectDao(),
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            jobDao = database.generationJobDao(),
            queue = generationQueue,
            coordinator = generationCoordinator,
            recoveryManager = generationRecoveryManager
        )
    }

    override val modelStorageManager: ModelStorageManager by lazy {
        ModelStorageManager(context)
    }

    override val modelInstaller: ModelInstaller by lazy {
        ModelInstaller(modelStorageManager)
    }

    override val deviceEvaluator: DeviceProfileEvaluator by lazy {
        DeviceProfileEvaluator(context)
    }

    override val audioPreviewPlayer: AudioPreviewPlayer by lazy {
        AudioPreviewPlayer(context)
    }

    override val voicePreviewManager: VoicePreviewManager by lazy {
        VoicePreviewManager(context, audioPreviewPlayer)
    }

    override val ttsRepository: TtsRepository by lazy {
        TtsRepositoryImpl(
            modelDao = database.ttsModelDao(),
            voiceDao = database.ttsVoiceDao(),
            storageManager = modelStorageManager,
            modelInstaller = modelInstaller,
            deviceEvaluator = deviceEvaluator,
            engineRegistry = ttsEngineRegistry
        )
    }
}
