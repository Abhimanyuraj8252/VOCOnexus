package com.voconexus.app.core.tts.engine

import android.content.Context
import com.voconexus.app.core.tts.TtsEngine
import java.util.concurrent.ConcurrentHashMap

class TtsEngineRegistry(
    private val context: Context,
    defaultEngines: List<TtsEngine> = listOf(
        SherpaOnnxEngine(),
        KokoroEngine(),
        PiperEngine(),
        GoogleCloudTtsEngine(context),
        EdgeTtsEngine(context),
        FakeTtsEngine()
    )
) {

    private val engines = ConcurrentHashMap<String, TtsEngine>()

    init {
        defaultEngines.forEach { registerEngine(it) }
    }

    fun registerEngine(engine: TtsEngine) {
        engines[engine.id] = engine
    }

    fun getEngine(engineId: String): TtsEngine? {
        val exact = engines[engineId]
        if (exact != null) return exact
        val norm = engineId.lowercase()
        return engines.values.find { engine ->
            val eid = engine.id.lowercase()
            (norm.contains("kokoro") && eid.contains("kokoro")) ||
            (norm.contains("piper") && eid.contains("piper")) ||
            (norm.contains("sherpa") && eid.contains("sherpa")) ||
            (norm.contains("edge") && eid.contains("edge")) ||
            (norm.contains("google") && eid.contains("google"))
        }
    }

    fun getRequiredEngine(engineId: String): TtsEngine {
        return getEngine(engineId) ?: engines["fake-tts"] ?: engines.values.firstOrNull()
        ?: throw IllegalStateException("No TTS engines registered in TtsEngineRegistry")
    }

    fun getAllEngines(): List<TtsEngine> {
        return engines.values.toList()
    }
}
