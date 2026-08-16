package com.voconexus.app

import com.voconexus.app.core.generation.audio.WavAudioSink
import com.voconexus.app.core.multilingual.audio.MultiVoiceAudioAssembler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MultiVoiceAudioAssemblerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val assembler = MultiVoiceAudioAssembler()

    @Test
    fun testMultiVoiceWavSegmentAssembly() = runBlocking {
        val file1 = tempFolder.newFile("seg1.wav")
        val file2 = tempFolder.newFile("seg2.wav")

        val sink1 = WavAudioSink().apply { open(file1, 24000, 1) }
        val pcm1 = ByteArray(4800) { (it % 128).toByte() }
        sink1.writePcm(pcm1)
        sink1.flushAndClose()

        val sink2 = WavAudioSink().apply { open(file2, 24000, 1) }
        val pcm2 = ByteArray(4800) { ((it + 10) % 128).toByte() }
        sink2.writePcm(pcm2)
        sink2.flushAndClose()

        val outputFile = tempFolder.newFile("output_assembled.wav")

        val success = assembler.assembleSegments(
            segmentFiles = listOf(file1, file2),
            targetOutputFile = outputFile,
            targetSampleRate = 24000,
            targetChannels = 1
        )

        assertTrue(success)
        assertTrue(outputFile.exists())
        assertEquals(44L + 9600L, outputFile.length())
    }
}
