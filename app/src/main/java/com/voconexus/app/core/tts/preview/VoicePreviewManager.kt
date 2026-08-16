package com.voconexus.app.core.tts.preview

import android.content.Context
import com.voconexus.app.core.tts.SynthesizedAudio
import com.voconexus.app.core.tts.TtsEngine
import com.voconexus.app.core.tts.TtsVoice
import com.voconexus.app.core.tts.engine.AndroidTtsSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoicePreviewManager(
    private val context: Context,
    val previewPlayer: AudioPreviewPlayer
) {

    val previewCacheDir: File
        get() = File(context.cacheDir, "previews").also { if (!it.exists()) it.mkdirs() }

    suspend fun generateAndPlayPreview(
        engine: TtsEngine,
        voice: TtsVoice,
        previewText: String? = null
    ) = withContext(Dispatchers.IO) {
        val isMultilingual = voice.name.contains("Multilingual", ignoreCase = true) ||
            voice.language.contains("+", ignoreCase = true) ||
            voice.id.contains("hf_", ignoreCase = true) ||
            voice.id.contains("hm_", ignoreCase = true) ||
            (voice.language.contains("hi", ignoreCase = true) && voice.engineId == "kokoro-82m")

        val isHindi = voice.language.contains("hi", ignoreCase = true) ||
            voice.name.contains("Hindi", ignoreCase = true) ||
            voice.locale.contains("IN", ignoreCase = true)

        // Build bilingual / trilingual preview text for ALL engines (Edge, Google, Piper, Sherpa, Kokoro)
        val textToSpeak = previewText ?: buildBilingualPreviewText(voice)

        // Try primary TTS engine; throw if fails to show proper error in UI
        val audio = engine.synthesize(
            textToSpeak,
            voice.id,
            com.voconexus.app.core.tts.SynthesisSettings()
        )

        val audioFile = if (audio.encoding == com.voconexus.app.core.tts.AudioEncoding.MP3) {
            val mp3File = File(previewCacheDir, "preview_${voice.id}.mp3")
            FileOutputStream(mp3File).use { it.write(audio.pcmData) }
            mp3File
        } else {
            val wavFile = File(previewCacheDir, "preview_${voice.id}.wav")
            writePcmToWav(audio, wavFile)
            wavFile
        }

        val pitch = if (voice.gender == com.voconexus.app.core.tts.VoiceGender.MALE && voice.engineId == "google-cloud-tts") 0.82f else 1.0f

        withContext(Dispatchers.Main) {
            previewPlayer.playPreview(voice.id, audioFile, pitch)
        }
    }

    private fun getHindiPreviewText(voiceId: String): String {
        return when {
            voiceId.contains("alpha", ignoreCase = true) -> "नमस्ते! मैं अल्फा हूँ। यह वोकोनेक्सस का हिंदी ऑडियो उदाहरण है। आज का मौसम बहुत सुहावना है।"
            voiceId.contains("beta", ignoreCase = true) -> "नमस्ते! मैं बीटा हूँ। वोकोनेक्सस प्राकृतिक आवाज़ में बोलता है। कहानी सुनाना मुझे बहुत पसंद है।"
            voiceId.contains("omega", ignoreCase = true) -> "नमस्ते! मैं ओमेगा हूँ। वोकोनेक्सस उच्च गुणवत्ता वाला ऑफलाइन ऑडियो बनाता है। मेरी आवाज़ गहरी और शक्तिशाली है।"
            voiceId.contains("psi", ignoreCase = true) -> "नमस्ते! मैं साई हूँ। वोकोनेक्सस से हिंदी में ऑडियो बुक बनाना आसान है। मेरी आवाज़ मधुर और स्पष्ट है।"
            else -> "नमस्ते! यह वोकोनेक्सस का हिंदी ऑडियो उदाहरण है। प्राकृतिक आवाज़ में बोलना सबसे अच्छा है।"
        }
    }

    private fun buildBilingualPreviewText(voice: TtsVoice): String {
        val lang = voice.language.lowercase()
        val locale = voice.locale.lowercase()
        val voiceId = voice.id.lowercase()

        return when {
            lang.contains("hi") || locale.contains("hi") || voiceId.contains("swara") || voiceId.contains("madhur") || voiceId.contains("g_hi") || voiceId.contains("alpha") || voiceId.contains("beta") || voiceId.contains("omega") || voiceId.contains("psi") -> {
                "नमस्ते! मैं ${voice.name} हूँ। वोकोनेक्सस में आपका स्वागत है। Hello! This is a high quality voice preview."
            }
            locale.contains("en-in") || voiceId.contains("en-in") || voiceId.contains("neerja") || voiceId.contains("prabhat") || voiceId.contains("kavya") || voiceId.contains("ananya") || voiceId.contains("aarav") || voiceId.contains("rehaan") || voiceId.contains("g_en_in") -> {
                "Namaste! Hello, I am ${voice.name}. Welcome to VocoNexus. This is a bilingual voice preview."
            }
            lang.contains("bn") || locale.contains("bn") -> {
                "নমস্কার! আমি ${voice.name}। VocoNexus-এ আপনাকে স্বাগতম। Hello! This is a voice preview."
            }
            lang.contains("gu") || locale.contains("gu") -> {
                "નમસ્તે! હું ${voice.name} છું. VocoNexus માં તમારું સ્વાગત છે. Hello! This is a voice preview."
            }
            lang.contains("ta") || locale.contains("ta") -> {
                "வணக்கம்! நான் ${voice.name}. VocoNexus-க்கு வரவேற்கிறோம். Hello! This is a voice preview."
            }
            lang.contains("te") || locale.contains("te") -> {
                "நமస్కారం! నేను ${voice.name}. VocoNexus కు స్వాగతం. Hello! This is a voice preview."
            }
            lang.contains("mr") || locale.contains("mr") -> {
                "नमस्कार! मी ${voice.name}. VocoNexus मध्ये तुमचे स्वागत आहे. Hello! This is a voice preview."
            }
            lang.contains("fr") || locale.contains("fr") -> {
                "Bonjour! Je suis ${voice.name}. Bienvenue sur VocoNexus. Hello! This is a high quality voice preview."
            }
            lang.contains("es") || locale.contains("es") -> {
                "¡Hola! Soy ${voice.name}. Bienvenido a VocoNexus. Hello! This is a high quality voice preview."
            }
            lang.contains("de") || locale.contains("de") -> {
                "Hallo! Ich bin ${voice.name}. Willkommen bei VocoNexus. Hello! This is a high quality voice preview."
            }
            lang.contains("it") || locale.contains("it") -> {
                "Ciao! Sono ${voice.name}. Benvenuto su VocoNexus. Hello! This is a high quality voice preview."
            }
            lang.contains("ja") || locale.contains("ja") -> {
                "こんにちは！ ${voice.name}です。 VocoNexusへようこそ。 Hello! This is a voice preview."
            }
            lang.contains("zh") || locale.contains("zh") -> {
                "你好！我是 ${voice.name}。 欢迎使用 VocoNexus。 Hello! This is a voice preview."
            }
            lang.contains("ru") || locale.contains("ru") -> {
                "Здравствуйте! Я ${voice.name}. Добро пожаловать в VocoNexus. Hello! This is a voice preview."
            }
            else -> {
                "Hello! I am ${voice.name} from VocoNexus. This is a high quality voice preview. Namaste!"
            }
        }
    }

    fun stopPreview() {
        previewPlayer.stop()
    }

    fun clearPreviewCache() {
        if (previewCacheDir.exists()) {
            previewCacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun writePcmToWav(audio: SynthesizedAudio, outputFile: File) {
        val pcmData = audio.pcmData
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val sampleRate = audio.sampleRate.toLong()
        val channels = audio.channels
        val byteRate = sampleRate * channels * 2

        FileOutputStream(outputFile).use { out ->
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
                putInt(totalDataLen.toInt())
                put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())
                put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
                putInt(16)
                putShort(1.toShort())
                putShort(channels.toShort())
                putInt(sampleRate.toInt())
                putInt(byteRate.toInt())
                putShort((channels * 2).toShort())
                putShort(16.toShort())
                put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
                putInt(totalAudioLen.toInt())
            }.array()

            out.write(header)
            out.write(pcmData)
        }
    }
}
