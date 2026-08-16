package com.voconexus.app

import com.voconexus.app.core.tts.TtsEngineException
import com.voconexus.app.core.tts.security.SecuritySanitizer
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class SecuritySanitizerTest {

    @Test(expected = TtsEngineException.PathTraversalDetectedException::class)
    fun testZipSlipPathTraversalDetection() {
        val destDir = File("/tmp/target_extract_dir")
        SecuritySanitizer.validateZipEntryPath("../../etc/passwd", destDir)
    }

    @Test
    fun testValidZipEntryPath() {
        val destDir = File("/tmp/target_extract_dir")
        val validatedFile = SecuritySanitizer.validateZipEntryPath("models/sub/file.onnx", destDir)
        assertNotNull(validatedFile)
    }

    @Test(expected = IllegalStateException::class)
    fun testDecompressedBoundsExceeded() {
        SecuritySanitizer.checkDecompressedBounds(
            currentExtractedBytes = 4_000_000_000L,
            additionalBytes = 500_000_000L
        )
    }
}
