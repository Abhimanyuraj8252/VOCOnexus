package com.voconexus.app.core.tts.device

data class ModelRequirements(
    val modelId: String,
    val modelName: String,
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val estimatedRuntimeMemoryMb: Int,
    val modelFileSizeBytes: Long,
    val requiredAbi: String = "arm64-v8a",
    val supportedLanguages: List<String> = listOf("en"),
    val supportedEngines: List<String> = listOf("kokoro")
)

data class ModelRecommendation(
    val modelId: String,
    val category: CompatibilityLevel,
    val summary: String,
    val expectedPerformanceText: String,
    val reasons: List<String>,
    val warnings: List<String>,
    val isExecutableOnDevice: Boolean
)

class ModelRecommendationEngine {

    fun evaluateRecommendation(
        profile: DeviceProfile,
        requirements: ModelRequirements
    ): ModelRecommendation {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. ABI Check
        val isAbiSupported = profile.staticInfo.supportedAbis.any { abi ->
            abi.contains(requirements.requiredAbi, ignoreCase = true) ||
                    (requirements.requiredAbi == "arm64-v8a" && abi.contains("arm64"))
        }

        if (isAbiSupported) {
            reasons.add("✓ Supported ABI architecture (${profile.primaryAbi}).")
        } else {
            warnings.add("✕ Required ABI (${requirements.requiredAbi}) is not supported by your device (${profile.primaryAbi}).")
        }

        // 2. RAM Check
        val hasMinRam = profile.totalRamMb >= requirements.minimumRamMb
        val hasRecommendedRam = profile.totalRamMb >= requirements.recommendedRamMb
        val hasAvailableRam = profile.availableRamMb >= requirements.estimatedRuntimeMemoryMb

        if (hasRecommendedRam) {
            reasons.add("✓ Total RAM (${profile.totalRamMb / 1024} GB) meets recommended requirement (${requirements.recommendedRamMb / 1024} GB).")
        } else if (hasMinRam) {
            warnings.add("⚠ Total RAM (${profile.totalRamMb} MB) meets minimum but is below recommended (${requirements.recommendedRamMb} MB).")
        } else {
            warnings.add("✕ Total RAM (${profile.totalRamMb} MB) is below minimum requirement (${requirements.minimumRamMb} MB).")
        }

        if (hasAvailableRam) {
            reasons.add("✓ Available RAM (${profile.availableRamMb} MB) is sufficient for runtime (~${requirements.estimatedRuntimeMemoryMb} MB).")
        } else {
            warnings.add("⚠ Current available RAM (${profile.availableRamMb} MB) is low for model runtime memory (~${requirements.estimatedRuntimeMemoryMb} MB).")
        }

        // 3. Storage Check
        val requiredStorageMb = (requirements.modelFileSizeBytes * 1.5f / (1024 * 1024)).toLong()
        if (profile.availableStorageMb >= requiredStorageMb) {
            reasons.add("✓ Sufficient storage free (${profile.availableStorageMb} MB free vs ${requiredStorageMb} MB required).")
        } else {
            warnings.add("✕ Insufficient storage free (${profile.availableStorageMb} MB free, ${requiredStorageMb} MB required for installation).")
        }

        // Categorize
        val category = when {
            !isAbiSupported || profile.availableStorageMb < requiredStorageMb -> CompatibilityLevel.UNSUPPORTED
            !hasMinRam || profile.availableRamMb < 400 -> CompatibilityLevel.NOT_RECOMMENDED
            !hasRecommendedRam || !hasAvailableRam -> CompatibilityLevel.HEAVY
            warnings.isNotEmpty() -> CompatibilityLevel.COMPATIBLE
            else -> CompatibilityLevel.RECOMMENDED
        }

        val perfText = when (category) {
            CompatibilityLevel.RECOMMENDED -> "Fast to Moderate generation expected."
            CompatibilityLevel.COMPATIBLE -> "Moderate generation speed expected."
            CompatibilityLevel.HEAVY -> "Substantial memory usage. Generation may be slower."
            CompatibilityLevel.NOT_RECOMMENDED -> "Slow generation expected with risk of memory pressure."
            CompatibilityLevel.UNSUPPORTED -> "Model cannot be executed on this device."
        }

        val summary = when (category) {
            CompatibilityLevel.RECOMMENDED -> "Recommended for your device"
            CompatibilityLevel.COMPATIBLE -> "Compatible with minor warnings"
            CompatibilityLevel.HEAVY -> "Heavy model requirement"
            CompatibilityLevel.NOT_RECOMMENDED -> "Not recommended for current resources"
            CompatibilityLevel.UNSUPPORTED -> "Unsupported on this device"
        }

        return ModelRecommendation(
            modelId = requirements.modelId,
            category = category,
            summary = summary,
            expectedPerformanceText = perfText,
            reasons = reasons,
            warnings = warnings,
            isExecutableOnDevice = isAbiSupported && profile.availableStorageMb >= requiredStorageMb
        )
    }
}
