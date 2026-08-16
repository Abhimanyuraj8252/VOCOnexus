package com.voconexus.app.core.export

import com.voconexus.app.core.generation.audio.WavAudioSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

class AudioCombiner {

    data class WavHeaderInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val pcmDataOffset: Int,
        val pcmDataLength: Long
    )

    suspend fun combineWavFiles(
        sourceFiles: List<File>,
        targetOutputFile: File,
        onProgress: (progressFraction: Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (sourceFiles.isEmpty()) return@withContext false

        // Temporary output file
        val tempOutputFile = File(targetOutputFile.parentFile, "${targetOutputFile.name}.tmp")
        if (tempOutputFile.exists()) {
            tempOutputFile.delete()
        }

        try {
            val headers = sourceFiles.map { file ->
                parseWavHeader(file) ?: return@withContext false
            }

            // Verify Format Compatibility
            val firstHeader = headers.first()
            for (header in headers) {
                if (header.sampleRate != firstHeader.sampleRate ||
                    header.channels != firstHeader.channels ||
                    header.bitsPerSample != firstHeader.bitsPerSample
                ) {
                    // Formats incompatible for direct lossless PCM concatenation
                    return@withContext false
                }
            }

            val sink = WavAudioSink()
            sink.open(tempOutputFile, firstHeader.sampleRate, firstHeader.channels)

            val totalPcmBytes = headers.sumOf { it.pcmDataLength }
            var bytesCopiedTotal = 0L

            val buffer = ByteArray(8192)

            for ((index, sourceFile) in sourceFiles.withIndex()) {
                val header = headers[index]
                FileInputStream(sourceFile).use { fis ->
                    fis.skip(header.pcmDataOffset.toLong())
                    var remaining = header.pcmDataLength

                    while (remaining > 0) {
                        val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                        val read = fis.read(buffer, 0, toRead)
                        if (read <= 0) break

                        val chunkBytes = buffer.copyOf(read)
                        sink.writePcm(chunkBytes)
                        bytesCopiedTotal += read
                        remaining -= read

                        if (totalPcmBytes > 0) {
                            onProgress(bytesCopiedTotal.toFloat() / totalPcmBytes.toFloat())
                        }
                    }
                }
            }

            sink.flushAndClose()

            if (!tempOutputFile.exists() || tempOutputFile.length() <= 44) {
                tempOutputFile.delete()
                return@withContext false
            }

            if (targetOutputFile.exists()) {
                targetOutputFile.delete()
            }

            val renamed = tempOutputFile.renameTo(targetOutputFile)
            if (!renamed) {
                tempOutputFile.copyTo(targetOutputFile, overwrite = true)
                tempOutputFile.delete()
            }

            return@withContext targetOutputFile.exists() && targetOutputFile.length() > 44
        } catch (e: Exception) {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            return@withContext false
        }
    }

    fun parseWavHeader(file: File): WavHeaderInfo? {
        if (!file.exists() || file.length() < 44) return null

        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                raf.readFully(header)

                val riffMagic = String(header, 0, 4)
                val waveMagic = String(header, 8, 4)
                val fmtMagic = String(header, 12, 4)

                if (riffMagic != "RIFF" || waveMagic != "WAVE" || fmtMagic != "fmt ") return null

                val channels = (header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8)
                val sampleRate = (header[24].toInt() and 0xFF) or
                        ((header[25].toInt() and 0xFF) shl 8) or
                        ((header[26].toInt() and 0xFF) shl 16) or
                        ((header[27].toInt() and 0xFF) shl 24)

                val bitsPerSample = (header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8)

                // Seek for "data" chunk
                var pcmOffset = 36
                raf.seek(36)
                val subChunkHeader = ByteArray(8)
                while (raf.filePointer < file.length()) {
                    val read = raf.read(subChunkHeader)
                    if (read < 8) break
                    val subChunkId = String(subChunkHeader, 0, 4)
                    val subChunkSize = (subChunkHeader[4].toInt() and 0xFF) or
                            ((subChunkHeader[5].toInt() and 0xFF) shl 8) or
                            ((subChunkHeader[6].toInt() and 0xFF) shl 16) or
                            ((subChunkHeader[7].toInt() and 0xFF) shl 24)

                    if (subChunkId == "data") {
                        pcmOffset = raf.filePointer.toInt()
                        val pcmLength = Math.min(subChunkSize.toLong(), file.length() - pcmOffset)
                        return WavHeaderInfo(
                            sampleRate = sampleRate,
                            channels = channels,
                            bitsPerSample = bitsPerSample,
                            pcmDataOffset = pcmOffset,
                            pcmDataLength = pcmLength
                        )
                    } else {
                        raf.seek(raf.filePointer + subChunkSize)
                    }
                }

                // Fallback default
                return WavHeaderInfo(
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    pcmDataOffset = 44,
                    pcmDataLength = file.length() - 44
                )
            }
        } catch (_: Exception) {
            return null
        }
    }
}
