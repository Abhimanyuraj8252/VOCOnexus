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
        if (fileSize <= 500) {
            return AudioValidationResult(isValid = false, errorMessage = "Audio file is empty or truncated (size <= 500 bytes)")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                val read = raf.read(header)
                if (read >= 44) {
                    val riffMarker = String(header, 0, 4, Charsets.US_ASCII)
                    val waveMarker = String(header, 8, 4, Charsets.US_ASCII)

                    if (riffMarker == "RIFF" && waveMarker == "WAVE") {
                        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        val channels = buffer.getShort(22).toInt().coerceAtLeast(1)
                        val sampleRate = buffer.getInt(24).coerceAtLeast(8000)
                        val durationMs = ((fileSize - 44) * 1000L) / (sampleRate * channels * 2)
                        
                        if (durationMs < 100L) {
                            return AudioValidationResult(isValid = false, errorMessage = "Audio file duration is too short (< 100ms)")
                        }
                        
                        return AudioValidationResult(
                            isValid = true,
                            sampleRate = sampleRate,
                            channels = channels,
                            durationMs = durationMs,
                            fileSizeBytes = fileSize
                        )
                    }
                }

                // If non-WAV / MP3 audio stream with valid audio content
                if (fileSize > 1000) {
                    val approxDurationMs = (fileSize * 8 * 1000L) / 48000L
                    if (approxDurationMs >= 100L) {
                        return AudioValidationResult(
                            isValid = true,
                            sampleRate = expectedSampleRate,
                            channels = 1,
                            durationMs = approxDurationMs,
                            fileSizeBytes = fileSize
                        )
                    }
                }

                AudioValidationResult(isValid = false, errorMessage = "Unsupported audio file header or empty audio stream")
            }
        } catch (e: Exception) {
            AudioValidationResult(isValid = false, errorMessage = "Validation Exception: ${e.message}")
        }
    }
}
