package com.voconexus.app

import com.voconexus.app.core.preprocessing.PreprocessingOptions
import com.voconexus.app.core.preprocessing.TextPreprocessingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import kotlinx.coroutines.test.runTest

class TextPreprocessingEngineTest {

    private val engine = TextPreprocessingEngine()

    @Test
    fun testNormalizeWhitespaceAndLineEndings() = runTest {
        val input = "Hello     world.\r\n\r\n\r\nThis   is   paragraph 2.\t\t"
        val result = engine.preprocess(input, PreprocessingOptions(normalizeWhitespace = true))

        val expected = "Hello world.\n\nThis is paragraph 2."
        assertEquals(expected, result.normalizedText)
        assertTrue(result.summary.whitespaceFixes > 0)
    }

    @Test
    fun testStripHtmlTags() = runTest {
        val input = "<i>Hello</i> <b>world</b>! <font color='red'>Welcome</font>."
        val result = engine.preprocess(input, PreprocessingOptions(stripFormattingTags = true))

        assertEquals("Hello world! Welcome.", result.normalizedText)
        assertEquals(6, result.summary.tagsRemoved)
    }

    @Test
    fun testStripSrtTimestamps() = runTest {
        val input = "00:01:20,000 --> 00:01:25,000 Hello 00:01:26,000 --> 00:01:30,000 World."
        val result = engine.preprocess(input, PreprocessingOptions(removeSrtTimestamps = true))

        assertEquals("Hello World.", result.normalizedText)
        assertEquals(2, result.summary.timestampsRemoved)
    }

    @Test
    fun testConservativePunctuationNormalization() = runTest {
        val input = "Wait!!!!!! What??? Is this real?!"
        val result = engine.preprocess(input, PreprocessingOptions(normalizeRepeatedPunctuation = true))

        assertEquals("Wait! What? Is this real?!", result.normalizedText)
    }

    @Test
    fun testFullPreservationOfHindiDevanagariAndUnicode() = runTest {
        val input = "यह एक हिंदी वाक्य है! This is English with emoji 🎙️."
        val result = engine.preprocess(input)

        assertEquals("यह एक हिंदी वाक्य है! This is English with emoji 🎙️.", result.normalizedText)
    }

    @Test
    fun testClassifiesTechnicalArtifacts() = runTest {
        val input = "###\nNormal dialogue text.\n***\n[SCENE 4]"
        val result = engine.preprocess(input, PreprocessingOptions(classifyTechnicalArtifacts = true))

        assertTrue(result.summary.artifactsDetected >= 3)
    }
}
