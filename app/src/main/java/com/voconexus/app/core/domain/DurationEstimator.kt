package com.voconexus.app.core.domain

interface DurationEstimator {
    fun estimateDurationMs(text: String, wordCount: Int, speed: Float = 1.0f): Long
    fun estimateDurationMsWithCps(text: String, wordCount: Int, speed: Float = 1.0f, charsPerSecond: Float = 15.0f): Long
}

class StandardDurationEstimator : DurationEstimator {

    companion object {
        const val BASE_MS_PER_WORD = 400L // Baseline 150 WPM = 400ms per word
        const val PAUSE_PERIOD_MS = 400L  // Pause after . ! ?
        const val PAUSE_COMMA_MS = 200L   // Pause after , ; :
    }

    override fun estimateDurationMs(text: String, wordCount: Int, speed: Float): Long {
        return estimateDurationMsWithCps(text, wordCount, speed, 15.0f)
    }

    override fun estimateDurationMsWithCps(text: String, wordCount: Int, speed: Float, charsPerSecond: Float): Long {
        if (wordCount <= 0 || text.isBlank()) return 0L

        val safeSpeed = speed.coerceIn(0.5f, 3.0f)
        val safeCps = charsPerSecond.coerceIn(5.0f, 40.0f)

        val charDurationSec = text.length.toFloat() / safeCps
        val baseMs = (charDurationSec * 1000.0f) / safeSpeed

        var pauseMs = 0L
        for (char in text) {
            when (char) {
                '.', '!', '?' -> pauseMs += PAUSE_PERIOD_MS
                ',', ';', ':' -> pauseMs += PAUSE_COMMA_MS
            }
        }

        val totalMs = (baseMs + (pauseMs.toFloat() / safeSpeed)).toLong()
        return totalMs.coerceAtLeast(1000L)
    }
}
