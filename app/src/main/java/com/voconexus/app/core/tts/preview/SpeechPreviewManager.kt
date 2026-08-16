package com.voconexus.app.core.tts.preview

import android.content.Context
import com.voconexus.app.core.dsp.AudioProcessor
import com.voconexus.app.core.dsp.DspParameters
import com.voconexus.app.core.dsp.WsolaAudioProcessor
import com.voconexus.app.core.generation.audio.WavAudioSink
import com.voconexus.app.core.tts.SynthesisSettings
import com.voconexus.app.core.tts.engine.AndroidTtsSynthesizer
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages speech preview synthesis and playback.
 *
 * Design decisions for performance:
 * - KokoroEngine model is loaded ONCE and kept alive (model is 350MB, takes ~5s to load)
 * - AndroidTtsSynthesizer is also kept as a single lazy instance (avoids TTS service re-bind)
 * - unloadModel() is intentionally NOT called after previews – only on release()
 */
class SpeechPreviewManager(
    private val context: Context,
    private val audioPreviewPlayer: AudioPreviewPlayer,
    private val engineRegistry: TtsEngineRegistry,
    private val dspProcessor: AudioProcessor = WsolaAudioProcessor(),
    private val ttsRepository: com.voconexus.app.core.data.repository.TtsRepository
) {
    private var activePreviewFile: File? = null



    // Track last loaded model path so we don't reload on every preview call
    private var loadedModelPath: String? = null
    private var loadedEngineId: String? = null

    suspend fun playSpeechPreview(
        sampleText: String = "Hello! This is a preview of the selected voice. नमस्ते! वोकोनेक्सस में आपका स्वागत है।",
        voiceId: String,
        engineId: String,  // NO default — caller MUST specify
        speed: Float = 1.0f,
        pitchSemitones: Float = 0.0f
    ): Boolean = withContext(Dispatchers.IO) {
        // Stop any current playback (thread-safe – dispatches to main internally)
        audioPreviewPlayer.stop()
        activePreviewFile?.delete()
        activePreviewFile = null

        android.util.Log.i("SpeechPreview", "▶ playSpeechPreview: engineId=$engineId, voiceId=$voiceId")

        val previewDir = File(context.cacheDir, "speech_previews").also { it.mkdirs() }
        val rawFile       = File(previewDir, "raw_preview_${System.currentTimeMillis()}.wav")
        val processedFile = File(previewDir, "processed_preview_${System.currentTimeMillis()}.wav")

        try {
            val settings = SynthesisSettings(speed = speed, pitch = 1.0f)

            // --- Try Native/Cloud engine ---
            val synthesized = try {
                val engine = engineRegistry.getEngine(engineId)
                android.util.Log.i("SpeechPreview", "  Engine lookup for '$engineId': ${engine?.displayName ?: "NULL"}")

                if (engine != null) {
                    val voice = ttsRepository.getVoiceById(voiceId)
                    val model = voice?.let { ttsRepository.getModelById(it.modelId) }
                    android.util.Log.i("SpeechPreview", "  Voice: ${voice?.name}, Model: ${model?.name}, installedPath: ${model?.installedPath}")

                    // Cloud engines (Edge TTS, Google Cloud) don't need local model files
                    val isCloudEngine = engineId == "edge-tts" || engineId == "google-cloud-tts"
                    val installedPath = model?.installedPath ?: ""

                    if (!isCloudEngine && installedPath.isBlank()) {
                        android.util.Log.w("SpeechPreview", "  Offline engine '$engineId' has no installed model path — skipping loadModel")
                    }

                    // Only call loadModel() if path changed (model stays alive between previews)
                    if (loadedModelPath != installedPath || loadedEngineId != engineId) {
                        if (isCloudEngine || installedPath.isNotBlank()) {
                            engine.loadModel(model?.id ?: "", installedPath)
                            loadedModelPath = installedPath
                            loadedEngineId  = engineId
                        }
                    }
                    // Synthesize – engine stays loaded for next preview
                    engine.synthesize(sampleText, voiceId, settings)
                } else null
            } catch (e: Exception) {
                android.util.Log.w("SpeechPreview", "Engine failed for $voiceId (engine=$engineId): ${e.message}")
                null
            }

            val finalAudio = synthesized
            
            if (finalAudio == null) {
                android.util.Log.e("SpeechPreview", "Engine '$engineId' returned null for voiceId=$voiceId — NOT falling back to Android TTS")
                return@withContext false
            }

            val finalFileToPlay = if (finalAudio.encoding == com.voconexus.app.core.tts.AudioEncoding.MP3) {
                val mp3File = File(context.cacheDir, "speech_preview_${voiceId}.mp3")
                java.io.FileOutputStream(mp3File).use { fos -> fos.write(finalAudio.pcmData) }
                mp3File
            } else {
                val sink = WavAudioSink()
                sink.open(rawFile, finalAudio.sampleRate, 1)
                sink.writePcm(finalAudio.pcmData)
                sink.flushAndClose()

                if (!rawFile.exists() || rawFile.length() <= 44) return@withContext false

                val dspSuccess = if (Math.abs(pitchSemitones) >= 0.01f) {
                    dspProcessor.process(
                        sourceFile = rawFile,
                        outputFile = processedFile,
                        parameters = DspParameters(timeStretchRatio = 1.0f, pitchShiftSemitones = pitchSemitones)
                    )
                } else false

                if (dspSuccess && processedFile.exists()) processedFile else rawFile
            }

            activePreviewFile = finalFileToPlay

            val voiceGender = try { ttsRepository.getVoiceById(voiceId)?.gender } catch (_: Exception) { null }
            val pitchToUse = if (voiceGender == com.voconexus.app.core.tts.VoiceGender.MALE && engineId == "google-cloud-tts") 0.82f else 1.0f

            // Play (thread-safe – AudioPreviewPlayer dispatches to main internally)
            audioPreviewPlayer.playPreview(voiceId, finalFileToPlay, pitchToUse)
            return@withContext true

        } catch (e: Exception) {
            android.util.Log.e("SpeechPreview", "playSpeechPreview failed: ${e.message}", e)
            rawFile.delete()
            processedFile.delete()
            return@withContext false
        }
    }

    fun stopPreview() {
        audioPreviewPlayer.stop()
        activePreviewFile?.delete()
        activePreviewFile = null
    }

    /**
     * Release all resources. Call when the screen/component is permanently destroyed.
     * This is the only place where the Kokoro model is unloaded.
     */
    fun release() {
        stopPreview()
        audioPreviewPlayer.release()
        // Unload native model to free ~350MB RAM
        if (loadedEngineId != null) {
            try {
                val engine = engineRegistry.getEngine(loadedEngineId!!)
                kotlinx.coroutines.runBlocking { engine?.unloadModel() }
            } catch (_: Exception) {}
        }
        loadedModelPath = null
        loadedEngineId  = null

    }
}
