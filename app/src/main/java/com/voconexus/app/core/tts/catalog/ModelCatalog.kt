package com.voconexus.app.core.tts.catalog

class ModelCatalog {

    val catalogVersion: Int = 1
    val updatedAt: Long = System.currentTimeMillis()

    private val models = listOf(
        DefaultModelProvider.defaultModel,
        ModelDescriptor(
            modelId = "piper-en-us-v1.0",
            displayName = "Piper US English",
            provider = "Piper TTS",
            version = "1.0",
            engineId = "piper",
            languages = listOf("en"),
            voicesCount = 4,
            fileSizeBytes = 65 * 1024 * 1024L,
            runtimeMemoryMb = 250,
            supportedAbis = listOf("arm64-v8a", "x86_64", "armeabi-v7a"),
            quantization = "INT8",
            isOfflineCapable = true,
            license = LicenseInfo(
                licenseName = "MIT",
                licenseUrl = "https://opensource.org/licenses/MIT",
                attributionRequired = true,
                commercialUse = "Permitted",
                redistribution = true,
                modification = true
            ),
            checksumSha256 = "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2",
            downloadSourceUrl = "https://github.com/rhasspy/piper/releases/download/v1.0/piper-en-us.zip"
        )
    )

    fun getAvailableModels(): List<ModelDescriptor> = models

    fun getModelById(modelId: String): ModelDescriptor? {
        return models.find { it.modelId == modelId } ?: if (modelId == DefaultModelProvider.defaultModel.modelId) DefaultModelProvider.defaultModel else null
    }
}
