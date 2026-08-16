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
import kotlin.math.sin

class FakeTtsEngine(
    override val id: String = "fake-tts",
    override val displayName: String = "Fake Testing Engine",
    override val version: String = "1.0.0"
) : TtsEngine {

    private val _lifecycleState = MutableStateFlow(EngineLifecycleState.UNLOADED)
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

    private var loadedModelId: String? = null
    @Volatile
    private var isCancelled: Boolean = false

    override suspend fun getModels(): List<TtsModel> {
        return listOf(
            TtsModel(
                id = "fake-model-en",
                engineId = id,
                name = "Fake English Voice Model",
                version = "1.0.0",
                sizeBytes = 10 * 1024 * 1024L,
                downloadUrl = "https://example.com/fake-model-en.zip",
                checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                status = ModelStatus.INSTALLED,
                installedPath = "/fake/models/fake-model-en",
                supportedLanguages = listOf("en"),
                voicesCount = 2,
                license = ModelLicenseInfo("MIT", "https://opensource.org/licenses/MIT")
            )
        )
    }

    override suspend fun getVoices(modelId: String): List<TtsVoice> {
        return listOf(
            TtsVoice(
                id = "fake_voice_female",
                modelId = modelId,
                engineId = id,
                name = "Fake Female Voice",
                language = "en",
                locale = "en-US",
                gender = VoiceGender.FEMALE,
                sampleRate = 24000,
                isDefault = true
            ),
            TtsVoice(
                id = "fake_voice_male",
                modelId = modelId,
                engineId = id,
                name = "Fake Male Voice",
                language = "en",
                locale = "en-US",
                gender = VoiceGender.MALE,
                sampleRate = 24000,
                isDefault = false
            )
        )
    }

    override suspend fun loadModel(modelId: String, modelPath: String) {
        _lifecycleState.value = EngineLifecycleState.LOADING
        loadedModelId = modelId
        _lifecycleState.value = EngineLifecycleState.READY
    }

    override suspend fun unloadModel() {
        _lifecycleState.value = EngineLifecycleState.UNLOADING
        loadedModelId = null
        _lifecycleState.value = EngineLifecycleState.UNLOADED
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio {
        check(loadedModelId != null) { "No model loaded in FakeTtsEngine" }
        require(text.isNotBlank()) { "Cannot synthesize blank text" }
        if (isCancelled) {
            isCancelled = false
            throw TtsEngineException.CancelledTtsException()
        }

        _lifecycleState.value = EngineLifecycleState.BUSY

        // Generate synthetic PCM sine wave matching requested text length
        val sampleRate = settings.sampleRate
        val words = text.split("\\s+".toRegex()).size
        val durationMs = ((words * 350L) / settings.speed).toLong().coerceAtLeast(500L)
        val numSamples = ((sampleRate * durationMs) / 1000L).toInt()

        val pcmData = ByteArray(numSamples * 2)
        val frequency = if (voiceId.contains("female")) 440.0 else 220.0

        for (i in 0 until numSamples) {
            if (isCancelled) {
                _lifecycleState.value = EngineLifecycleState.READY
                isCancelled = false
                throw TtsEngineException.CancelledTtsException()
            }
            val sampleVal = (sin(2.0 * Math.PI * frequency * i / sampleRate) * 16000.0).toInt().toShort()
            pcmData[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        _lifecycleState.value = EngineLifecycleState.READY
        return SynthesizedAudio(
            sampleRate = sampleRate,
            channels = 1,
            encoding = AudioEncoding.PCM_16BIT,
            durationMs = durationMs,
            pcmData = pcmData
        )
    }

    override suspend fun cancelSynthesis() {
        isCancelled = true
    }

    override suspend fun release() {
        unloadModel()
    }
}
