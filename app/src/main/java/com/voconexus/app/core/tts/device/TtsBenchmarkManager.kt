package com.voconexus.app.core.tts.device

import android.content.Context
import android.os.BatteryManager
import com.voconexus.app.core.tts.SynthesisSettings
import com.voconexus.app.core.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BenchmarkResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val engineId: String,
    val modelId: String,
    val voiceId: String,
    val coldStartMs: Long,
    val synthesisMs: Long,
    val audioDurationMs: Long,
    val realTimeFactor: Float,
    val peakMemoryMb: Int,
    val isStale: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val synthesisDurationMs: Long get() = synthesisMs
}

class TtsBenchmarkManager(private val context: Context? = null) {

    val benchmarkText = "VocoNexus offline-first high quality text to speech system performance benchmark test."

    suspend fun runLocalBenchmark(
        engine: TtsEngine,
        modelId: String,
        voiceId: String
    ): BenchmarkResult = withContext(Dispatchers.Default) {
        // Battery check if context available
        if (context != null) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            if (batteryLevel < 15) {
                throw IllegalStateException("Battery is too low (${batteryLevel}%). Charge device before running performance benchmark.")
            }
        }

        // 1. Cold start measurement
        val coldStartBegin = System.currentTimeMillis()
        val warmAudio = engine.synthesize(
            text = "Cold start test.",
            voiceId = voiceId,
            settings = SynthesisSettings(speed = 1.0f)
        )
        val coldStartDuration = System.currentTimeMillis() - coldStartBegin

        // 2. Warm generation measurement
        val synthesisBegin = System.currentTimeMillis()
        val audio = engine.synthesize(
            text = benchmarkText,
            voiceId = voiceId,
            settings = SynthesisSettings(speed = 1.0f)
        )
        val synthesisDuration = System.currentTimeMillis() - synthesisBegin

        val audioDuration = audio.durationMs.coerceAtLeast(1L)
        val rtf = (synthesisDuration.toFloat() / audioDuration.toFloat())

        val runtime = Runtime.getRuntime()
        val usedMemoryMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()

        return@withContext BenchmarkResult(
            engineId = engine.id,
            modelId = modelId,
            voiceId = voiceId,
            coldStartMs = coldStartDuration,
            synthesisMs = synthesisDuration,
            audioDurationMs = audioDuration,
            realTimeFactor = rtf,
            peakMemoryMb = usedMemoryMb
        )
    }
}
