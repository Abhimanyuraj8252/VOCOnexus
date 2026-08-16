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
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig

/**
 * Kokoro 82M Multilingual Neural TTS Engine backed by Sherpa-ONNX.
 *
 * The model is loaded ONCE and kept alive across multiple synthesize() calls.
 * unloadModel() only truly releases when explicitly called (e.g. app shutdown),
 * NOT after every preview, to avoid the 5-10 second reload cost.
 *
 * Kokoro voice speaker IDs (sid) from voices.bin:
 *   0  = af_heart   (US Female, warm)
 *   1  = af_bella   (US Female, bright)
 *   2  = af_nicole  (US Female, neutral)
 *   3  = af_sky     (US Female, airy)
 *   4  = am_adam    (US Male, deep)
 *   5  = am_michael (US Male, clear)
 *   6  = bf_emma    (UK Female)
 *   7  = bm_george  (UK Male)
 *   8  = hf_alpha   (Hindi Female)
 *   9  = hf_beta    (Hindi Female 2)
 *  10  = hm_omega   (Hindi Male)
 *  11  = hf_psi     (Hindi Female 3)
 */
class KokoroEngine(
    override val id: String = "kokoro-82m",
    override val displayName: String = "Kokoro 82M (Sherpa-ONNX)",
    override val version: String = "1.0.0"
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

    // Correct Kokoro speaker ID mapping (matches voices.bin order)
    private val VOICE_SID_MAP = mapOf(
        "af_heart"   to 0,
        "af_bella"   to 1,
        "af_nicole"  to 2,
        "af_sky"     to 3,
        "am_adam"    to 4,
        "am_michael" to 5,
        "bf_emma"    to 6,
        "bm_george"  to 7,
        "hf_alpha"   to 8,
        "hf_beta"    to 9,
        "hm_omega"   to 10,
        "hf_psi"     to 11,
        "ff_siwis"   to 12,
        "ef_dora"    to 13,
        "if_sara"    to 14,
        "jf_alpha"   to 15,
        "zf_xiaoxiao" to 16,
        "zm_yunjian" to 17
    )

    private var activeModelId: String = "kokoro-v1.0"
    private var activeModelPath: String = ""
    private var offlineTts: OfflineTts? = null

    @Volatile
    private var isCancelled: Boolean = false

    override suspend fun getModels(): List<TtsModel> {
        return listOf(
            TtsModel(
                id = "kokoro-v1.0",
                engineId = id,
                name = "Kokoro 82M v1.0 Multilingual",
                version = "1.0.0",
                sizeBytes = 350 * 1024 * 1024L,
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
                checksumSha256 = "c08e50b86a8775f0a73b27b68636b13e9a7e089d701e851a70cb607611e9f456",
                status = ModelStatus.INSTALLED,
                supportedLanguages = listOf("en", "hi", "fr", "es", "it", "ja", "zh"),
                voicesCount = 18,
                license = ModelLicenseInfo(
                    licenseName = "Apache-2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    attributionRequired = true,
                    commercialUseAllowed = true
                ),
                minRamMb = 3072
            )
        )
    }

    override suspend fun getVoices(modelId: String): List<TtsVoice> {
        return listOf(
            TtsVoice("hf_alpha",   modelId, id, "Kokoro Alpha (अल्फा) • Hindi / English Multilingual Female", "hi", "hi-IN", VoiceGender.FEMALE, 24000, isDefault = true),
            TtsVoice("hm_omega",   modelId, id, "Kokoro Omega (ओमेगा) • Hindi / English Multilingual Male",   "hi", "hi-IN", VoiceGender.MALE,   24000),
            TtsVoice("hf_beta",    modelId, id, "Kokoro Beta (बीटा) • Hindi / English Multilingual Female",    "hi", "hi-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("hf_psi",     modelId, id, "Kokoro Psi (साई) • Hindi / English Multilingual Female",     "hi", "hi-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("af_heart",   modelId, id, "AF Heart • US English / Multilingual Female",   "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("af_bella",   modelId, id, "AF Bella • US English / Multilingual Female",   "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("af_nicole",  modelId, id, "AF Nicole • US English / Multilingual Female",  "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("af_sky",     modelId, id, "AF Sky • US English / Multilingual Female",     "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("am_adam",    modelId, id, "AM Adam • US English / Multilingual Male",    "en", "en-US", VoiceGender.MALE,   24000),
            TtsVoice("am_michael", modelId, id, "AM Michael • US English / Multilingual Male", "en", "en-US", VoiceGender.MALE,   24000),
            TtsVoice("bf_emma",    modelId, id, "BF Emma • UK English / Multilingual Female",    "en", "en-GB", VoiceGender.FEMALE, 24000),
            TtsVoice("bm_george",  modelId, id, "BM George • UK English / Multilingual Male",  "en", "en-GB", VoiceGender.MALE,   24000),
            TtsVoice("ff_siwis",   modelId, id, "FF Siwis • French / Multilingual Female",     "fr", "fr-FR", VoiceGender.FEMALE, 24000),
            TtsVoice("ef_dora",    modelId, id, "EF Dora • Spanish / Multilingual Female",      "es", "es-ES", VoiceGender.FEMALE, 24000),
            TtsVoice("em_alex",    modelId, id, "EM Alex • Spanish / Multilingual Male",      "es", "es-ES", VoiceGender.MALE,   24000),
            TtsVoice("if_sara",    modelId, id, "IF Sara • Italian / Multilingual Female",      "it", "it-IT", VoiceGender.FEMALE, 24000),
            TtsVoice("jf_alpha",   modelId, id, "JF Alpha • Japanese Female",   "ja", "ja-JP", VoiceGender.FEMALE, 24000),
            TtsVoice("zf_xiaoxiao",modelId, id, "ZF Xiaoxiao • Mandarin Female", "zh", "zh-CN", VoiceGender.FEMALE, 24000),
            TtsVoice("zm_yunjian", modelId, id, "ZM Yunjian (Mandarin Male)", "zh", "zh-CN", VoiceGender.MALE, 24000)
        )
    }

    /**
     * Load (or reuse) the Sherpa-ONNX model.
     * If the same model path is already loaded, this is a NO-OP for fast repeated calls.
     */
    override suspend fun loadModel(modelId: String, modelPath: String) = withContext(Dispatchers.IO) {
        // Already loaded with same path - skip expensive reload
        if (offlineTts != null && activeModelPath == modelPath && modelPath.isNotBlank()) {
            _lifecycleState.value = EngineLifecycleState.READY
            return@withContext
        }

        _lifecycleState.value = EngineLifecycleState.LOADING

        // Release previous instance if path changed
        try { offlineTts?.release() } catch (_: Exception) {}
        offlineTts = null

        activeModelId = modelId
        activeModelPath = modelPath

        if (modelPath.isBlank()) {
            _lifecycleState.value = EngineLifecycleState.ERROR
            throw TtsEngineException.ModelCorruptedException(modelId, "Model path is empty")
        }

        // Resolve the actual directory containing the .onnx file
        var targetDir = File(modelPath)
        if (targetDir.listFiles()?.none { it.name.endsWith(".onnx") } == true) {
            targetDir.listFiles { f -> f.isDirectory }
                ?.firstOrNull { dir -> dir.listFiles()?.any { it.name.endsWith(".onnx") } == true }
                ?.let { targetDir = it }
        }

        val modelFile = targetDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
        val voicesFile = File(targetDir, "voices.bin")
        val tokensFile = File(targetDir, "tokens.txt")

        if (modelFile == null || !modelFile.exists())  throw TtsEngineException.ModelCorruptedException(modelId, "model.onnx not found in $targetDir")
        if (!voicesFile.exists()) throw TtsEngineException.ModelCorruptedException(modelId, "voices.bin not found in $targetDir")
        if (!tokensFile.exists()) throw TtsEngineException.ModelCorruptedException(modelId, "tokens.txt not found in $targetDir")

        val lexiconFile = File(targetDir, "lexicon.txt").takeIf { it.exists() }?.absolutePath ?: ""
        val dictDir     = File(targetDir, "dict").takeIf { it.exists() }?.absolutePath ?: ""

        val dataDirFile = File(targetDir, "espeak-ng-data").also { if (!it.exists()) it.mkdirs() }
        val phontabFile = File(dataDirFile, "phontab")
        if (!phontabFile.exists()) {
            try { phontabFile.writeText("PHONTAB") } catch (_: Exception) {}
        }

        val kokoroConfig = OfflineTtsKokoroModelConfig(
            model    = modelFile.absolutePath,
            voices   = voicesFile.absolutePath,
            tokens   = tokensFile.absolutePath,
            dataDir  = dataDirFile.absolutePath,
            lexicon  = lexiconFile,
            lang     = "",
            dictDir  = dictDir,
            lengthScale = 1.0f
        )

        val ttsConfig = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro     = kokoroConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            ),
            ruleFsts       = "",
            ruleFars       = "",
            maxNumSentences = 1,
            silenceScale   = 0.2f
        )

        try {
            offlineTts = OfflineTts(assetManager = null, config = ttsConfig)
            _lifecycleState.value = EngineLifecycleState.READY
        } catch (e: Throwable) {
            _lifecycleState.value = EngineLifecycleState.ERROR
            offlineTts = null
            throw TtsEngineException.ModelCorruptedException(modelId, "Failed to initialize native model files: ${e.message}")
        }
    }

    /**
     * Unload model and free native memory.
     * Call this only when truly done (app shutdown / model switch), NOT after every preview.
     */
    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        _lifecycleState.value = EngineLifecycleState.UNLOADING
        try { offlineTts?.release() } catch (_: Exception) {}
        offlineTts = null
        activeModelPath = ""
        _lifecycleState.value = EngineLifecycleState.UNLOADED
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "Cannot synthesize blank text" }
        val tts = offlineTts ?: throw TtsEngineException.EngineNotReadyException(
            "Kokoro model is not loaded. Call loadModel() first."
        )

        if (isCancelled) { isCancelled = false; throw TtsEngineException.CancelledTtsException() }

        _lifecycleState.value = EngineLifecycleState.BUSY

        try {
            val speed = settings.speed.coerceIn(0.5f, 2.0f)

            // Exact voice ID match first, then fuzzy keyword fallback
            val sid = VOICE_SID_MAP[voiceId.lowercase()]
                ?: VOICE_SID_MAP.entries.firstOrNull { voiceId.contains(it.key, ignoreCase = true) }?.value
                ?: 0

            val audio = tts.generate(text, sid = sid, speed = speed)
            val samples    = audio.samples
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
                pcmData[i * 2]     = (pcm and 0xFF).toByte()
                pcmData[i * 2 + 1] = ((pcm shr 8) and 0xFF).toByte()
            }

            _lifecycleState.value = EngineLifecycleState.READY

            SynthesizedAudio(
                sampleRate = sampleRate,
                channels   = 1,
                encoding   = AudioEncoding.PCM_16BIT,
                durationMs = (samples.size * 1000L) / sampleRate,
                pcmData    = pcmData
            )
        } catch (e: Exception) {
            _lifecycleState.value = EngineLifecycleState.READY
            throw e
        }
    }

    override suspend fun cancelSynthesis() { isCancelled = true }

    override suspend fun release() { unloadModel() }
}
