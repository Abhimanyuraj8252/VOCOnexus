package com.voconexus.app.core.tts.catalog

data class LicenseInfo(
    val licenseName: String,
    val licenseUrl: String,
    val attributionRequired: Boolean = true,
    val commercialUse: String = "Check License",
    val redistribution: Boolean = true,
    val modification: Boolean = true,
    val notes: String = ""
)

data class VoiceDescriptor(
    val voiceId: String,
    val displayName: String,
    val language: String,
    val locale: String = "en-US",
    val category: String = "General",
    val modelId: String,
    val engineId: String,
    val sizeBytes: Long = 0L,
    val license: LicenseInfo = LicenseInfo("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    val isOfflineCapable: Boolean = true
)

data class ModelDescriptor(
    val modelId: String,
    val displayName: String,
    val provider: String,
    val version: String,
    val engineId: String,
    val languages: List<String>,
    val voicesCount: Int,
    val fileSizeBytes: Long,
    val runtimeMemoryMb: Int,
    val supportedAbis: List<String> = listOf("arm64-v8a", "x86_64"),
    val quantization: String = "FP32",
    val isOfflineCapable: Boolean = true,
    val license: LicenseInfo,
    val checksumSha256: String = "",
    val downloadSourceUrl: String = ""
)

object DefaultModelProvider {
    val kokoroLicense = LicenseInfo(
        licenseName = "Apache-2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        attributionRequired = true,
        commercialUse = "Permitted",
        redistribution = true,
        modification = true,
        notes = "Kokoro 82M open-source model licensed under Apache 2.0."
    )

    val defaultModel = ModelDescriptor(
        modelId = "kokoro-82m-v1.0",
        displayName = "Kokoro 82M",
        provider = "Hexgrad / ONNX",
        version = "1.0",
        engineId = "kokoro-82m",
        languages = listOf("en", "hi", "es", "fr", "ja"),
        voicesCount = 11,
        fileSizeBytes = 340 * 1024 * 1024L,
        runtimeMemoryMb = 600,
        supportedAbis = listOf("arm64-v8a", "x86_64"),
        quantization = "FP32",
        isOfflineCapable = true,
        license = kokoroLicense,
        checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        downloadSourceUrl = "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/kokoro-v1.0.zip"
    )
}

fun ModelDescriptor.toTtsModel(): com.voconexus.app.core.tts.TtsModel {
    return com.voconexus.app.core.tts.TtsModel(
        id = modelId,
        engineId = engineId,
        name = displayName,
        version = version,
        sizeBytes = fileSizeBytes,
        downloadUrl = downloadSourceUrl,
        checksumSha256 = checksumSha256,
        status = com.voconexus.app.core.tts.ModelStatus.NOT_INSTALLED,
        supportedLanguages = languages,
        voicesCount = voicesCount,
        license = com.voconexus.app.core.tts.ModelLicenseInfo(
            licenseName = license.licenseName,
            licenseUrl = license.licenseUrl,
            attributionRequired = license.attributionRequired
        ),
        minRamMb = runtimeMemoryMb
    )
}
