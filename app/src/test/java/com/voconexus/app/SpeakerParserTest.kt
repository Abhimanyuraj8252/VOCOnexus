package com.voconexus.app

import com.voconexus.app.core.multilingual.speaker.SpeakerParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerParserTest {

    private val parser = SpeakerParser()

    @Test
    fun testDialogueLineParsing() {
        val line = "Alice: Hello everyone, welcome to the show."
        val segment = parser.parseDialogueLine(line)

        assertEquals("Alice", segment.speakerId)
        assertEquals("Hello everyone, welcome to the show.", segment.spokenText)
    }

    @Test
    fun testNarratorLineFallback() {
        val line = "The sun rose quietly over the mountains."
        val segment = parser.parseDialogueLine(line)

        assertEquals("Narrator", segment.speakerId)
        assertEquals("The sun rose quietly over the mountains.", segment.spokenText)
    }

    @Test
    fun testExcludedLabels() {
        val line = "Note: This is an important detail."
        val segment = parser.parseDialogueLine(line)

        assertEquals("Narrator", segment.speakerId)
        assertEquals("Note: This is an important detail.", segment.spokenText)
    }
}
