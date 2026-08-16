package com.voconexus.app.core.tts.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.voconexus.app.core.tts.AudioEncoding
import com.voconexus.app.core.tts.SynthesisSettings
import com.voconexus.app.core.tts.SynthesizedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

class AndroidTtsSynthesizer(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
            }
        }
    }

    suspend fun synthesizeText(
        text: String,
        voiceId: String,
        settings: SynthesisSettings
    ): SynthesizedAudio? = withContext(Dispatchers.IO) {
        val ttsEngine = tts ?: return@withContext null
        if (!isInitialized) {
            var waitCount = 0
            while (!isInitialized && waitCount < 15) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }
        }
        if (!isInitialized) return@withContext null

        val targetLocale = when {
            voiceId.contains("fr", ignoreCase = true) || voiceId.contains("siwis", ignoreCase = true) -> Locale.FRANCE
            voiceId.contains("es", ignoreCase = true) || voiceId.contains("dora", ignoreCase = true) || voiceId.contains("elvira", ignoreCase = true) || voiceId.contains("alvaro", ignoreCase = true) -> Locale("es", "ES")
            voiceId.contains("it", ignoreCase = true) || voiceId.contains("sara", ignoreCase = true) || voiceId.contains("elsa", ignoreCase = true) -> Locale.ITALY
            voiceId.contains("ja", ignoreCase = true) || voiceId.contains("jp", ignoreCase = true) || voiceId.contains("nanami", ignoreCase = true) -> Locale.JAPAN
            voiceId.contains("zh", ignoreCase = true) || voiceId.contains("cn", ignoreCase = true) || voiceId.contains("xiao", ignoreCase = true) -> Locale.CHINA
            voiceId.contains("de", ignoreCase = true) || voiceId.contains("katja", ignoreCase = true) -> Locale.GERMANY
            voiceId.contains("ru", ignoreCase = true) -> Locale("ru", "RU")
            voiceId.contains("pt", ignoreCase = true) -> Locale("pt", "BR")
            voiceId.contains("hi", ignoreCase = true) || voiceId.contains("alpha", ignoreCase = true) || voiceId.contains("beta", ignoreCase = true) || voiceId.contains("omega", ignoreCase = true) || voiceId.contains("psi", ignoreCase = true) || voiceId.contains("swara", ignoreCase = true) || voiceId.contains("madhur", ignoreCase = true) || text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI } -> Locale("hi", "IN")
            voiceId.contains("gb", ignoreCase = true) || voiceId.contains("uk", ignoreCase = true) || voiceId.contains("emma", ignoreCase = true) || voiceId.contains("george", ignoreCase = true) -> Locale.UK
            else -> Locale.US
        }
        ttsEngine.language = targetLocale

        // Try to select the best matching Android TTS voice for target locale & gender
        try {
            val availableVoices = ttsEngine.voices
            if (availableVoices != null) {
                val isMale = voiceId.contains("male", ignoreCase = true) || voiceId.contains("guy", ignoreCase = true) || voiceId.contains("adam", ignoreCase = true) || voiceId.contains("michael", ignoreCase = true) || voiceId.contains("george", ignoreCase = true) || voiceId.contains("omega", ignoreCase = true) || voiceId.contains("henri", ignoreCase = true) || voiceId.contains("alvaro", ignoreCase = true) || voiceId.contains("madhur", ignoreCase = true) || voiceId.contains("ryan", ignoreCase = true) || voiceId.contains("yunjian", ignoreCase = true)
                val matchedVoice = availableVoices
                    .filter { it.locale.language == targetLocale.language }
                    .maxByOrNull { v ->
                        var score = 0
                        if (isMale && (v.name.contains("male", ignoreCase = true) || v.name.contains("man", ignoreCase = true))) score += 10
                        if (!isMale && (v.name.contains("female", ignoreCase = true) || v.name.contains("woman", ignoreCase = true))) score += 10
                        if (v.locale.country == targetLocale.country) score += 5
                        score
                    }
                if (matchedVoice != null) {
                    ttsEngine.voice = matchedVoice
                }
            }
        } catch (_: Exception) {}

        val (targetPitch, targetRate) = when {
            voiceId.contains("bella", ignoreCase = true) -> Pair(1.25f, 1.05f)
            voiceId.contains("sky", ignoreCase = true) -> Pair(1.35f, 0.95f)
            voiceId.contains("nicole", ignoreCase = true) -> Pair(1.15f, 1.0f)
            voiceId.contains("adam", ignoreCase = true) -> Pair(0.80f, 0.95f)
            voiceId.contains("michael", ignoreCase = true) -> Pair(0.75f, 0.90f)
            voiceId.contains("george", ignoreCase = true) -> Pair(0.70f, 0.85f)
            voiceId.contains("alpha", ignoreCase = true) -> Pair(1.15f, 1.0f)
            voiceId.contains("beta", ignoreCase = true) -> Pair(1.30f, 1.05f)
            voiceId.contains("omega", ignoreCase = true) -> Pair(0.82f, 0.92f)
            voiceId.contains("psi", ignoreCase = true) -> Pair(1.05f, 0.98f)
            voiceId.contains("male", ignoreCase = true) || voiceId.contains("guy", ignoreCase = true) || voiceId.contains("madhur", ignoreCase = true) -> Pair(0.85f, 0.95f)
            voiceId.contains("female", ignoreCase = true) || voiceId.contains("swara", ignoreCase = true) || voiceId.contains("aria", ignoreCase = true) -> Pair(1.20f, 1.05f)
            else -> Pair(1.0f, 1.0f)
        }

        ttsEngine.setPitch(targetPitch)
        ttsEngine.setSpeechRate(settings.speed * targetRate)

        val outputFile = File(context.cacheDir, "tts_synth_${System.currentTimeMillis()}.wav")

        val success = suspendCancellableCoroutine<Boolean> { continuation ->
            val utteranceId = "voconexus_${System.currentTimeMillis()}"
            val listener = object : UtteranceProgressListener() {
                override fun onStart(uttId: String?) {}
                override fun onDone(uttId: String?) {
                    if (uttId == utteranceId) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                }
                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onError(uttId: String?) {
                    if (uttId == utteranceId) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
            ttsEngine.setOnUtteranceProgressListener(listener)

            val params = Bundle()
            val result = ttsEngine.synthesizeToFile(text, params, outputFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                if (continuation.isActive) continuation.resume(false)
            }
        }

        if (success && outputFile.exists() && outputFile.length() > 44) {
            val pcmBytes = readWavToPcm(outputFile)
            outputFile.delete()
            if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                return@withContext SynthesizedAudio(
                    sampleRate = settings.sampleRate,
                    channels = 1,
                    encoding = AudioEncoding.PCM_16BIT,
                    durationMs = (pcmBytes.size / (settings.sampleRate * 2 / 1000)).toLong().coerceAtLeast(400L),
                    pcmData = pcmBytes
                )
            }
        }

        outputFile.delete()
        return@withContext null
    }

    private fun readWavToPcm(wavFile: File): ByteArray? {
        return try {
            val fileBytes = FileInputStream(wavFile).use { it.readBytes() }
            if (fileBytes.size < 44) return null
            val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)

            val riff = ByteArray(4)
            buffer.get(riff)
            if (String(riff) != "RIFF") return null
            buffer.int // fileSize
            val wave = ByteArray(4)
            buffer.get(wave)
            if (String(wave) != "WAVE") return null

            var dataOffset = -1
            var dataSize = -1

            while (buffer.remaining() >= 8) {
                val chunkIdBytes = ByteArray(4)
                buffer.get(chunkIdBytes)
                val chunkId = String(chunkIdBytes)
                val chunkSize = buffer.int

                if (chunkId == "data") {
                    dataOffset = buffer.position()
                    dataSize = chunkSize
                    break
                } else {
                    buffer.position(buffer.position() + chunkSize)
                }
            }

            if (dataOffset != -1 && dataSize > 0 && dataOffset + dataSize <= fileBytes.size) {
                fileBytes.copyOfRange(dataOffset, dataOffset + dataSize)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
