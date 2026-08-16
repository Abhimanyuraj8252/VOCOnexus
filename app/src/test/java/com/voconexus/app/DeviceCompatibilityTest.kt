package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.tts.device.CompatibilityLevel
import com.voconexus.app.core.tts.device.DeviceProfile
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.device.DynamicDeviceState
import com.voconexus.app.core.tts.device.StaticDeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceCompatibilityTest {

    @Test
    fun testDeviceProfileEvaluation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val evaluator = DeviceProfileEvaluator(context)

        val profile = evaluator.evaluateDeviceProfile()
        assertNotNull(profile)

        val highEndProfile = DeviceProfile(
            staticInfo = StaticDeviceProfile(
                manufacturer = "Google",
                model = "Pixel 8 Pro",
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
                availableStorageMb = 50000,
                thermalState = 0,
                batteryLevelPercent = 90,
                isCharging = true
            )
        )

        val reportHigh = evaluator.classifyCompatibility(highEndProfile, minRamMb = 3072)
        assertEquals(CompatibilityLevel.RECOMMENDED, reportHigh.level)

        val lowEndProfile = DeviceProfile(
            staticInfo = StaticDeviceProfile(
                manufacturer = "Generic",
                model = "Low End",
                androidVersion = "8.1",
                sdkVersion = 26,
                primaryAbi = "armeabi-v7a",
                supportedAbis = listOf("armeabi-v7a"),
                totalRamMb = 1000,
                cpuCores = 2
            ),
            dynamicState = DynamicDeviceState(
                availableRamMb = 300,
                isLowMemory = true,
                lowMemoryThresholdMb = 256,
                availableStorageMb = 500,
                thermalState = 0,
                batteryLevelPercent = 20,
                isCharging = false
            )
        )

        val reportLow = evaluator.classifyCompatibility(lowEndProfile, minRamMb = 3072)
        assertEquals(CompatibilityLevel.UNSUPPORTED, reportLow.level)
    }
}
