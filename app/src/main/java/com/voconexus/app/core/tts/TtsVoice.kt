package com.voconexus.app.core.tts

enum class VoiceGender {
    MALE,
    FEMALE,
    OTHER,
    UNKNOWN
}

data class TtsVoice(
    val id: String,
    val modelId: String,
    val engineId: String,
    val name: String,
    val language: String,
    val locale: String,
    val gender: VoiceGender = VoiceGender.UNKNOWN,
    val sampleRate: Int = 24000,
    val isDefault: Boolean = false,
    val previewSamplePath: String? = null
)
