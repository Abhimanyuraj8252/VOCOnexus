package com.voconexus.app.core.generation.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioValidationResult(
    val isValid: Boolean,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

class AudioValidator {

    fun validateWavFile(file: File, expectedSampleRate: Int = 24000): AudioValidationResult {
        if (!file.exists()) {
            return AudioValidationResult(isValid = false, errorMessage = "Audio file does not exist")
        }

        if (!file.canRead()) {
            return AudioValidationResult(isValid = false, errorMessage = "Audio file is not readable")
        }

        val fileSize = file.length()
        if (fileSize <= 44) {
            return AudioValidationResult(isValid = false, errorMessage = "Audio file is empty or truncated (size <= 44 bytes)")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                val read = raf.read(header)
                if (read < 44) {
                    return AudioValidationResult(isValid = false, errorMessage = "Failed to read 44-byte WAV header")
                }

                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

                val riffMarker = String(header, 0, 4, Charsets.US_ASCII)
                val waveMarker = String(header, 8, 4, Charsets.US_ASCII)
                val fmtMarker = String(header, 12, 4, Charsets.US_ASCII)

                if (riffMarker != "RIFF" || waveMarker != "WAVE" || fmtMarker != "fmt ") {
                    return AudioValidationResult(isValid = false, errorMessage = "Invalid WAV header magic identifiers")
                }

                val audioFormat = buffer.getShort(20).toInt()
                val channels = buffer.getShort(22).toInt()
                val sampleRate = buffer.getInt(24)
                val bitsPerSample = buffer.getShort(34).toInt()

                if (audioFormat != 1) {
                    return AudioValidationResult(isValid = false, errorMessage = "Unsupported audio format encoding: $audioFormat (expected PCM = 1)")
                }

                if (channels <= 0) {
                    return AudioValidationResult(isValid = false, errorMessage = "Invalid channel count: $channels")
                }

                if (sampleRate <= 0) {
                    return AudioValidationResult(isValid = false, errorMessage = "Invalid sample rate: $sampleRate")
                }

                val dataSize = fileSize - 44
                val bytesPerSample = (channels * bitsPerSample) / 8
                val numSamples = if (bytesPerSample > 0) dataSize / bytesPerSample else 0
                val durationMs = (numSamples * 1000L) / sampleRate

                if (durationMs <= 0) {
                    return AudioValidationResult(isValid = false, errorMessage = "Audio duration is 0ms")
                }

                AudioValidationResult(
                    isValid = true,
                    sampleRate = sampleRate,
                    channels = channels,
                    durationMs = durationMs,
                    fileSizeBytes = fileSize
                )
            }
        } catch (e: Exception) {
            AudioValidationResult(isValid = false, errorMessage = e.message ?: "Failed to parse audio file")
        }
    }
}
