package com.voconexus.app.core.data.repository

import com.voconexus.app.core.data.dao.TtsModelDao
import com.voconexus.app.core.data.dao.TtsVoiceDao
import com.voconexus.app.core.data.db.ModelStatus as DbModelStatus
import com.voconexus.app.core.data.db.TtsModelEntity
import com.voconexus.app.core.data.db.TtsVoiceEntity
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.TtsEngine
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.TtsVoice
import com.voconexus.app.core.tts.VoiceGender
import com.voconexus.app.core.tts.device.BenchmarkResult
import com.voconexus.app.core.tts.device.DeviceProfile
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.device.TtsBenchmarkManager
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import com.voconexus.app.core.tts.installer.ModelInstaller
import com.voconexus.app.core.tts.installer.ModelStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface TtsRepository {
    fun getAllModels(): Flow<List<TtsModel>>
    fun getAllVoices(): Flow<List<TtsVoice>>
    fun getVoicesForModel(modelId: String): Flow<List<TtsVoice>>
    suspend fun getModelById(modelId: String): TtsModel?
    suspend fun getVoiceById(voiceId: String): TtsVoice?
    suspend fun installModel(modelId: String, onProgress: (Float) -> Unit = {})
    suspend fun deleteModel(modelId: String)
    suspend fun runBenchmark(modelId: String, voiceId: String): BenchmarkResult
    fun getDeviceProfile(): DeviceProfile
    fun getEngine(engineId: String): TtsEngine
}

class TtsRepositoryImpl(
    private val modelDao: TtsModelDao,
    private val voiceDao: TtsVoiceDao,
    private val storageManager: ModelStorageManager,
    private val modelInstaller: ModelInstaller,
    private val deviceEvaluator: DeviceProfileEvaluator,
    private val engineRegistry: TtsEngineRegistry,
    private val benchmarkManager: TtsBenchmarkManager = TtsBenchmarkManager()
) : TtsRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        repositoryScope.launch {
            ensureInitialModelsAndVoicesSeeded()
        }
    }

    override fun getAllModels(): Flow<List<TtsModel>> {
        return modelDao.getAllModelsFlow().onStart {
            ensureInitialModelsAndVoicesSeeded()
        }.map { entities ->
            val legacyIds = setOf("fake-model-en", "fake-tts", "edge-tts-online", "google-tts-online")
            entities
                .filter { it.id !in legacyIds }
                .distinctBy { it.id }
                .map { entity ->
                    val domainModel = entity.toDomainModel()
                    if (domainModel.id == "edge-tts" || domainModel.id == "google-cloud-tts" || domainModel.id == "android-tts" || domainModel.sizeBytes == 0L) {
                        domainModel.copy(status = ModelStatus.INSTALLED)
                    } else {
                        domainModel
                    }
                }
        }
    }

    override fun getAllVoices(): Flow<List<TtsVoice>> {
        return voiceDao.getAllVoicesFlow().onStart {
            ensureInitialModelsAndVoicesSeeded()
        }.map { entities ->
            entities.map { it.toDomainVoice() }
        }
    }

    override fun getVoicesForModel(modelId: String): Flow<List<TtsVoice>> {
        return voiceDao.getVoicesForModelFlow(modelId).map { entities ->
            entities.map { it.toDomainVoice() }
        }
    }

    override suspend fun getModelById(modelId: String): TtsModel? {
        return modelDao.getModelById(modelId)?.toDomainModel()
    }

    override suspend fun getVoiceById(voiceId: String): TtsVoice? {
        return voiceDao.getVoiceById(voiceId)?.toDomainVoice()
    }

    override suspend fun installModel(modelId: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val model = getModelById(modelId) ?: throw IllegalArgumentException("Model not found: $modelId")
        modelDao.updateModelStatus(modelId, DbModelStatus.DOWNLOADING, 0f)

        try {
            val installedDir = modelInstaller.downloadAndInstallModel(model) { progress ->
                repositoryScope.launch {
                    val dbStatus = if (progress >= 0.95f) DbModelStatus.VERIFYING else DbModelStatus.DOWNLOADING
                    modelDao.updateModelStatus(modelId, dbStatus, progress)
                }
                onProgress(progress)
            }

            modelDao.markModelInstalled(modelId, installedDir.absolutePath)
            onProgress(1.0f)
        } catch (e: Exception) {
            modelDao.updateModelStatus(modelId, DbModelStatus.FAILED, 0f, e.message ?: "Download failed")
            throw e
        }
    }

    override suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        storageManager.deleteModelDirectory(modelId)
        modelDao.markModelUninstalled(modelId)
    }

    override suspend fun runBenchmark(modelId: String, voiceId: String): BenchmarkResult = withContext(Dispatchers.Default) {
        val model = getModelById(modelId) ?: throw IllegalArgumentException("Model not found: $modelId")
        val engine = engineRegistry.getRequiredEngine(model.engineId)

        if (model.installedPath != null) {
            engine.loadModel(modelId, model.installedPath)
        }

        val result = benchmarkManager.runLocalBenchmark(engine, modelId, voiceId)
        engine.unloadModel()
        return@withContext result
    }

    override fun getDeviceProfile(): DeviceProfile {
        return deviceEvaluator.evaluateDeviceProfile()
    }

    override fun getEngine(engineId: String): TtsEngine {
        return engineRegistry.getRequiredEngine(engineId)
    }

    private suspend fun ensureInitialModelsAndVoicesSeeded() = withContext(Dispatchers.IO) {
        val initialModels = mutableListOf<TtsModelEntity>()
        val initialVoices = mutableListOf<TtsVoiceEntity>()

        for (engine in engineRegistry.getAllEngines()) {
            val models = engine.getModels()
            models.forEach { model ->
                val existing = modelDao.getModelById(model.id)
                val isInstalled = storageManager.isModelInstalled(model.id) || model.status == ModelStatus.INSTALLED
                val status = when {
                    isInstalled -> DbModelStatus.INSTALLED
                    existing != null -> existing.status
                    else -> model.status.name
                }
                val installedPath = if (isInstalled) storageManager.getInstalledModelDirectory(model.id).absolutePath else (existing?.localPath ?: "")

                initialModels.add(
                    TtsModelEntity(
                        id = model.id,
                        name = model.name,
                        engineId = model.engineId,
                        version = model.version,
                        isDownloaded = isInstalled,
                        localPath = installedPath,
                        sizeBytes = model.sizeBytes,
                        downloadUrl = model.downloadUrl,
                        checksumSha256 = model.checksumSha256,
                        status = status,
                        languagesJson = "[\"en\", \"hi\"]",
                        voicesCount = model.voicesCount,
                        licenseName = model.license.licenseName,
                        licenseUrl = model.license.licenseUrl,
                        minRamMb = model.minRamMb
                    )
                )

                val voices = engine.getVoices(model.id)
                voices.forEach { voice ->
                    initialVoices.add(
                        TtsVoiceEntity(
                            id = voice.id,
                            name = voice.name,
                            modelId = voice.modelId,
                            engineId = voice.engineId,
                            gender = voice.gender.name,
                            language = voice.language,
                            locale = voice.locale,
                            sampleRate = voice.sampleRate,
                            isDefault = voice.isDefault
                        )
                    )
                }
            }
        }

        modelDao.insertModels(initialModels)
        voiceDao.insertVoices(initialVoices)
    }

    private fun TtsModelEntity.toDomainModel(): TtsModel {
        val statusEnum = try {
            ModelStatus.valueOf(status)
        } catch (_: Exception) {
            ModelStatus.NOT_INSTALLED
        }

        return TtsModel(
            id = id,
            engineId = engineId,
            name = name,
            version = version,
            sizeBytes = sizeBytes,
            downloadUrl = downloadUrl,
            checksumSha256 = checksumSha256,
            status = statusEnum,
            installedPath = localPath,
            supportedLanguages = listOf("en", "hi"),
            voicesCount = voicesCount,
            minRamMb = minRamMb,
            downloadProgress = downloadProgress,
            errorMessage = errorMessage.ifEmpty { null }
        )
    }

    private fun TtsVoiceEntity.toDomainVoice(): TtsVoice {
        val genderEnum = try {
            VoiceGender.valueOf(gender)
        } catch (_: Exception) {
            VoiceGender.UNKNOWN
        }

        return TtsVoice(
            id = id,
            modelId = modelId,
            engineId = engineId,
            name = name,
            language = language,
            locale = locale,
            gender = genderEnum,
            sampleRate = sampleRate,
            isDefault = isDefault,
            previewSamplePath = previewSamplePath.ifEmpty { null }
        )
    }
}
