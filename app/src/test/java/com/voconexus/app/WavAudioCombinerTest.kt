package com.voconexus.app

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

class WavAudioCombinerTest {

    private val combiner = AudioCombiner()
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(System.getProperty("java.io.tmpdir"), "wav_combiner_${System.currentTimeMillis()}").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun testDirectPCMConcatenation() {
        runBlocking {
            val file1 = File(testDir, "chunk1.wav")
            val file2 = File(testDir, "chunk2.wav")

            val sink1 = WavAudioSink()
            sink1.open(file1, 24000, 1)
            sink1.writePcm(ByteArray(48000)) // 1 sec PCM silence
            sink1.flushAndClose()

            val sink2 = WavAudioSink()
            sink2.open(file2, 24000, 1)
            sink2.writePcm(ByteArray(48000)) // 1 sec PCM silence
            sink2.flushAndClose()

            val combined = File(testDir, "combined.wav")
            val success = combiner.combineWavFiles(listOf(file1, file2), combined)

            assertTrue(success)
            assertTrue(combined.exists())
            assertEquals(96044L, combined.length()) // 48000 + 48000 + 44-byte header

            val header = combiner.parseWavHeader(combined)
            assertNotNull(header)
            assertEquals(24000, header?.sampleRate)
            assertEquals(1, header?.channels)
            assertEquals(96000L, header?.pcmDataLength)
        }
    }
}
