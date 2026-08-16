package com.voconexus.app.core.dsp

import com.voconexus.app.core.export.AudioCombiner
import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.generation.audio.WavAudioSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class WsolaAudioProcessor(
    private val combiner: AudioCombiner = AudioCombiner(),
    private val audioValidator: AudioValidator = AudioValidator()
) : AudioProcessor {

    override suspend fun process(
        sourceFile: File,
        outputFile: File,
        parameters: DspParameters,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() < 44) return@withContext false

        val header = combiner.parseWavHeader(sourceFile) ?: return@withContext false
        val ratio = parameters.timeStretchRatio.coerceIn(0.5f, 2.5f)
        val pitchSemis = parameters.pitchShiftSemitones.coerceIn(-12.0f, 12.0f)

        // If identity transformation, copy file directly
        if (Math.abs(ratio - 1.0f) < 0.001f && Math.abs(pitchSemis) < 0.01f) {
            sourceFile.copyTo(outputFile, overwrite = true)
            onProgress(1.0f)
            return@withContext true
        }

        val tempOutputFile = File(outputFile.parentFile, "${outputFile.name}.dsp_tmp")

        try {
            // Read 16-bit PCM samples
            val pcmBytes = ByteArray(header.pcmDataLength.toInt())
            RandomAccessFile(sourceFile, "r").use { raf ->
                raf.seek(header.pcmDataOffset.toLong())
                raf.readFully(pcmBytes)
            }

            val numSamples = pcmBytes.size / 2
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val b1 = pcmBytes[i * 2].toInt() and 0xFF
                val b2 = pcmBytes[i * 2 + 1].toInt() and 0xFF
                samples[i] = ((b2 shl 8) or b1).toShort()
            }

            // Calculate effective stretch & resampling ratios for pitch shift
            val pitchRatio = Math.pow(2.0, (pitchSemis / 12.0).toDouble()).toFloat()
            val effectiveTimeStretch = ratio / pitchRatio

            // Step 1: WSOLA Time Stretch
            val stretchedSamples = wsolaTimeStretch(samples, effectiveTimeStretch, onProgress)

            // Step 2: Resample for Pitch Shift (if pitch shift requested)
            val finalSamples = if (Math.abs(pitchRatio - 1.0f) >= 0.01f) {
                linearResample(stretchedSamples, pitchRatio)
            } else {
                stretchedSamples
            }

            // Convert shorts back to 16-bit PCM bytes
            val outBytes = ByteArray(finalSamples.size * 2)
            for (i in finalSamples.indices) {
                val s = finalSamples[i].toInt()
                outBytes[i * 2] = (s and 0xFF).toByte()
                outBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }

            val sink = WavAudioSink()
            sink.open(tempOutputFile, header.sampleRate, header.channels)
            sink.writePcm(outBytes)
            sink.flushAndClose()

            // Validate temp output
            val valResult = audioValidator.validateWavFile(tempOutputFile)
            if (!valResult.isValid) {
                tempOutputFile.delete()
                return@withContext false
            }

            // Safe Atomic Commit
            if (outputFile.exists()) {
                outputFile.delete()
            }
            tempOutputFile.renameTo(outputFile)
            onProgress(1.0f)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext false
        }
    }

    private fun wsolaTimeStretch(
        input: ShortArray,
        stretchFactor: Float,
        onProgress: (Float) -> Unit
    ): ShortArray {
        val windowSize = 512
        val synthesisStep = 256
        val analysisStep = (synthesisStep * stretchFactor).toInt().coerceAtLeast(64)
        val searchRange = 128

        val outputCapacity = (input.size / stretchFactor).toInt() + windowSize * 2
        val output = ShortArray(outputCapacity)
        val hanning = FloatArray(windowSize) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (windowSize - 1)))).toFloat()
        }

        var inputPos = 0
        var outputPos = 0

        while (inputPos + windowSize + searchRange < input.size) {
            // Find best matching overlap alignment using cross-correlation
            var bestOffset = 0
            var maxCorr = Long.MIN_VALUE

            for (offset in -searchRange..searchRange) {
                val candidatePos = inputPos + offset
                if (candidatePos < 0 || candidatePos + windowSize >= input.size) continue

                var corr = 0L
                for (j in 0 until 128 step 4) {
                    val s1 = input[candidatePos + j].toLong()
                    val s2 = output[(outputPos - windowSize + j).coerceAtLeast(0)].toLong()
                    corr += s1 * s2
                }
                if (corr > maxCorr) {
                    maxCorr = corr
                    bestOffset = offset
                }
            }

            val alignedPos = inputPos + bestOffset

            // Overlap-Add windowing
            for (j in 0 until windowSize) {
                val inVal = (input[alignedPos + j] * hanning[j]).toInt()
                val targetIndex = outputPos + j
                if (targetIndex < output.size) {
                    output[targetIndex] = (output[targetIndex] + inVal).coerceIn(-32768, 32767).toShort()
                }
            }

            inputPos += analysisStep
            outputPos += synthesisStep

            if (inputPos % 4096 == 0 && input.size > 0) {
                onProgress((inputPos.toFloat() / input.size.toFloat()).coerceIn(0f, 0.9f))
            }
        }

        return output.copyOf(outputPos)
    }

    private fun linearResample(input: ShortArray, speedFactor: Float): ShortArray {
        val newLength = (input.size / speedFactor).toInt().coerceAtLeast(1)
        val output = ShortArray(newLength)

        for (i in 0 until newLength) {
            val srcPos = i * speedFactor
            val index = srcPos.toInt()
            val frac = srcPos - index

            if (index + 1 < input.size) {
                val sample1 = input[index]
                val sample2 = input[index + 1]
                val interpolated = sample1 + frac * (sample2 - sample1)
                output[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
            } else if (index < input.size) {
                output[i] = input[index]
            }
        }
        return output
    }
}
