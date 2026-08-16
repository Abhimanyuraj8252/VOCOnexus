package com.voconexus.app.core.tts

data class EngineCapabilities(
    val isOffline: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsVoiceSelection: Boolean = true,
    val supportsMultilingual: Boolean = true,
    val supportsSpeedControl: Boolean = true,
    val supportsPitchControl: Boolean = false,
    val supportsCpuInference: Boolean = true,
    val supportsGpuAcceleration: Boolean = false,
    val supportsNnapi: Boolean = false
)
