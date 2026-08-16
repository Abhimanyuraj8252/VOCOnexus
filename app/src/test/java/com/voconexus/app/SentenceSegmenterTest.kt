package com.voconexus.app

import com.voconexus.app.core.domain.StandardDurationEstimator
import com.voconexus.app.core.planner.model.HeuristicTokenEstimator
import com.voconexus.app.core.planner.segmentation.RuleBasedSentenceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmenterTest {

    private val segmenter = RuleBasedSentenceSegmenter()
    private val tokenEstimator = HeuristicTokenEstimator()
    private val durationEstimator = StandardDurationEstimator()

    @Test
    fun testEnglishSentenceSegmentation() {
        val text = "Hello world. How are you today? I am fine!"
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertEquals(3, sentences.size)
        assertEquals("Hello world.", sentences[0].text)
        assertEquals("How are you today?", sentences[1].text)
        assertEquals("I am fine!", sentences[2].text)
    }

    @Test
    fun testHindiDevanagariDandaSegmentation() {
        val text = "यह एक हिंदी वाक्य है। क्या आप ठीक हैं? हाँ, मैं ठीक हूँ।"
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertEquals(3, sentences.size)
        assertEquals("यह एक हिंदी वाक्य है।", sentences[0].text)
        assertEquals("क्या आप ठीक हैं?", sentences[1].text)
        assertEquals("हाँ, मैं ठीक हूँ।", sentences[2].text)
        assertEquals("hi-IN", sentences[0].language)
    }

    @Test
    fun testMixedHindiEnglishSegmentation() {
        val text = "यह एक example है। This is an English sentence. फिर हम वापस हिंदी में आते हैं।"
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertEquals(3, sentences.size)
        assertEquals("यह एक example है।", sentences[0].text)
        assertEquals("This is an English sentence.", sentences[1].text)
        assertEquals("फिर हम वापस हिंदी में आते हैं।", sentences[2].text)
    }

    @Test
    fun testAbbreviationHandling() {
        val text = "Dr. Smith arrived at 5 p.m. He left later."
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertEquals(2, sentences.size)
        assertEquals("Dr. Smith arrived at 5 p.m.", sentences[0].text)
        assertEquals("He left later.", sentences[1].text)
    }

    @Test
    fun testDecimalNumbersAndPrices() {
        val text = "The value of pi is 3.14. It costs $19.99 today."
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertEquals(2, sentences.size)
        assertEquals("The value of pi is 3.14.", sentences[0].text)
        assertEquals("It costs $19.99 today.", sentences[1].text)
    }

    @Test
    fun testUrlsAndEmails() {
        val text = "Visit https://example.com/page for details. Contact user@domain.com now."
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertEquals(2, sentences.size)
        assertEquals("Visit https://example.com/page for details.", sentences[0].text)
        assertEquals("Contact user@domain.com now.", sentences[1].text)
    }

    @Test
    fun testQuotationAndDialogue() {
        val text = "He said, \"Wait. Don't go.\"\n\n\"Where are you going?\""
        val sentences = segmenter.segmentDocument(text, tokenEstimator, durationEstimator)

        assertTrue(sentences.size >= 2)
        assertTrue(sentences[0].text.contains("He said"))
    }
}
