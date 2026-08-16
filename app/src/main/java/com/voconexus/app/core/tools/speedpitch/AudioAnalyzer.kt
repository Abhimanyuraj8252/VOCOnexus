package com.voconexus.app.core.tools.speedpitch

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Extracts a normalized amplitude array from an audio file for waveform visualization.
 * Returns a FloatArray of size [targetSamples] with values in [0.0, 1.0].
 */
object AudioAnalyzer {

    suspend fun extractWaveform(
        context: Context,
        uri: Uri,
        targetSamples: Int = 500
    ): FloatArray = withContext(Dispatchers.Default) {
        try {
            extractWaveformInternal(context, uri, targetSamples)
        } catch (_: Throwable) {
            FloatArray(targetSamples) { 0.1f + (it % 5) * 0.04f } // fallback pattern
        }
    }

    private fun extractWaveformInternal(
        context: Context,
        uri: Uri,
        targetSamples: Int
    ): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (_: Throwable) {
            return FloatArray(targetSamples) { 0.1f + (it % 5) * 0.04f }
        }

        // Find the first audio track
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            try {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            } catch (_: Throwable) {}
        }

        if (audioTrackIndex < 0 || audioFormat == null) {
            try { extractor.release() } catch (_: Throwable) {}
            return FloatArray(targetSamples) { 0.1f }
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Throwable) {
            try { extractor.release() } catch (_: Throwable) {}
            return FloatArray(targetSamples) { 0.1f + (it % 5) * 0.04f }
        }

        try {
            codec.configure(audioFormat, null, null, 0)
            codec.start()
        } catch (_: Throwable) {
            try { codec.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
            return FloatArray(targetSamples) { 0.1f + (it % 5) * 0.04f }
        }

        val durationUs = try { audioFormat.getLong(MediaFormat.KEY_DURATION) } catch (_: Throwable) { 0L }
        val chunkDurationUs = if (durationUs > 0) durationUs / targetSamples else 1000L
        val amplitudes = FloatArray(targetSamples)
        val chunkSamples = mutableListOf<Float>()
        var currentChunk = 0

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var presentationTimeUs = 0L

        try {
            while (!outputDone && currentChunk < targetSamples) {
                // Feed input
                if (!inputDone) {
                    val inputBufferId = codec.dequeueInputBuffer(1000L)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain output
                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 1000L)
                if (outputBufferId >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    val outputBuffer = codec.getOutputBuffer(outputBufferId)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val shortBuffer = outputBuffer.asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            val sample = abs(shortBuffer.get().toInt()) / 32768f
                            chunkSamples.add(sample)
                        }

                        // Flush completed chunks
                        val targetChunkEnd = (currentChunk + 1) * chunkDurationUs
                        while (bufferInfo.presentationTimeUs >= targetChunkEnd && currentChunk < targetSamples) {
                            amplitudes[currentChunk] = if (chunkSamples.isNotEmpty()) {
                                rms(chunkSamples)
                            } else 0f
                            chunkSamples.clear()
                            currentChunk++
                            if (currentChunk >= targetSamples) break
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                }
            }
        } catch (_: Exception) {
            // Partial result is fine
        } finally {
            // Fill any remaining chunks
            if (currentChunk < targetSamples && chunkSamples.isNotEmpty()) {
                amplitudes[currentChunk] = rms(chunkSamples)
                currentChunk++
            }
            for (i in currentChunk until targetSamples) {
                amplitudes[i] = amplitudes.take(currentChunk).average().toFloat().coerceAtLeast(0.05f)
            }
            try { codec.stop(); codec.release() } catch (_: Exception) {}
            extractor.release()
        }

        // Normalize to [0.05, 1.0]
        val max = amplitudes.max().coerceAtLeast(0.001f)
        return FloatArray(targetSamples) { i -> (amplitudes[i] / max).coerceIn(0.02f, 1f) }
    }

    private fun rms(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sum = samples.sumOf { (it * it).toDouble() }
        return sqrt(sum / samples.size).toFloat()
    }
}
