package com.voconexus.app.core.tts

enum class AudioEncoding {
    PCM_16BIT,
    FLOAT_32BIT,
    MP3
}

data class SynthesizedAudio(
    val sampleRate: Int,
    val channels: Int,
    val encoding: AudioEncoding = AudioEncoding.PCM_16BIT,
    val durationMs: Long,
    val pcmData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SynthesizedAudio

        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (encoding != other.encoding) return false
        if (durationMs != other.durationMs) return false
        if (!pcmData.contentEquals(other.pcmData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + channels
        result = 31 * result + encoding.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + pcmData.contentHashCode()
        return result
    }
}
