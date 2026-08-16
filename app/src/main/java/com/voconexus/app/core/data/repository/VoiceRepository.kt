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
        val existingModels = modelDao.getAllModelsFlow().first()
        if (existingModels.isEmpty()) {
            val defaultModels = listOf(
                TtsModelEntity(
                    id = "kokoro-v1.0",
                    name = "Kokoro 82M v1.0",
                    engineId = "kokoro-82m",
                    version = "1.0.0",
                    isDownloaded = true,
                    localPath = "models/kokoro-v1.0.onnx",
                    sizeBytes = 86000000L
                ),
                TtsModelEntity(
                    id = "piper-en-medium",
                    name = "Piper English Medium",
                    engineId = "piper",
                    version = "0.9.2",
                    isDownloaded = true,
                    localPath = "models/piper-en-medium.onnx",
                    sizeBytes = 64000000L
                )
            )
            modelDao.insertModels(defaultModels)

            val defaultVoices = listOf(
                TtsVoiceEntity(
                    id = "af_heart",
                    name = "Heart (Female / Natural US)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "en-US",
                    locale = "en_US"
                ),
                TtsVoiceEntity(
                    id = "af_bella",
                    name = "Bella (Female / Expressive)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "en-US",
                    locale = "en_US"
                ),
                TtsVoiceEntity(
                    id = "af_sky",
                    name = "Sky (Female / Bright)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "en-US",
                    locale = "en_US"
                ),
                TtsVoiceEntity(
                    id = "af_nicole",
                    name = "Nicole (Female / Smooth)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "en-US",
                    locale = "en_US"
                ),
                TtsVoiceEntity(
                    id = "am_adam",
                    name = "Adam (Male / Professional)",
                    modelId = "kokoro-v1.0",
                    gender = "Male",
                    language = "en-US",
                    locale = "en_US"
                ),
                TtsVoiceEntity(
                    id = "am_michael",
                    name = "Michael (Male / Warm)",
                    modelId = "kokoro-v1.0",
                    gender = "Male",
                    language = "en-US",
                    locale = "en_US"
                ),
                TtsVoiceEntity(
                    id = "hf_alpha",
                    name = "Alpha (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "hi-IN",
                    locale = "hi_IN"
                ),
                TtsVoiceEntity(
                    id = "hf_beta",
                    name = "Beta (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "hi-IN",
                    locale = "hi_IN"
                ),
                TtsVoiceEntity(
                    id = "hm_omega",
                    name = "Omega (Kokoro Hindi Male / हिन्दी 🇮🇳)",
                    modelId = "kokoro-v1.0",
                    gender = "Male",
                    language = "hi-IN",
                    locale = "hi_IN"
                ),
                TtsVoiceEntity(
                    id = "hf_psi",
                    name = "Psi (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    modelId = "kokoro-v1.0",
                    gender = "Female",
                    language = "hi-IN",
                    locale = "hi_IN"
                ),
                TtsVoiceEntity(
                    id = "piper_ryan",
                    name = "Ryan (Male / Clear)",
                    modelId = "piper-en-medium",
                    gender = "Male",
                    language = "en-US",
                    locale = "en_US"
                )
            )
            voiceDao.insertVoices(defaultVoices)
        } else {
            // Ensure Hindi voices exist in database if missing
            val hindiAlpha = TtsVoiceEntity(id = "hf_alpha", name = "Alpha (Kokoro Hindi Female / हिन्दी 🇮🇳)", modelId = "kokoro-v1.0", gender = "Female", language = "hi-IN", locale = "hi_IN")
            val hindiBeta = TtsVoiceEntity(id = "hf_beta", name = "Beta (Kokoro Hindi Female / हिन्दी 🇮🇳)", modelId = "kokoro-v1.0", gender = "Female", language = "hi-IN", locale = "hi_IN")
            val hindiOmega = TtsVoiceEntity(id = "hm_omega", name = "Omega (Kokoro Hindi Male / हिन्दी 🇮🇳)", modelId = "kokoro-v1.0", gender = "Male", language = "hi-IN", locale = "hi_IN")
            val hindiPsi = TtsVoiceEntity(id = "hf_psi", name = "Psi (Kokoro Hindi Female / हिन्दी 🇮🇳)", modelId = "kokoro-v1.0", gender = "Female", language = "hi-IN", locale = "hi_IN")
            voiceDao.insertVoices(listOf(hindiAlpha, hindiBeta, hindiOmega, hindiPsi))
        }
    }
}
