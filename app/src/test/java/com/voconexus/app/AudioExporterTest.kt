package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.export.AudioExporter
import com.voconexus.app.core.storage.AudioStorageManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioExporterTest {

    private lateinit var exporter: AudioExporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storageManager = AudioStorageManager(context)
        exporter = AudioExporter(context, storageManager)
    }

    @Test
    fun testFilenameSanitization() {
        val rawName = "VocoNexus / Audio : Test * File ?.wav"
        val sanitized = exporter.sanitizeFilename(rawName)
        assertEquals("VocoNexus _ Audio _ Test _ File _.wav", sanitized)
    }
}
