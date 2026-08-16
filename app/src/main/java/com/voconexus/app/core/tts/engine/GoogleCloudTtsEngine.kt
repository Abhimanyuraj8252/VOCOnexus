package com.voconexus.app.core.tts.engine

import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleCloudTtsEngine(private val context: Context) : TtsEngine {

    override val id: String = "google-cloud-tts"
    override val displayName: String = "Google TTS (Free Cloud)"
    override val version: String = "1.0.0"

    private val _lifecycleState = MutableStateFlow(EngineLifecycleState.READY)
    override val lifecycleState: StateFlow<EngineLifecycleState> = _lifecycleState.asStateFlow()

    override val capabilities: EngineCapabilities = EngineCapabilities(
        isOffline = false,
        supportsStreaming = false,
        supportsVoiceSelection = true,
        supportsMultilingual = true,
        supportsSpeedControl = true,
        supportsPitchControl = true,
        supportsCpuInference = false,
        supportsGpuAcceleration = false
    )

    override suspend fun getModels(): List<TtsModel> {
        return listOf(
            TtsModel(
                id = "google-cloud-tts",
                engineId = id,
                name = "Google Cloud Neural TTS",
                version = "1.0.0",
                sizeBytes = 0L,
                downloadUrl = "",
                checksumSha256 = "",
                status = ModelStatus.INSTALLED,
                supportedLanguages = listOf("en", "hi", "fr", "es", "it", "ja", "zh", "de", "ru", "pt"),
                voicesCount = 15,
                license = ModelLicenseInfo(
                    licenseName = "Proprietary",
                    licenseUrl = "https://cloud.google.com/text-to-speech",
                    attributionRequired = false,
                    commercialUseAllowed = false
                ),
                minRamMb = 512
            )
        )
    }

    override suspend fun getVoices(modelId: String): List<TtsVoice> {
        return listOf(
            TtsVoice("g_en_in_female", modelId, id, "Google Neerja • Indian English / Hindi Multilingual Female", "en", "en-IN", VoiceGender.FEMALE, 24000, isDefault = true),
            TtsVoice("g_en_in_male", modelId, id, "Google Prabhat • Indian English / Hindi Multilingual Male", "en", "en-IN", VoiceGender.MALE, 24000),
            TtsVoice("g_hi_in_female", modelId, id, "Google Swara • Hindi / English Multilingual Female", "hi", "hi-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("g_hi_in_male", modelId, id, "Google Madhur • Hindi / English Multilingual Male", "hi", "hi-IN", VoiceGender.MALE, 24000),
            TtsVoice("g_en_us_female", modelId, id, "Google US English Female", "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("g_en_us_male", modelId, id, "Google US English Male", "en", "en-US", VoiceGender.MALE, 24000),
            TtsVoice("g_en_uk_female", modelId, id, "Google UK English Female", "en", "en-GB", VoiceGender.FEMALE, 24000),
            TtsVoice("g_en_uk_male", modelId, id, "Google UK English Male", "en", "en-GB", VoiceGender.MALE, 24000),
            TtsVoice("g_en_au_female", modelId, id, "Google Australian English Female", "en", "en-AU", VoiceGender.FEMALE, 24000),
            TtsVoice("g_en_au_male", modelId, id, "Google Australian English Male", "en", "en-AU", VoiceGender.MALE, 24000),
            TtsVoice("g_fr_fr_female", modelId, id, "Google French Female", "fr", "fr-FR", VoiceGender.FEMALE, 24000),
            TtsVoice("g_fr_fr_male", modelId, id, "Google French Male", "fr", "fr-FR", VoiceGender.MALE, 24000),
            TtsVoice("g_es_es_female", modelId, id, "Google Spanish Female", "es", "es-ES", VoiceGender.FEMALE, 24000),
            TtsVoice("g_es_es_male", modelId, id, "Google Spanish Male", "es", "es-ES", VoiceGender.MALE, 24000),
            TtsVoice("g_de_de_female", modelId, id, "Google German Female", "de", "de-DE", VoiceGender.FEMALE, 24000),
            TtsVoice("g_de_de_male", modelId, id, "Google German Male", "de", "de-DE", VoiceGender.MALE, 24000),
            TtsVoice("g_it_it_female", modelId, id, "Google Italian Female", "it", "it-IT", VoiceGender.FEMALE, 24000),
            TtsVoice("g_ja_jp_female", modelId, id, "Google Japanese Female", "ja", "ja-JP", VoiceGender.FEMALE, 24000),
            TtsVoice("g_zh_cn_female", modelId, id, "Google Mandarin Female", "zh", "zh-CN", VoiceGender.FEMALE, 24000),
            TtsVoice("g_ru_ru_female", modelId, id, "Google Russian Female", "ru", "ru-RU", VoiceGender.FEMALE, 24000),
            TtsVoice("g_pt_br_female", modelId, id, "Google Portuguese Female", "pt", "pt-BR", VoiceGender.FEMALE, 24000)
        )
    }

    override suspend fun loadModel(modelId: String, modelPath: String) {
        _lifecycleState.value = EngineLifecycleState.READY
    }

    override suspend fun unloadModel() {
        _lifecycleState.value = EngineLifecycleState.UNLOADED
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio = withContext(Dispatchers.IO) {
        val langCode = when {
            voiceId.contains("en_uk", ignoreCase = true) || voiceId.contains("gb", ignoreCase = true) -> "en-GB"
            voiceId.contains("en_au", ignoreCase = true) || voiceId.contains("au", ignoreCase = true) -> "en-AU"
            voiceId.contains("en_in", ignoreCase = true) -> "en-IN"
            voiceId.contains("en_us", ignoreCase = true) -> "en-US"
            voiceId.contains("hi", ignoreCase = true) -> "hi-IN"
            voiceId.contains("fr", ignoreCase = true) -> "fr-FR"
            voiceId.contains("es", ignoreCase = true) -> "es-ES"
            voiceId.contains("de", ignoreCase = true) -> "de-DE"
            voiceId.contains("it", ignoreCase = true) -> "it-IT"
            voiceId.contains("ja", ignoreCase = true) -> "ja-JP"
            voiceId.contains("zh", ignoreCase = true) -> "zh-CN"
            voiceId.contains("ru", ignoreCase = true) -> "ru-RU"
            voiceId.contains("pt", ignoreCase = true) -> "pt-BR"
            else -> "en-US"
        }

        try {
            val chunks = splitTextIntoChunks(text, 150)
            val combinedMp3Bytes = java.io.ByteArrayOutputStream()

            for (chunk in chunks) {
                if (chunk.isBlank()) continue
                val encodedText = URLEncoder.encode(chunk, "UTF-8")
                var urlString = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=$langCode&client=tw-ob"
                var connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.connectTimeout = 7000
                connection.readTimeout = 7000

                var responseCode = connection.responseCode
                if (responseCode != 200) {
                    urlString = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=$langCode&client=gtx"
                    connection = URL(urlString).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connection.connectTimeout = 7000
                    connection.readTimeout = 7000
                    responseCode = connection.responseCode
                }

                if (responseCode == 200) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        combinedMp3Bytes.write(bytes)
                    }
                } else {
                    throw TtsEngineException.NativeRuntimeTtsException("Google Cloud TTS HTTP error: $responseCode")
                }
            }

            val finalMp3 = combinedMp3Bytes.toByteArray()
            if (finalMp3.isNotEmpty()) {
                return@withContext SynthesizedAudio(
                    sampleRate = 24000,
                    channels = 1,
                    encoding = AudioEncoding.MP3,
                    durationMs = 3000L,
                    pcmData = finalMp3
                )
            } else {
                throw TtsEngineException.NativeRuntimeTtsException("Google Cloud TTS returned empty audio")
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleCloudTtsEngine", "Google TTS Web API synthesis failed: ${e.message}", e)
            throw if (e is TtsEngineException) e else TtsEngineException.NativeRuntimeTtsException("Google Cloud TTS error: ${e.message}", e)
        }
    }

    private fun splitTextIntoChunks(text: String, maxLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        var currentText = text
        while (currentText.isNotEmpty()) {
            if (currentText.length <= maxLength) {
                chunks.add(currentText)
                break
            }
            // Find last space before maxLength
            var splitIndex = currentText.lastIndexOf(' ', maxLength)
            if (splitIndex == -1) {
                splitIndex = maxLength
            }
            chunks.add(currentText.substring(0, splitIndex))
            currentText = currentText.substring(splitIndex).trim()
        }
        return chunks
    }

    override suspend fun cancelSynthesis() {}

    override suspend fun release() {}
}
