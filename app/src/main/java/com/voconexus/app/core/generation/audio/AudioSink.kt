package com.voconexus.app.core.generation.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface AudioSink {
    fun open(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int = 16)
    fun writePcm(pcmData: ByteArray)
    fun flushAndClose(): Long
    fun cancel()
}

class WavAudioSink : AudioSink {

    private var raf: RandomAccessFile? = null
    private var targetFile: File? = null
    private var sampleRate: Int = 24000
    private var channels: Int = 1
    private var bitsPerSample: Int = 16
    private var totalBytesWritten: Long = 0L

    override fun open(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        this.targetFile = file
        this.sampleRate = sampleRate
        this.channels = channels
        this.bitsPerSample = bitsPerSample
        this.totalBytesWritten = 0L

        file.parentFile?.mkdirs()
        if (file.exists()) {
            file.delete()
        }

        val accessFile = RandomAccessFile(file, "rw")
        this.raf = accessFile

        // Write initial placeholder 44-byte WAV header
        val headerPlaceholder = ByteArray(44)
        accessFile.write(headerPlaceholder)
    }

    override fun writePcm(pcmData: ByteArray) {
        val accessFile = raf ?: throw IllegalStateException("AudioSink is not open")
        accessFile.write(pcmData)
        totalBytesWritten += pcmData.size
    }

    override fun flushAndClose(): Long {
        val accessFile = raf ?: return 0L
        try {
            // Seek to 0 and write real 44-byte WAV header
            accessFile.seek(0)
            val header = createWavHeader(totalBytesWritten, sampleRate, channels, bitsPerSample)
            accessFile.write(header)
            accessFile.close()

            val bytesPerSample = (channels * bitsPerSample) / 8
            val numSamples = totalBytesWritten / bytesPerSample
            val durationMs = (numSamples * 1000L) / sampleRate

            return durationMs
        } finally {
            raf = null
        }
    }

    override fun cancel() {
        try {
            raf?.close()
        } catch (_: Exception) {
        } finally {
            raf = null
            targetFile?.delete()
        }
    }

    private fun createWavHeader(pcmDataLen: Long, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcmDataLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat 1 = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * (bitsPerSample / 8)).toShort()) // BlockAlign
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataLen.toInt())
        }

        return header.array()
    }
}
