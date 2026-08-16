package com.voconexus.app.core.tts

import kotlinx.coroutines.flow.StateFlow

enum class EngineLifecycleState {
    UNLOADED,
    LOADING,
    READY,
    BUSY,
    UNLOADING,
    ERROR
}

data class SynthesisSettings(
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val sampleRate: Int = 24000
)

interface TtsEngine {
    val id: String
    val displayName: String
    val version: String
    val capabilities: EngineCapabilities
    val lifecycleState: StateFlow<EngineLifecycleState>

    suspend fun getModels(): List<TtsModel>
    suspend fun getVoices(modelId: String): List<TtsVoice>
    suspend fun loadModel(modelId: String, modelPath: String)
    suspend fun unloadModel()
    suspend fun synthesize(
        text: String,
        voiceId: String,
        settings: SynthesisSettings = SynthesisSettings()
    ): SynthesizedAudio

    suspend fun cancelSynthesis()
    suspend fun release()
}
