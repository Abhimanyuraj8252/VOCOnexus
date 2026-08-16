package com.voconexus.app

import com.voconexus.app.core.multilingual.language.ConfidenceLevel
import com.voconexus.app.core.multilingual.language.LanguageDetector
import com.voconexus.app.core.multilingual.language.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDetectorTest {

    private val detector = LanguageDetector()

    @Test
    fun testDevanagariHindiDetection() {
        val text = "नमस्ते, आज हम एक नए project पर काम करेंगे।"
        val detected = detector.detectLanguage(text)

        assertEquals("hi", detected.languageCode)
        assertEquals(ScriptType.DEVANAGARI, detected.scriptType)
        assertEquals(ConfidenceLevel.HIGH, detected.confidence)
    }

    @Test
    fun testBengaliDetection() {
        val text = "নমস্কার বন্ধুগণ, আজকে আমরা একটি নতুন বিষয়ে কথা বলব।"
        val detected = detector.detectLanguage(text)

        assertEquals("bn", detected.languageCode)
        assertEquals(ScriptType.BENGALI, detected.scriptType)
        assertEquals(ConfidenceLevel.HIGH, detected.confidence)
    }

    @Test
    fun testLatinEnglishDetection() {
        val text = "Welcome to VocoNexus text to speech engine."
        val detected = detector.detectLanguage(text)

        assertEquals("en", detected.languageCode)
        assertEquals(ScriptType.LATIN, detected.scriptType)
        assertEquals(ConfidenceLevel.HIGH, detected.confidence)
    }

    @Test
    fun testShortAmbiguousText() {
        val text = "OK"
        val detected = detector.detectLanguage(text)

        assertEquals("en", detected.languageCode)
        assertTrue(detected.confidence == ConfidenceLevel.LOW || detected.confidence == ConfidenceLevel.MEDIUM)
    }
}
