package com.voconexus.app

import com.voconexus.app.core.dsp.DspParameters
import com.voconexus.app.core.dsp.WsolaAudioProcessor
import com.voconexus.app.core.export.AudioCombiner
import com.voconexus.app.core.generation.audio.WavAudioSink
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WsolaAudioProcessorTest {

    private val processor = WsolaAudioProcessor()
    private val combiner = AudioCombiner()
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(System.getProperty("java.io.tmpdir"), "wsola_test_${System.currentTimeMillis()}").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun testWsolaTimeStretchAndPitchShift() {
        runBlocking {
            val sourceWav = File(testDir, "source.wav")
            val outputWav = File(testDir, "processed.wav")

            // Synthesize 1 sec 24kHz mono 16-bit PCM WAV (48000 bytes)
            val sink = WavAudioSink()
            sink.open(sourceWav, 24000, 1)
            sink.writePcm(ByteArray(48000))
            sink.flushAndClose()

            // 1. Time Stretch 1.25x (Faster -> ~0.8s output)
            val params = DspParameters(timeStretchRatio = 1.25f, pitchShiftSemitones = 2.0f)
            val success = processor.process(sourceWav, outputWav, params)

            assertTrue(success)
            assertTrue(outputWav.exists())

            val header = combiner.parseWavHeader(outputWav)
            assertNotNull(header)
            assertEquals(24000, header?.sampleRate)
            assertEquals(1, header?.channels)
        }
    }
}
