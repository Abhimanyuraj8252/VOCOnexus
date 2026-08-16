package com.voconexus.app

import com.voconexus.app.core.parser.SrtParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class SrtParserTest {

    private val parser = SrtParser()

    @Test
    fun testParseStandardSrtSubtitle() = runBlocking {
        val srtContent = """
            1
            00:01:20,000 --> 00:01:25,000
            <i>Hello everyone.</i>

            2
            00:01:26,000 --> 00:01:31,000
            Welcome to <b>VocoNexus</b> TTS.
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(srtContent.toByteArray(StandardCharsets.UTF_8)), "episode1.srt")

        assertEquals("SRT", result.sourceType)
        assertEquals("episode1.srt", result.originalFileName)
        assertEquals(2, result.blocksParsed)

        val expectedSpokenText = "Hello everyone.\n\nWelcome to VocoNexus TTS."
        assertEquals(expectedSpokenText, result.extractedText)
    }

    @Test
    fun testPreservesNumbersAndColonsInDialogue() = runBlocking {
        val srtContent = """
            1
            00:00:01,000 --> 00:00:05,000
            I live at number 12.

            2
            00:00:06,000 --> 00:00:10,000
            Call me at 10:30.
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(srtContent.toByteArray(StandardCharsets.UTF_8)), "dialogue.srt")

        val expectedText = "I live at number 12.\n\nCall me at 10:30."
        assertEquals(expectedText, result.extractedText)
    }

    @Test
    fun testHandlesMultilineSubtitlesAndUnicode() = runBlocking {
        val srtContent = """
            1
            00:02:10,500 --> 00:02:15,200
            This is line one.
            This is line two.

            2
            00:02:16,000 --> 00:02:20,000
            यह हिंदी संवाद है।
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(srtContent.toByteArray(StandardCharsets.UTF_8)), "multiline.srt")

        val expected = "This is line one. This is line two.\n\nयह हिंदी संवाद है।"
        assertEquals(expected, result.extractedText)
    }

    @Test
    fun testMalformedSrtRecovery() = runBlocking {
        val srtContent = """
            00:01:20,000 --> 00:01:25,000
            Dialogue without block index.

            3
            00:01:26,000 --> 00:01:31,000
            Dialogue with block index.
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(srtContent.toByteArray(StandardCharsets.UTF_8)), "malformed.srt")

        assertTrue(result.extractedText.contains("Dialogue without block index."))
        assertTrue(result.extractedText.contains("Dialogue with block index."))
    }
}
