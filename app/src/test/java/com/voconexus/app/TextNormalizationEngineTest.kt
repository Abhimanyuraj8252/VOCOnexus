package com.voconexus.app

import com.voconexus.app.core.speech.model.NormalizationProfile
import com.voconexus.app.core.speech.normalization.TextNormalizationEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizationEngineTest {

    private val engine = TextNormalizationEngine()

    @Test
    fun testCurrencyNormalizationEnglish() {
        val text = "Price is ₹500 and $20."
        val normalized = engine.normalizeText(text, NormalizationProfile.NATURAL_NARRATION, "en")

        assertEquals("Price is 500 rupees and 20 dollars.", normalized)
    }

    @Test
    fun testCurrencyNormalizationHindi() {
        val text = "कीमत ₹500 है।"
        val normalized = engine.normalizeText(text, NormalizationProfile.NATURAL_NARRATION, "hi")

        assertEquals("कीमत 500 रुपये है।", normalized)
    }

    @Test
    fun testTechnicalTextProtection() {
        val url = "http://192.168.1.1"
        val normalized = engine.normalizeText(url, NormalizationProfile.NATURAL_NARRATION, "en")

        assertEquals(url, normalized)
    }

    @Test
    fun testExactTextProfile() {
        val text = "Price is ₹500."
        val normalized = engine.normalizeText(text, NormalizationProfile.EXACT_TEXT, "en")

        assertEquals("Price is ₹500.", normalized)
    }
}
