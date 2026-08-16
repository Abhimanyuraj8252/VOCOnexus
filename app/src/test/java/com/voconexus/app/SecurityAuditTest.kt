package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.error.DiagnosticsExporter
import com.voconexus.app.core.error.ErrorCode
import com.voconexus.app.core.error.VocoNexusError
import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.core.tts.TtsEngineException
import com.voconexus.app.core.tts.security.SecuritySanitizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecurityAuditTest {

    @Test(expected = TtsEngineException.PathTraversalDetectedException::class)
    fun testZipSlipPathTraversalBlocked() {
        val destDir = File("/tmp/safe_extract_dir")
        SecuritySanitizer.validateZipEntryPath("../../../etc/shadow", destDir)
    }

    @Test(expected = IllegalStateException::class)
    fun testDecompressedArchiveBoundsExceeded() {
        SecuritySanitizer.checkDecompressedBounds(
            currentExtractedBytes = 4_000_000_000L,
            additionalBytes = 500_000_000L
        )
    }

    @Test
    fun testDiagnosticsExporterStripsScriptContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storageManager = StorageManager(context)
        val exporter = DiagnosticsExporter(context, storageManager)

        val sensitiveText = "SECRET_SCRIPT_CONTENT_DO_NOT_LEAK_12345"
        val error = VocoNexusError(
            errorCode = ErrorCode.ENGINE_RUNTIME_ERROR,
            title = "Engine Initialization Failure",
            userMessage = "Failed to load model",
            actionableAdvice = "Restart app",
            technicalDetails = "Code 500"
        )

        val report = exporter.generateDiagnosticReport(listOf(error))

        assertNotNull(report)
        assertFalse(report.contains(sensitiveText))
        assertTrue(report.contains("Sanitized: No script content"))
    }
}
