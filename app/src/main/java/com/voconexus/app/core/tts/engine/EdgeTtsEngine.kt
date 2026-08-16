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
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.TtsVoice
import com.voconexus.app.core.tts.VoiceGender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class EdgeTtsEngine(private val context: Context) : TtsEngine {

    override val id: String = "edge-tts"
    override val displayName: String = "Microsoft Edge TTS"
    override val version: String = "1.0.0"

    private val _lifecycleState = MutableStateFlow(EngineLifecycleState.READY)
    override val lifecycleState: StateFlow<EngineLifecycleState> = _lifecycleState.asStateFlow()

    override val capabilities: EngineCapabilities = EngineCapabilities(
        isOffline = false,
        supportsStreaming = true,
        supportsVoiceSelection = true,
        supportsMultilingual = true,
        supportsSpeedControl = true,
        supportsPitchControl = true,
        supportsCpuInference = false,
        supportsGpuAcceleration = false
    )

    private val synthesizer by lazy { AndroidTtsSynthesizer(context) }
    private val client = OkHttpClient.Builder().build()

    override suspend fun getModels(): List<TtsModel> {
        return listOf(
            TtsModel(
                id = "edge-tts",
                engineId = id,
                name = "Microsoft Edge Cloud TTS",
                version = "1.0.0",
                sizeBytes = 0L,
                downloadUrl = "",
                checksumSha256 = "",
                status = ModelStatus.INSTALLED,
                supportedLanguages = listOf("en", "hi", "fr", "es", "it", "ja", "zh", "de"),
                voicesCount = 14,
                license = ModelLicenseInfo(
                    licenseName = "Proprietary",
                    licenseUrl = "https://azure.microsoft.com/en-us/services/cognitive-services/text-to-speech/",
                    attributionRequired = false,
                    commercialUseAllowed = false
                ),
                minRamMb = 512
            )
        )
    }

    override suspend fun getVoices(modelId: String): List<TtsVoice> {
        return listOf(
            TtsVoice("en-US-AvaMultilingualNeural", modelId, id, "Ava Multilingual (आवा) • Hindi / English / Multi Female", "en", "en-US", VoiceGender.FEMALE, 24000, isDefault = true),
            TtsVoice("en-US-AndrewMultilingualNeural", modelId, id, "Andrew Multilingual (एंड्रयू) • Hindi / English / Multi Male", "en", "en-US", VoiceGender.MALE, 24000),
            TtsVoice("en-US-EmmaMultilingualNeural", modelId, id, "Emma Multilingual (एम्मा) • English / Hindi / Multi Female", "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("en-US-BrianMultilingualNeural", modelId, id, "Brian Multilingual (ब्रायन) • English / Hindi / Multi Male", "en", "en-US", VoiceGender.MALE, 24000),
            TtsVoice("hi-IN-SwaraNeural", modelId, id, "Swara (स्वरा) • Hindi / English Multilingual Female", "hi", "hi-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("hi-IN-MadhurNeural", modelId, id, "Madhur (मधुर) • Hindi / English Multilingual Male", "hi", "hi-IN", VoiceGender.MALE, 24000),
            TtsVoice("en-IN-NeerjaNeural", modelId, id, "Neerja (नीरजा) • Indian English / Hindi Multilingual Female", "en", "en-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("en-IN-PrabhatNeural", modelId, id, "Prabhat (प्रभात) • Indian English / Hindi Multilingual Male", "en", "en-IN", VoiceGender.MALE, 24000),
            TtsVoice("en-IN-KavyaNeural", modelId, id, "Kavya (काव्या) • Indian English / Hindi Female", "en", "en-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("en-IN-AnanyaNeural", modelId, id, "Ananya (अनन्या) • Indian English Female", "en", "en-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("en-IN-AaravNeural", modelId, id, "Aarav (आरव) • Indian English Male", "en", "en-IN", VoiceGender.MALE, 24000),
            TtsVoice("en-IN-RehaanNeural", modelId, id, "Rehaan (रेहान) • Indian English Male", "en", "en-IN", VoiceGender.MALE, 24000),
            TtsVoice("en-US-AriaNeural", modelId, id, "Aria Neural • US English Female", "en", "en-US", VoiceGender.FEMALE, 24000),
            TtsVoice("en-US-GuyNeural", modelId, id, "Guy Neural • US English Male", "en", "en-US", VoiceGender.MALE, 24000),
            TtsVoice("en-GB-SoniaNeural", modelId, id, "Sonia Neural • UK English Female", "en", "en-GB", VoiceGender.FEMALE, 24000),
            TtsVoice("en-GB-RyanNeural", modelId, id, "Ryan Neural • UK English Male", "en", "en-GB", VoiceGender.MALE, 24000),
            TtsVoice("bn-IN-TanishaaNeural", modelId, id, "Tanishaa (তানিষা) • Bengali / Hindi Multilingual Female", "bn", "bn-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("gu-IN-DhwaniNeural", modelId, id, "Dhwani (ધ્વનિ) • Gujarati / Hindi Multilingual Female", "gu", "gu-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("kn-IN-SapnaNeural", modelId, id, "Sapna (સપના) • Kannada / Hindi Multilingual Female", "kn", "kn-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("ml-IN-SobhanaNeural", modelId, id, "Sobhana • Malayalam / Hindi Multilingual Female", "ml", "ml-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("mr-IN-AarohiNeural", modelId, id, "Aarohi (आरोही) • Marathi / Hindi Multilingual Female", "mr", "mr-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("ta-IN-PallaviNeural", modelId, id, "Pallavi • Tamil / Hindi Multilingual Female", "ta", "ta-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("te-IN-ShrutiNeural", modelId, id, "Shruti • Telugu / Hindi Multilingual Female", "te", "te-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("ur-IN-GulNeural", modelId, id, "Gul (गुल) • Urdu / Hindi Multilingual Female", "ur", "ur-IN", VoiceGender.FEMALE, 24000),
            TtsVoice("pa-IN-GurpreetNeural", modelId, id, "Gurpreet (गुरप्रीत) • Punjabi / Hindi Multilingual Male", "pa", "pa-IN", VoiceGender.MALE, 24000),
            TtsVoice("fr-FR-DeniseNeural", modelId, id, "Denise Neural • French Female", "fr", "fr-FR", VoiceGender.FEMALE, 24000),
            TtsVoice("fr-FR-HenriNeural", modelId, id, "Henri Neural • French Male", "fr", "fr-FR", VoiceGender.MALE, 24000),
            TtsVoice("es-ES-ElviraNeural", modelId, id, "Elvira Neural • Spanish Female", "es", "es-ES", VoiceGender.FEMALE, 24000),
            TtsVoice("es-ES-AlvaroNeural", modelId, id, "Alvaro Neural • Spanish Male", "es", "es-ES", VoiceGender.MALE, 24000),
            TtsVoice("it-IT-ElsaNeural", modelId, id, "Elsa Neural • Italian Female", "it", "it-IT", VoiceGender.FEMALE, 24000),
            TtsVoice("ja-JP-NanamiNeural", modelId, id, "Nanami Neural • Japanese Female", "ja", "ja-JP", VoiceGender.FEMALE, 24000),
            TtsVoice("zh-CN-XiaoxiaoNeural", modelId, id, "Xiaoxiao Neural • Mandarin Female", "zh", "zh-CN", VoiceGender.FEMALE, 24000),
            TtsVoice("de-DE-KatjaNeural", modelId, id, "Katja Neural • German Female", "de", "de-DE", VoiceGender.FEMALE, 24000)
        )
    }

    override suspend fun loadModel(modelId: String, modelPath: String) {
        _lifecycleState.value = EngineLifecycleState.READY
    }

    override suspend fun unloadModel() {
        _lifecycleState.value = EngineLifecycleState.UNLOADED
    }

    private fun getTimestamp(): String {
        val dateFormat = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(Date())
    }

    private fun getFallbackVoiceId(voiceId: String): String {
        return when (voiceId) {
            "en-IN-AaravNeural", "en-IN-RehaanNeural" -> "en-IN-PrabhatNeural"
            "en-IN-AnanyaNeural" -> "en-IN-NeerjaNeural"
            else -> if (voiceId.startsWith("hi-IN")) "hi-IN-SwaraNeural" else "en-IN-NeerjaNeural"
        }
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio = withContext(Dispatchers.IO) {
        try {
            var audioBytes = try {
                fetchEdgeTtsAudio(text, voiceId, settings.speed)
            } catch (e: Exception) {
                val fallbackVoice = getFallbackVoiceId(voiceId)
                if (fallbackVoice != voiceId) {
                    fetchEdgeTtsAudio(text, fallbackVoice, settings.speed)
                } else throw e
            }

            if (audioBytes.isEmpty()) {
                val fallbackVoice = getFallbackVoiceId(voiceId)
                audioBytes = fetchEdgeTtsAudio(text, fallbackVoice, settings.speed)
            }

            if (audioBytes.isNotEmpty()) {
                return@withContext SynthesizedAudio(
                    sampleRate = 24000,
                    channels = 1,
                    encoding = AudioEncoding.MP3,
                    durationMs = 3000L,
                    pcmData = audioBytes
                )
            }

            val fallback = synthesizer.synthesizeText(text, voiceId, settings)
            if (fallback != null) return@withContext fallback
            throw com.voconexus.app.core.tts.TtsEngineException.NativeRuntimeTtsException("Edge TTS returned empty audio")
        } catch (e: Exception) {
            android.util.Log.e("EdgeTtsEngine", "WebSocket Error or Synthesis Failed for $voiceId, falling back to Android TTS", e)
            try {
                val fallback = synthesizer.synthesizeText(text, voiceId, settings)
                if (fallback != null) return@withContext fallback
            } catch (_: Exception) {}
            throw com.voconexus.app.core.tts.TtsEngineException.NativeRuntimeTtsException("Edge TTS API & Fallback failed: ${e.message}", e)
        }
    }

    private suspend fun fetchEdgeTtsAudio(text: String, voiceId: String, speed: Float): ByteArray = suspendCoroutine { continuation ->
        val connectionId = java.util.UUID.randomUUID().toString().replace("-", "")
        val muid = java.util.UUID.randomUUID().toString().replace("-", "").uppercase()
        val secMsGec = getSecMsGec()
        val secMsGecVersion = "1-143.0.3650.75"
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&ConnectionId=$connectionId&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=$secMsGecVersion"
        val request = Request.Builder().url(url)
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .addHeader("Cookie", "muid=$muid;")
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .build()

        var isDone = false
        val audioBuffer = java.io.ByteArrayOutputStream()

        val lang = if (voiceId.contains("-")) {
            val parts = voiceId.split("-")
            if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "en-US"
        } else "en-US"

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                try {
                    val configMessage = "X-Timestamp:${getTimestamp()}\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMessage)

                    val requestId = UUID.randomUUID().toString().replace("-", "")
                    
                    val rate = if (speed > 1.0f) "+${((speed - 1.0f) * 100).toInt()}%" 
                               else if (speed < 1.0f) "${((speed - 1.0f) * 100).toInt()}%"
                               else "+0%"
                               
                    val safeText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

                    val ssmlMessage = "X-RequestId:$requestId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:${getTimestamp()}\r\n" +
                            "Path:ssml\r\n\r\n" +
                            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$lang'>" +
                            "<voice name='$voiceId'><prosody pitch='+0Hz' rate='$rate' volume='+0%'>" +
                            "$safeText</prosody></voice></speak>"
                    
                    webSocket.send(ssmlMessage)
                } catch (e: Exception) {
                    if (!isDone) {
                        isDone = true
                        continuation.resumeWithException(e)
                        webSocket.close(1000, null)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    if (!isDone) {
                        isDone = true
                        continuation.resume(audioBuffer.toByteArray())
                        webSocket.close(1000, null)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Edge TTS binary messages start with 2 bytes containing the header length.
                val byteArray = bytes.toByteArray()
                if (byteArray.size > 2) {
                    val headerLength = ((byteArray[0].toInt() and 0xFF) shl 8) or (byteArray[1].toInt() and 0xFF)
                    val audioStart = headerLength + 2
                    if (audioStart < byteArray.size) {
                        audioBuffer.write(byteArray, audioStart, byteArray.size - audioStart)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isDone) {
                    isDone = true
                    continuation.resumeWithException(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isDone) {
                    isDone = true
                    continuation.resume(audioBuffer.toByteArray())
                }
            }
        })
    }

    private fun getSecMsGec(): String {
        val ticks = (System.currentTimeMillis() * 10_000L) + 116444736000000000L
        val roundedTicks = ticks - (ticks % 3_000_000_000L)
        val strToHash = "${roundedTicks}6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(strToHash.toByteArray(kotlin.text.Charsets.US_ASCII))
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }

    override suspend fun cancelSynthesis() {}

    override suspend fun release() {
        synthesizer.shutdown()
        client.dispatcher.executorService.shutdown()
    }
}
