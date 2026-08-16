package com.voconexus.app

import com.voconexus.app.core.domain.speech.SpeechSpeedConfig
import com.voconexus.app.core.domain.speech.isSpeedValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSpeedConfigTest {

    @Test
    fun testSpeedValidation() {
        assertTrue(isSpeedValid(1.0f))
        assertTrue(isSpeedValid(0.75f))
        assertTrue(isSpeedValid(2.5f))

        assertFalse(isSpeedValid(0.0f))
        assertFalse(isSpeedValid(-1.0f))
        assertFalse(isSpeedValid(Float.NaN))
        assertFalse(isSpeedValid(Float.POSITIVE_INFINITY))
    }

    @Test
    fun testSpeedWarningsAndExtremeDetection() {
        val normal = SpeechSpeedConfig(1.25f)
        assertFalse(normal.isExtreme)
        assertNull(normal.warningMessage)

        val extremeFast = SpeechSpeedConfig(2.0f)
        assertTrue(extremeFast.isExtreme)
        assertEquals("Extreme speed. Speech naturalness may decrease.", extremeFast.warningMessage)

        val invalid = SpeechSpeedConfig(-0.5f)
        assertEquals("Speed must be a positive finite number", invalid.warningMessage)
    }
}
