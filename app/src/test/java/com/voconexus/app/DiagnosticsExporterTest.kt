package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.error.DiagnosticsExporter
import com.voconexus.app.core.error.ErrorCode
import com.voconexus.app.core.error.VocoNexusError
import com.voconexus.app.core.storage.StorageManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticsExporterTest {

    private lateinit var diagnosticsExporter: DiagnosticsExporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storageManager = StorageManager(context)
        diagnosticsExporter = DiagnosticsExporter(context, storageManager)
    }

    @Test
    fun testGenerateDiagnosticReport() {
        val errors = listOf(
            VocoNexusError(
                errorCode = ErrorCode.TTS_MODEL_LOAD_FAILED,
                title = "Test Failure",
                userMessage = "Model failed to initialize",
                actionableAdvice = "Redownload model",
                technicalDetails = "Code 500"
            )
        )

        val report = diagnosticsExporter.generateDiagnosticReport(errors)

        assertNotNull(report)
        assertTrue(report.contains("VIRTUAL DIAGNOSTIC REPORT"))
        assertTrue(report.contains("TTS_MODEL_LOAD_FAILED"))
        assertTrue(report.contains("Sanitized: No script content"))
    }
}
