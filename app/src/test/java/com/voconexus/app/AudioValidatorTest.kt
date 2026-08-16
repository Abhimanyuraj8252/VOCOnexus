package com.voconexus.app

import com.voconexus.app.core.generation.audio.AudioValidator
import com.voconexus.app.core.generation.audio.WavAudioSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AudioValidatorTest {

    private val validator = AudioValidator()
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(System.getProperty("java.io.tmpdir"), "audio_val_test_${System.currentTimeMillis()}").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun testNonExistentAndTruncatedFileValidation() {
        val nonExistent = File(testDir, "missing.wav")
        val result1 = validator.validateWavFile(nonExistent)
        assertFalse(result1.isValid)
        assertEquals("Audio file does not exist", result1.errorMessage)

        val truncated = File(testDir, "truncated.wav").also { it.writeText("RIFF_TINY") }
        val result2 = validator.validateWavFile(truncated)
        assertFalse(result2.isValid)
        assertTrue(result2.errorMessage!!.contains("size <= 44 bytes"))
    }

    @Test
    fun testValidWavFileValidation() {
        val wavFile = File(testDir, "valid.wav")
        val sink = WavAudioSink()
        sink.open(wavFile, 24000, 1)

        // Write 1 second of 16-bit PCM silence (24,000 samples * 2 bytes = 48,000 bytes)
        val pcm = ByteArray(48000)
        sink.writePcm(pcm)
        val durationMs = sink.flushAndClose()

        assertTrue(durationMs > 0)
        val result = validator.validateWavFile(wavFile)
        assertTrue(result.isValid)
        assertEquals(24000, result.sampleRate)
        assertEquals(1, result.channels)
        assertEquals(1000L, result.durationMs)
        assertEquals(48044L, result.fileSizeBytes)
    }
}
