package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.storage.StorageManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StorageManagerTest {

    @Test
    fun testGetStorageBreakdown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = StorageManager(context)

        val breakdown = manager.getStorageBreakdown()

        assertNotNull(breakdown)
        assertTrue(breakdown.freeSystemStorageBytes >= 0L)
    }

    @Test
    fun testAudioExportSizeEstimation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = StorageManager(context)

        // 100 seconds at 192kbps = 100 * (192,000 / 8) = 2,400,000 bytes
        val estimatedBytes = manager.estimateAudioExportSize(durationSeconds = 100, bitrateKbps = 192)

        assertTrue(estimatedBytes > 2_000_000L)
    }
}
