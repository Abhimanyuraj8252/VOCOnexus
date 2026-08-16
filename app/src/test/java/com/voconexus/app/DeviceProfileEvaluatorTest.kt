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
class DeviceProfileEvaluatorTest {

    @Test
    fun testEvaluateDeviceProfile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val evaluator = DeviceProfileEvaluator(context)

        val profile = evaluator.evaluateDeviceProfile()

        assertNotNull(profile)
        assertNotNull(profile.staticInfo)
        assertNotNull(profile.dynamicState)
    }

    @Test
    fun testClassifyCompatibilityRecommended() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val evaluator = DeviceProfileEvaluator(context)

        val profile = DeviceProfile(
            staticInfo = StaticDeviceProfile(
                manufacturer = "Google",
                model = "Pixel 8 Pro",
                androidVersion = "14",
                sdkVersion = 34,
                primaryAbi = "arm64-v8a",
                supportedAbis = listOf("arm64-v8a"),
                totalRamMb = 12288,
                cpuCores = 8
            ),
            dynamicState = DynamicDeviceState(
                availableRamMb = 6144,
                isLowMemory = false,
                lowMemoryThresholdMb = 512,
                availableStorageMb = 65536,
                thermalState = 0,
                batteryLevelPercent = 90,
                isCharging = true
            )
        )

        val report = evaluator.classifyCompatibility(profile, minRamMb = 2048)

        assertEquals(CompatibilityLevel.RECOMMENDED, report.level)
    }
}
