package com.voconexus.app.core.data.repository

import com.voconexus.app.core.data.dao.TtsModelDao
import com.voconexus.app.core.data.dao.TtsVoiceDao
import com.voconexus.app.core.data.db.TtsModelEntity
import com.voconexus.app.core.data.db.TtsVoiceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface VoiceRepository {
    fun getAllVoices(): Flow<List<TtsVoiceEntity>>
    fun getAllModels(): Flow<List<TtsModelEntity>>
    suspend fun seedDefaultCatalog()
}

class VoiceRepositoryImpl(
    private val modelDao: TtsModelDao,
    private val voiceDao: TtsVoiceDao
) : VoiceRepository {

    override fun getAllVoices(): Flow<List<TtsVoiceEntity>> = voiceDao.getAllVoicesFlow()

    override fun getAllModels(): Flow<List<TtsModelEntity>> = modelDao.getAllModelsFlow()

    override suspend fun seedDefaultCatalog() {
        val allEngineVoices = listOf(
            // Kokoro 18 Voices
            TtsVoiceEntity("hf_alpha", "Alpha (Kokoro Hindi Female / हिन्दी 🇮🇳)", "kokoro-v1.0", "kokoro-v1.0", "Female", "hi-IN", "hi_IN", isDefault = true),
            TtsVoiceEntity("hm_omega", "Omega (Kokoro Hindi Male / हिन्दी 🇮🇳)", "kokoro-v1.0", "kokoro-v1.0", "Male", "hi-IN", "hi_IN"),
            TtsVoiceEntity("hf_beta", "Beta (Kokoro Hindi Female / हिन्दी 🇮🇳)", "kokoro-v1.0", "kokoro-v1.0", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("hf_psi", "Psi (Kokoro Hindi Female / हिन्दी 🇮🇳)", "kokoro-v1.0", "kokoro-v1.0", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("af_heart", "Heart (Kokoro US Female / Natural)", "kokoro-v1.0", "kokoro-v1.0", "Female", "en-US", "en_US"),
            TtsVoiceEntity("af_bella", "Bella (Kokoro US Female / Expressive)", "kokoro-v1.0", "kokoro-v1.0", "Female", "en-US", "en_US"),
            TtsVoiceEntity("af_sky", "Sky (Kokoro US Female / Bright)", "kokoro-v1.0", "kokoro-v1.0", "Female", "en-US", "en_US"),
            TtsVoiceEntity("af_nicole", "Nicole (Kokoro US Female / Smooth)", "kokoro-v1.0", "kokoro-v1.0", "Female", "en-US", "en_US"),
            TtsVoiceEntity("am_adam", "Adam (Kokoro US Male / Professional)", "kokoro-v1.0", "kokoro-v1.0", "Male", "en-US", "en_US"),
            TtsVoiceEntity("am_michael", "Michael (Kokoro US Male / Warm)", "kokoro-v1.0", "kokoro-v1.0", "Male", "en-US", "en_US"),
            TtsVoiceEntity("bf_emma", "Emma (Kokoro UK Female)", "kokoro-v1.0", "kokoro-v1.0", "Female", "en-GB", "en_GB"),
            TtsVoiceEntity("bm_george", "George (Kokoro UK Male)", "kokoro-v1.0", "kokoro-v1.0", "Male", "en-GB", "en_GB"),
            TtsVoiceEntity("ff_siwis", "Siwis (Kokoro French Female 🇫🇷)", "kokoro-v1.0", "kokoro-v1.0", "Female", "fr-FR", "fr_FR"),
            TtsVoiceEntity("ef_dora", "Dora (Kokoro Spanish Female 🇪🇸)", "kokoro-v1.0", "kokoro-v1.0", "Female", "es-ES", "es_ES"),
            TtsVoiceEntity("em_alex", "Alex (Kokoro Spanish Male 🇪🇸)", "kokoro-v1.0", "kokoro-v1.0", "Male", "es-ES", "es_ES"),
            TtsVoiceEntity("if_sara", "Sara (Kokoro Italian Female 🇮🇹)", "kokoro-v1.0", "kokoro-v1.0", "Female", "it-IT", "it_IT"),
            TtsVoiceEntity("jf_alpha", "Alpha (Kokoro Japanese Female 🇯🇵)", "kokoro-v1.0", "kokoro-v1.0", "Female", "ja-JP", "ja_JP"),
            TtsVoiceEntity("zf_xiaoxiao", "Xiaoxiao (Kokoro Mandarin Female 🇨🇳)", "kokoro-v1.0", "kokoro-v1.0", "Female", "zh-CN", "zh_CN"),
            TtsVoiceEntity("zm_yunjian", "Yunjian (Kokoro Mandarin Male 🇨🇳)", "kokoro-v1.0", "kokoro-v1.0", "Male", "zh-CN", "zh_CN"),

            // Microsoft Edge TTS Voices
            TtsVoiceEntity("hi-IN-SwaraNeural", "Swara (Edge Hindi Female / हिन्दी 🇮🇳)", "edge-tts", "edge-tts", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("hi-IN-MadhurNeural", "Madhur (Edge Hindi Male / हिन्दी 🇮🇳)", "edge-tts", "edge-tts", "Male", "hi-IN", "hi_IN"),
            TtsVoiceEntity("en-IN-NeerjaNeural", "Neerja (Edge Indian Female 🇮🇳)", "edge-tts", "edge-tts", "Female", "en-IN", "en_IN"),
            TtsVoiceEntity("en-IN-PrabhatNeural", "Prabhat (Edge Indian Male 🇮🇳)", "edge-tts", "edge-tts", "Male", "en-IN", "en_IN"),
            TtsVoiceEntity("en-US-AvaMultilingualNeural", "Ava (Edge US Multilingual Female)", "edge-tts", "edge-tts", "Female", "en-US", "en_US"),
            TtsVoiceEntity("en-US-AndrewMultilingualNeural", "Andrew (Edge US Multilingual Male)", "edge-tts", "edge-tts", "Male", "en-US", "en_US"),
            TtsVoiceEntity("en-US-BrianMultilingualNeural", "Brian (Edge US Multilingual Male)", "edge-tts", "edge-tts", "Male", "en-US", "en_US"),
            TtsVoiceEntity("en-US-EmmaMultilingualNeural", "Emma (Edge US Multilingual Female)", "edge-tts", "edge-tts", "Female", "en-US", "en_US"),

            // Google Cloud TTS Voices
            TtsVoiceEntity("hi-IN-Wavenet-A", "Google Swara (Hindi Female / हिन्दी 🇮🇳)", "google-cloud-tts", "google-cloud-tts", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("hi-IN-Wavenet-B", "Google Madhur (Hindi Male / हिन्दी 🇮🇳)", "google-cloud-tts", "google-cloud-tts", "Male", "hi-IN", "hi_IN"),
            TtsVoiceEntity("hi-IN-Neural2-A", "Google Neural2 (Hindi Female / हिन्दी 🇮🇳)", "google-cloud-tts", "google-cloud-tts", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("hi-IN-Neural2-B", "Google Neural2 (Hindi Male / हिन्दी 🇮🇳)", "google-cloud-tts", "google-cloud-tts", "Male", "hi-IN", "hi_IN"),

            // Piper TTS Voices
            TtsVoiceEntity("piper_hi_female", "Piper Swara (Hindi Female / हिन्दी 🇮🇳)", "piper-tts", "piper-tts", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("piper_hi_male", "Piper Madhur (Hindi Male / हिन्दी 🇮🇳)", "piper-tts", "piper-tts", "Male", "hi-IN", "hi_IN"),
            TtsVoiceEntity("piper_en_lessac", "Piper Lessac (US English Female)", "piper-tts", "piper-tts", "Female", "en-US", "en_US"),
            TtsVoiceEntity("piper_en_ryan", "Piper Ryan (US English Male)", "piper-tts", "piper-tts", "Male", "en-US", "en_US"),

            // Sherpa ONNX Voices
            TtsVoiceEntity("sherpa_hi_alpha", "Sherpa Alpha (Hindi Female / हिन्दी 🇮🇳)", "sherpa-onnx", "sherpa-onnx", "Female", "hi-IN", "hi_IN"),
            TtsVoiceEntity("sherpa_hi_omega", "Sherpa Omega (Hindi Male / हिन्दी 🇮🇳)", "sherpa-onnx", "sherpa-onnx", "Male", "hi-IN", "hi_IN"),
            TtsVoiceEntity("sherpa_af_heart", "Sherpa Heart (US Female)", "sherpa-onnx", "sherpa-onnx", "Female", "en-US", "en_US"),
            TtsVoiceEntity("sherpa_am_adam", "Sherpa Adam (US Male)", "sherpa-onnx", "sherpa-onnx", "Male", "en-US", "en_US")
        )

        val allModels = listOf(
            TtsModelEntity("kokoro-v1.0", "Kokoro 82M v1.0", "kokoro-v1.0", "1.0.0", isDownloaded = true, localPath = "models/kokoro-v1.0.onnx", sizeBytes = 86000000L),
            TtsModelEntity("piper-tts", "Piper TTS", "piper-tts", "0.9.2", isDownloaded = true, localPath = "models/piper-en-medium.onnx", sizeBytes = 64000000L),
            TtsModelEntity("edge-tts", "Microsoft Edge Cloud TTS", "edge-tts", "1.0.0", isDownloaded = true, localPath = "", sizeBytes = 0L),
            TtsModelEntity("google-cloud-tts", "Google Cloud Neural TTS", "google-cloud-tts", "1.0.0", isDownloaded = true, localPath = "", sizeBytes = 0L),
            TtsModelEntity("sherpa-onnx", "Sherpa ONNX Offline TTS", "sherpa-onnx", "1.0.0", isDownloaded = true, localPath = "", sizeBytes = 0L),
            TtsModelEntity("android-tts", "Android System TTS", "android-tts", "1.0.0", isDownloaded = true, localPath = "", sizeBytes = 0L)
        )

        modelDao.insertModels(allModels)
        voiceDao.insertVoices(allEngineVoices)
    }
}
