package com.voconexus.app.core.tts

enum class ModelStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLING,
    INSTALLED,
    FAILED,
    UPDATE_AVAILABLE,
    CORRUPTED
}

data class ModelLicenseInfo(
    val licenseName: String,
    val licenseUrl: String,
    val attributionRequired: Boolean = true,
    val commercialUseAllowed: Boolean = true
)

data class TtsModel(
    val id: String,
    val engineId: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val checksumSha256: String,
    val status: ModelStatus = ModelStatus.NOT_INSTALLED,
    val installedPath: String? = null,
    val supportedLanguages: List<String> = listOf("en"),
    val voicesCount: Int = 1,
    val license: ModelLicenseInfo = ModelLicenseInfo("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    val minRamMb: Int = 2048,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null
)
