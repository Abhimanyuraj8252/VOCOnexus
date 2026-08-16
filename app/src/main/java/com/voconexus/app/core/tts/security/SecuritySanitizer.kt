package com.voconexus.app.core.tts.security

import com.voconexus.app.core.tts.TtsEngineException
import java.io.File

object SecuritySanitizer {

    private const val MAX_DECOMPRESSED_SIZE_BYTES = 4L * 1024L * 1024L * 1024L // 4 GB safety ceiling

    fun validateZipEntryPath(entryName: String, destinationDirectory: File): File {
        if (entryName.contains("../") || entryName.contains("..\\") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw TtsEngineException.PathTraversalDetectedException(entryName)
        }

        val targetFile = File(destinationDirectory, entryName)
        val canonicalDest = destinationDirectory.canonicalPath
        val canonicalTarget = targetFile.canonicalPath

        if (!canonicalTarget.startsWith(canonicalDest)) {
            throw TtsEngineException.PathTraversalDetectedException(entryName)
        }

        return targetFile
    }

    fun checkDecompressedBounds(currentExtractedBytes: Long, additionalBytes: Long) {
        if (currentExtractedBytes + additionalBytes > MAX_DECOMPRESSED_SIZE_BYTES) {
            throw IllegalStateException("Decompressed archive size exceeds maximum safety limit (4 GB). Potential Zip Bomb detected.")
        }
    }
}
