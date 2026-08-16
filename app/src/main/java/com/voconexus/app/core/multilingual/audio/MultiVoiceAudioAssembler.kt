package com.voconexus.app.core.multilingual.audio

import com.voconexus.app.core.export.AudioCombiner
import com.voconexus.app.core.generation.audio.WavAudioSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class MultiVoiceAudioAssembler(
    private val combiner: AudioCombiner = AudioCombiner()
) {

    suspend fun assembleSegments(
        segmentFiles: List<File>,
        targetOutputFile: File,
        targetSampleRate: Int = 24000,
        targetChannels: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        if (segmentFiles.isEmpty()) return@withContext false

        val tempFile = File(targetOutputFile.parentFile, "${targetOutputFile.name}.asm_tmp")

        try {
            val sink = WavAudioSink()
            sink.open(tempFile, targetSampleRate, targetChannels)

            for (file in segmentFiles) {
                if (!file.exists() || file.length() < 44) continue

                val header = combiner.parseWavHeader(file) ?: continue
                val pcmBytes = ByteArray(header.pcmDataLength.toInt())

                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(header.pcmDataOffset.toLong())
                    raf.readFully(pcmBytes)
                }

                // 1. Channel layout conversion
                val normalizedChannelsBytes = convertChannels(
                    pcmBytes = pcmBytes,
                    srcChannels = header.channels,
                    dstChannels = targetChannels
                )

                // 2. Sample rate conversion
                val resampledBytes = convertSampleRate(
                    pcmBytes = normalizedChannelsBytes,
                    srcSampleRate = header.sampleRate,
                    dstSampleRate = targetSampleRate,
                    channels = targetChannels
                )

                sink.writePcm(resampledBytes)
            }

            sink.flushAndClose()

            if (targetOutputFile.exists()) {
                targetOutputFile.delete()
            }
            tempFile.renameTo(targetOutputFile)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            return@withContext false
        }
    }

    private fun convertChannels(pcmBytes: ByteArray, srcChannels: Int, dstChannels: Int): ByteArray {
        if (srcChannels == dstChannels) return pcmBytes

        val sampleCount = pcmBytes.size / (2 * srcChannels)
        if (srcChannels == 1 && dstChannels == 2) {
            // Mono -> Stereo
            val out = ByteArray(sampleCount * 4)
            for (i in 0 until sampleCount) {
                val b1 = pcmBytes[i * 2]
                val b2 = pcmBytes[i * 2 + 1]
                out[i * 4] = b1
                out[i * 4 + 1] = b2
                out[i * 4 + 2] = b1
                out[i * 4 + 3] = b2
            }
            return out
        } else if (srcChannels == 2 && dstChannels == 1) {
            // Stereo -> Mono
            val out = ByteArray(sampleCount * 2)
            for (i in 0 until sampleCount) {
                val l1 = pcmBytes[i * 4].toInt() and 0xFF
                val l2 = pcmBytes[i * 4 + 1].toInt() and 0xFF
                val s1 = ((l2 shl 8) or l1).toShort().toInt()

                val r1 = pcmBytes[i * 4 + 2].toInt() and 0xFF
                val r2 = pcmBytes[i * 4 + 3].toInt() and 0xFF
                val s2 = ((r2 shl 8) or r1).toShort().toInt()

                val avg = ((s1 + s2) / 2).coerceIn(-32768, 32767)
                out[i * 2] = (avg and 0xFF).toByte()
                out[i * 2 + 1] = ((avg shr 8) and 0xFF).toByte()
            }
            return out
        }
        return pcmBytes
    }

    private fun convertSampleRate(
        pcmBytes: ByteArray,
        srcSampleRate: Int,
        dstSampleRate: Int,
        channels: Int
    ): ByteArray {
        if (srcSampleRate == dstSampleRate) return pcmBytes

        val srcSampleCount = pcmBytes.size / (2 * channels)
        val ratio = dstSampleRate.toFloat() / srcSampleRate.toFloat()
        val dstSampleCount = (srcSampleCount * ratio).toInt().coerceAtLeast(1)

        val srcSamples = ShortArray(srcSampleCount * channels)
        for (i in 0 until srcSampleCount * channels) {
            val b1 = pcmBytes[i * 2].toInt() and 0xFF
            val b2 = pcmBytes[i * 2 + 1].toInt() and 0xFF
            srcSamples[i] = ((b2 shl 8) or b1).toShort()
        }

        val dstSamples = ShortArray(dstSampleCount * channels)
        for (i in 0 until dstSampleCount) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx

            for (c in 0 until channels) {
                val idx1 = (idx * channels + c).coerceIn(0, srcSamples.size - 1)
                val idx2 = ((idx + 1) * channels + c).coerceIn(0, srcSamples.size - 1)

                val s1 = srcSamples[idx1].toFloat()
                val s2 = srcSamples[idx2].toFloat()
                val interpolated = s1 + frac * (s2 - s1)
                dstSamples[i * channels + c] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
            }
        }

        val outBytes = ByteArray(dstSamples.size * 2)
        for (i in dstSamples.indices) {
            val s = dstSamples[i].toInt()
            outBytes[i * 2] = (s and 0xFF).toByte()
            outBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        return outBytes
    }
}
