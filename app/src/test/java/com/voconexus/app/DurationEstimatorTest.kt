package com.voconexus.app

import com.voconexus.app.core.domain.StandardDurationEstimator
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationEstimatorTest {

    private val estimator = StandardDurationEstimator()

    @Test
    fun testDurationEstimationSpeedAndPunctuation() {
        val text = "Hello world. This is a production long-form test."
        val words = 8

        val duration1x = estimator.estimateDurationMs(text, words, 1.0f)
        val duration2x = estimator.estimateDurationMs(text, words, 2.0f)

        assertTrue(duration1x > 0L)
        assertTrue(duration2x < duration1x)
    }
}
