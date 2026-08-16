package com.voconexus.app.core.tts.engine

import com.voconexus.app.core.tts.AudioEncoding
import com.voconexus.app.core.tts.EngineCapabilities
import com.voconexus.app.core.tts.EngineLifecycleState
import com.voconexus.app.core.tts.ModelLicenseInfo
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.SynthesisSettings
import com.voconexus.app.core.tts.SynthesizedAudio
import com.voconexus.app.core.tts.TtsEngine
import com.voconexus.app.core.tts.TtsEngineException
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.TtsVoice
import com.voconexus.app.core.tts.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class PiperEngine(
    override val id: String = "piper-onnx",
    override val displayName: String = "Piper ONNX Engine",
    override val version: String = "1.2.0"
) : TtsEngine {

    private val _lifecycleState = MutableStateFlow(EngineLifecycleState.UNLOADED)
    override val lifecycleState: StateFlow<EngineLifecycleState> = _lifecycleState.asStateFlow()

    override val capabilities: EngineCapabilities = EngineCapabilities(
        isOffline = true,
        supportsStreaming = true,
        supportsVoiceSelection = true,
        supportsMultilingual = true,
        supportsSpeedControl = true,
        supportsPitchControl = false,
        supportsCpuInference = true,
        supportsGpuAcceleration = false
    )

    private var activeModelId: String? = null
    @Volatile
    private var isCancelled: Boolean = false

    override suspend fun getModels(): List<TtsModel> {
        return listOf(
            TtsModel(
                id = "vits-piper-en_US-lessac-medium",
                engineId = id,
                name = "Piper English Lessac (Medium)",
                version = "1.0.0",
                sizeBytes = 65 * 1024 * 1024L,
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
                checksumSha256 = "",
                status = ModelStatus.NOT_INSTALLED,
                supportedLanguages = listOf("en"),
                voicesCount = 1,
                license = ModelLicenseInfo("MIT", "https://opensource.org/licenses/MIT"),
                minRamMb = 1024
            )
        )
    }

    override suspend fun getVoices(modelId: String): List<TtsVoice> {
        return listOf(
            TtsVoice(
                id = "piper_hi_female",
                modelId = modelId,
                engineId = id,
                name = "Piper Swara • Hindi / English Multilingual Female",
                language = "hi",
                locale = "hi-IN",
                gender = VoiceGender.FEMALE,
                sampleRate = 22050,
                isDefault = true
            ),
            TtsVoice(
                id = "piper_lessac",
                modelId = modelId,
                engineId = id,
                name = "Piper Lessac • US English Female",
                language = "en",
                locale = "en-US",
                gender = VoiceGender.FEMALE,
                sampleRate = 22050
            )
        )
    }

    private var offlineTts: OfflineTts? = null

    override suspend fun loadModel(modelId: String, modelPath: String) = withContext(Dispatchers.IO) {
        _lifecycleState.value = EngineLifecycleState.LOADING
        val modelDir = File(modelPath)
        check(modelDir.exists()) { "Piper model directory does not exist: $modelPath" }
        
        val onnxFile = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".onnx") }
            ?: throw TtsEngineException.ModelCorruptedException(modelId, "Missing .onnx file")
        val tokensFile = modelDir.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
            ?: throw TtsEngineException.ModelCorruptedException(modelId, "Missing tokens.txt file")
        val espeakDir = modelDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
            ?: throw TtsEngineException.ModelCorruptedException(modelId, "Missing espeak-ng-data directory")

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = onnxFile.absolutePath,
            tokens = tokensFile.absolutePath,
            dataDir = espeakDir.absolutePath,
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            ),
            maxNumSentences = 1
        )

        try {
            offlineTts = OfflineTts(assetManager = null, config = config)
            activeModelId = modelId
            _lifecycleState.value = EngineLifecycleState.READY
        } catch (e: Exception) {
            _lifecycleState.value = EngineLifecycleState.ERROR
            throw TtsEngineException.ModelCorruptedException(modelId, "Failed to load Piper model: ${e.message}")
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        _lifecycleState.value = EngineLifecycleState.UNLOADING
        offlineTts?.release()
        offlineTts = null
        activeModelId = null
        _lifecycleState.value = EngineLifecycleState.UNLOADED
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "Cannot synthesize blank text" }
        val tts = offlineTts ?: throw TtsEngineException.EngineNotReadyException("Piper model is not loaded")

        if (isCancelled) {
            isCancelled = false
            throw TtsEngineException.CancelledTtsException()
        }

        _lifecycleState.value = EngineLifecycleState.BUSY

        try {
            val baseSpeed = settings.speed.coerceIn(0.5f, 2.0f)
            val audio = tts.generate(text, sid = 0, speed = baseSpeed)
            val samples = audio.samples
            val sampleRate = audio.sampleRate

            val pcmData = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                if (isCancelled) {
                    _lifecycleState.value = EngineLifecycleState.READY
                    isCancelled = false
                    throw TtsEngineException.CancelledTtsException()
                }
                var pcm = (samples[i] * 32767.0f).toInt()
                pcm = max(-32768, min(32767, pcm))
                pcmData[i * 2] = (pcm and 0xFF).toByte()
                pcmData[i * 2 + 1] = ((pcm shr 8) and 0xFF).toByte()
            }

            val durationMs = (samples.size * 1000L) / sampleRate
            _lifecycleState.value = EngineLifecycleState.READY

            SynthesizedAudio(
                sampleRate = sampleRate,
                channels = 1,
                encoding = AudioEncoding.PCM_16BIT,
                durationMs = durationMs,
                pcmData = pcmData
            )
        } catch (e: Exception) {
            _lifecycleState.value = EngineLifecycleState.READY
            throw e
        }
    }

    override suspend fun cancelSynthesis() {
        isCancelled = true
    }

    override suspend fun release() {
        unloadModel()
    }
}
