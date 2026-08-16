package com.voconexus.app

import com.voconexus.app.core.domain.speech.PitchConfig
import com.voconexus.app.core.domain.speech.isPitchValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchConfigTest {

    @Test
    fun testPitchValidation() {
        assertTrue(isPitchValid(0.0f))
        assertTrue(isPitchValid(-4.0f))
        assertTrue(isPitchValid(4.0f))

        assertFalse(isPitchValid(Float.NaN))
        assertFalse(isPitchValid(Float.POSITIVE_INFINITY))
    }

    @Test
    fun testPitchWarningsAndExtremeDetection() {
        val normal = PitchConfig(2.0f)
        assertFalse(normal.isExtreme)
        assertNull(normal.warningMessage)

        val extremeHigh = PitchConfig(6.0f)
        assertTrue(extremeHigh.isExtreme)
        assertEquals("Extreme pitch changes may sound unnatural.", extremeHigh.warningMessage)

        val invalid = PitchConfig(Float.NaN)
        assertEquals("Pitch must be a valid finite number", invalid.warningMessage)
    }
}
