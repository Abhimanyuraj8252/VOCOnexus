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
        return engines[engineId]
    }

    fun getRequiredEngine(engineId: String): TtsEngine {
        return engines[engineId] ?: engines["fake-tts"] ?: engines.values.firstOrNull()
        ?: throw IllegalStateException("No TTS engines registered in TtsEngineRegistry")
    }

    fun getAllEngines(): List<TtsEngine> {
        return engines.values.toList()
    }
}
