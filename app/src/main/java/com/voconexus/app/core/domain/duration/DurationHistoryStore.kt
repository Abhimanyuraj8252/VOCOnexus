package com.voconexus.app.core.domain.duration

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class SpeakingRateMetric(
    val voiceId: String,
    val modelId: String,
    val charsPerSecond: Float
)

class DurationHistoryStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voconexus_duration_history", Context.MODE_PRIVATE)

    fun recordMeasurement(
        voiceId: String,
        modelId: String,
        characterCount: Int,
        durationMs: Long,
        speed: Float
    ) {
        if (characterCount <= 0 || durationMs <= 0L) return

        val normalizedDurationSec = (durationMs.toFloat() / 1000.0f) * speed
        val currentCps = characterCount.toFloat() / normalizedDurationSec.coerceAtLeast(0.1f)

        val key = getKey(voiceId, modelId)
        val existingCps = prefs.getFloat(key, 15.0f)

        // Exponential moving average update (80% historical, 20% new)
        val updatedCps = (existingCps * 0.8f + currentCps * 0.2f).coerceIn(5.0f, 40.0f)
        prefs.edit { putFloat(key, updatedCps) }
    }

    fun getEstimatedCharsPerSecond(voiceId: String, modelId: String): Float {
        val key = getKey(voiceId, modelId)
        return prefs.getFloat(key, 15.0f)
    }

    private fun getKey(voiceId: String, modelId: String) = "${modelId}_$voiceId"
}
