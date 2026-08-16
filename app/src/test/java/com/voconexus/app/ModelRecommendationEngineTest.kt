package com.voconexus.app

import com.voconexus.app.core.tts.device.CompatibilityLevel
import com.voconexus.app.core.tts.device.DeviceProfile
import com.voconexus.app.core.tts.device.DynamicDeviceState
import com.voconexus.app.core.tts.device.ModelRecommendationEngine
import com.voconexus.app.core.tts.device.ModelRequirements
import com.voconexus.app.core.tts.device.StaticDeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRecommendationEngineTest {

    private val engine = ModelRecommendationEngine()

    @Test
    fun testEvaluateRecommendationForHighEndDevice() {
        val profile = DeviceProfile(
            staticInfo = StaticDeviceProfile(
                manufacturer = "Samsung",
                model = "Galaxy S24",
                androidVersion = "14",
                sdkVersion = 34,
                primaryAbi = "arm64-v8a",
                supportedAbis = listOf("arm64-v8a"),
                totalRamMb = 8192,
                cpuCores = 8
            ),
            dynamicState = DynamicDeviceState(
                availableRamMb = 4096,
                isLowMemory = false,
                lowMemoryThresholdMb = 512,
                availableStorageMb = 20480,
                thermalState = 0,
                batteryLevelPercent = 85,
                isCharging = false
            )
        )

        val kokoroReqs = ModelRequirements(
            modelId = "kokoro-82m-v1.0",
            modelName = "Kokoro 82M",
            minimumRamMb = 2048,
            recommendedRamMb = 4096,
            estimatedRuntimeMemoryMb = 600,
            modelFileSizeBytes = 340 * 1024 * 1024L
        )

        val rec = engine.evaluateRecommendation(profile, kokoroReqs)

        assertEquals(CompatibilityLevel.RECOMMENDED, rec.category)
        assertEquals(true, rec.isExecutableOnDevice)
    }

    @Test
    fun testEvaluateRecommendationUnsupportedAbi() {
        val profile = DeviceProfile(
            staticInfo = StaticDeviceProfile(
                manufacturer = "Generic",
                model = "Old Tablet",
                androidVersion = "8.1",
                sdkVersion = 27,
                primaryAbi = "armeabi-v7a",
                supportedAbis = listOf("armeabi-v7a"),
                totalRamMb = 2048,
                cpuCores = 4
            ),
            dynamicState = DynamicDeviceState(
                availableRamMb = 1024,
                isLowMemory = false,
                lowMemoryThresholdMb = 256,
                availableStorageMb = 5120,
                thermalState = 0,
                batteryLevelPercent = 50,
                isCharging = false
            )
        )

        val kokoroReqs = ModelRequirements(
            modelId = "kokoro-82m-v1.0",
            modelName = "Kokoro 82M",
            minimumRamMb = 2048,
            recommendedRamMb = 4096,
            estimatedRuntimeMemoryMb = 600,
            modelFileSizeBytes = 340 * 1024 * 1024L,
            requiredAbi = "arm64-v8a"
        )

        val rec = engine.evaluateRecommendation(profile, kokoroReqs)

        assertEquals(CompatibilityLevel.UNSUPPORTED, rec.category)
        assertEquals(false, rec.isExecutableOnDevice)
    }
}
