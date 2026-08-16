package com.voconexus.app.core.tts.device

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

data class StaticDeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val totalRamMb: Int,
    val cpuCores: Int
)

data class DynamicDeviceState(
    val availableRamMb: Int,
    val isLowMemory: Boolean,
    val lowMemoryThresholdMb: Int,
    val availableStorageMb: Long,
    val thermalState: Int, // PowerManager.THERMAL_STATUS_*
    val batteryLevelPercent: Int,
    val isCharging: Boolean
)

data class DeviceProfile(
    val staticInfo: StaticDeviceProfile,
    val dynamicState: DynamicDeviceState
) {
    val totalRamMb: Int get() = staticInfo.totalRamMb
    val availableRamMb: Int get() = dynamicState.availableRamMb
    val cpuCores: Int get() = staticInfo.cpuCores
    val primaryAbi: String get() = staticInfo.primaryAbi
    val sdkVersion: Int get() = staticInfo.sdkVersion
    val availableStorageMb: Long get() = dynamicState.availableStorageMb
}

enum class CompatibilityLevel {
    RECOMMENDED,
    COMPATIBLE,
    HEAVY,
    NOT_RECOMMENDED,
    UNSUPPORTED
}

data class CompatibilityReport(
    val level: CompatibilityLevel,
    val recommendedRtf: String,
    val summary: String,
    val reasons: List<String>,
    val warnings: List<String>
)

class DeviceProfileEvaluator(private val context: Context) {

    private val cachedStaticProfile: StaticDeviceProfile by lazy {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamMb = (memoryInfo.totalMem / (1024 * 1024)).toInt()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "arm64-v8a"
        val supportedAbis = Build.SUPPORTED_ABIS.toList()

        StaticDeviceProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            primaryAbi = primaryAbi,
            supportedAbis = supportedAbis,
            totalRamMb = totalRamMb,
            cpuCores = cpuCores
        )
    }

    fun getStaticProfile(): StaticDeviceProfile = cachedStaticProfile

    fun getDynamicState(): DynamicDeviceState {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val availableRamMb = (memoryInfo.availMem / (1024 * 1024)).toInt()
        val lowMemoryThresholdMb = (memoryInfo.threshold / (1024 * 1024)).toInt()
        val availableStorageMb = context.filesDir.usableSpace / (1024 * 1024)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            powerManager.currentThermalStatus
        } else {
            0 // PowerManager.THERMAL_STATUS_NONE
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && batteryManager != null) {
            batteryManager.isCharging
        } else false

        return DynamicDeviceState(
            availableRamMb = availableRamMb,
            isLowMemory = memoryInfo.lowMemory,
            lowMemoryThresholdMb = lowMemoryThresholdMb,
            availableStorageMb = availableStorageMb,
            thermalState = thermalStatus,
            batteryLevelPercent = batteryLevel,
            isCharging = isCharging
        )
    }

    fun evaluateDeviceProfile(): DeviceProfile {
        return DeviceProfile(
            staticInfo = getStaticProfile(),
            dynamicState = getDynamicState()
        )
    }

    fun classifyCompatibility(profile: DeviceProfile, minRamMb: Int = 2048): CompatibilityReport {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (profile.primaryAbi.contains("arm64") || profile.primaryAbi.contains("x86_64")) {
            reasons.add("64-bit architecture (${profile.primaryAbi}) supported.")
        } else {
            warnings.add("32-bit CPU ABI (${profile.primaryAbi}) may suffer reduced performance or OOMs.")
        }

        if (profile.totalRamMb >= minRamMb) {
            reasons.add("Total RAM (${profile.totalRamMb / 1024} GB) meets requirement.")
        } else {
            warnings.add("Total RAM (${profile.totalRamMb} MB) is below recommended ${minRamMb} MB.")
        }

        if (profile.availableRamMb < 1024) {
            warnings.add("Low available RAM (${profile.availableRamMb} MB). Close background applications.")
        }

        if (profile.availableStorageMb < 2048) {
            warnings.add("Storage space is tight (${profile.availableStorageMb} MB free).")
        }

        val level = when {
            profile.totalRamMb < 1500 || profile.availableRamMb < 400 -> CompatibilityLevel.UNSUPPORTED
            profile.totalRamMb < minRamMb || profile.cpuCores < 4 -> CompatibilityLevel.NOT_RECOMMENDED
            warnings.isNotEmpty() -> CompatibilityLevel.COMPATIBLE
            else -> CompatibilityLevel.RECOMMENDED
        }

        val rtfRecommendation = when (level) {
            CompatibilityLevel.RECOMMENDED -> "Fast (~0.3–0.6x RTF)"
            CompatibilityLevel.COMPATIBLE -> "Moderate (~0.7–1.2x RTF)"
            CompatibilityLevel.HEAVY -> "Heavy (~1.2–2.0x RTF)"
            CompatibilityLevel.NOT_RECOMMENDED -> "Slow (~1.5–3.0x RTF)"
            CompatibilityLevel.UNSUPPORTED -> "Unusable / High OOM Risk"
        }

        val summary = "${profile.staticInfo.manufacturer} ${profile.staticInfo.model} • ${profile.totalRamMb / 1024} GB RAM • ${profile.cpuCores} Cores"

        return CompatibilityReport(
            level = level,
            recommendedRtf = rtfRecommendation,
            summary = summary,
            reasons = reasons,
            warnings = warnings
        )
    }
}
