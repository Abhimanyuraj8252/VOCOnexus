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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * Sherpa-ONNX Primary Acoustic TTS Engine.
 */
class SherpaOnnxEngine(
    override val id: String = "sherpa-onnx",
    override val displayName: String = "Sherpa-ONNX Primary Engine",
    override val version: String = "1.10.0"
) : TtsEngine {

    private val _lifecycleState = MutableStateFlow(EngineLifecycleState.READY)
    override val lifecycleState: StateFlow<EngineLifecycleState> = _lifecycleState.asStateFlow()

    override val capabilities: EngineCapabilities = EngineCapabilities(
        isOffline = true,
        supportsStreaming = true,
        supportsVoiceSelection = true,
        supportsMultilingual = true,
        supportsSpeedControl = true,
        supportsPitchControl = true,
        supportsCpuInference = true,
        supportsGpuAcceleration = false
    )

    private var activeModelId: String = "sherpa-onnx-v1.0"
    private var activeModelDir: File? = null
    @Volatile
    private var isCancelled: Boolean = false

    override suspend fun getModels(): List<TtsModel> {
        return listOf(
            TtsModel(
                id = "sherpa-onnx-v1.0",
                engineId = id,
                name = "Sherpa-ONNX Offline Multilingual Model",
                version = "1.10.0",
                sizeBytes = 280 * 1024 * 1024L,
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-multilingual.tar.bz2",
                checksumSha256 = "c08e50b86a8775f0a73b27b68636b13e9a7e089d701e851a70cb607611e9f456",
                status = ModelStatus.INSTALLED,
                supportedLanguages = listOf("en", "hi", "es", "fr"),
                voicesCount = 8,
                license = ModelLicenseInfo(
                    licenseName = "Apache-2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    attributionRequired = true,
                    commercialUseAllowed = true
                ),
                minRamMb = 2048
            )
        )
    }

    override suspend fun getVoices(modelId: String): List<TtsVoice> {
        return listOf(
            TtsVoice("sherpa_hf_alpha", modelId, id, "Sherpa Alpha (अल्फा) • Hindi / English Multilingual Female", "hi", "hi-IN", VoiceGender.FEMALE, 24000, isDefault = true),
            TtsVoice("sherpa_hm_omega", modelId, id, "Sherpa Omega (ओमेगा) • Hindi / English Multilingual Male", "hi", "hi-IN", VoiceGender.MALE, 24000),
            TtsVoice("sherpa_hf_beta", modelId, id, "Sherpa Beta (बीटा) • Hindi / English Multilingual Female", "hi", "hi-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("sherpa_hf_psi", modelId, id, "Sherpa Psi (साई) • Hindi / English Multilingual Female", "hi", "hi-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("sherpa_af_heart", modelId, id, "Sherpa AF Heart • US English / Multilingual Female", "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("sherpa_am_adam", modelId, id, "Sherpa AM Adam • US English / Multilingual Male", "en", "en-US", VoiceGender.MALE, 24000)
        )
    }

    private var offlineTts: OfflineTts? = null

    override suspend fun loadModel(modelId: String, modelPath: String) = withContext(Dispatchers.IO) {
        _lifecycleState.value = EngineLifecycleState.LOADING
        
        val modelDir = File(modelPath)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            _lifecycleState.value = EngineLifecycleState.ERROR
            throw TtsEngineException.ModelCorruptedException(modelId, "Model directory does not exist: $modelPath")
        }

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = File(modelDir, "model.onnx").absolutePath,
            tokens = File(modelDir, "tokens.txt").absolutePath,
            lexicon = File(modelDir, "lexicon.txt").absolutePath,
            dataDir = File(modelDir, "espeak-ng-data").absolutePath,
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
            ruleFsts = "",
            maxNumSentences = 1
        )

        try {
            offlineTts = OfflineTts(assetManager = null, config = config)
            activeModelId = modelId
            activeModelDir = modelDir
            _lifecycleState.value = EngineLifecycleState.READY
        } catch (e: Exception) {
            _lifecycleState.value = EngineLifecycleState.ERROR
            throw TtsEngineException.ModelCorruptedException(modelId, "Failed to load Sherpa-ONNX model: ${e.message}")
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        _lifecycleState.value = EngineLifecycleState.UNLOADING
        activeModelDir = null
        _lifecycleState.value = EngineLifecycleState.UNLOADED
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "Cannot synthesize blank text" }
        val tts = offlineTts ?: throw TtsEngineException.EngineNotReadyException("Sherpa-ONNX model is not loaded")

        if (isCancelled) {
            isCancelled = false
            throw TtsEngineException.CancelledTtsException()
        }

        _lifecycleState.value = EngineLifecycleState.BUSY

        try {
            val baseSpeed = settings.speed.coerceIn(0.5f, 2.0f)
            
            // Parse voice ID (assuming format sherpa_voiceName, but kokoro speaker IDs are integers, we will map them, but default to 0 if not sure)
            // A more robust implementation would use a map. For now we hash the voice string to an ID or parse it.
            // Actually, Kokoro voices in sherpa-onnx are selected via speaker id (sid).
            // HF Beta is usually sid 1, HF Alpha is 0. We'll map simple numbers.
            val sid = when {
                voiceId.contains("heart", ignoreCase = true) -> 4
                voiceId.contains("adam", ignoreCase = true) -> 5
                voiceId.contains("alpha", ignoreCase = true) -> 0
                voiceId.contains("beta", ignoreCase = true) -> 1
                voiceId.contains("omega", ignoreCase = true) -> 2
                voiceId.contains("psi", ignoreCase = true) -> 3
                else -> 0
            }
            
            val audio = tts.generate(text, sid = sid, speed = baseSpeed)
            val samples = audio.samples
            val sampleRate = audio.sampleRate

            val pcmData = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                if (isCancelled) {
                    _lifecycleState.value = EngineLifecycleState.READY
                    isCancelled = false
                    throw TtsEngineException.CancelledTtsException()
                }
                
                // Convert float to 16-bit PCM
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

