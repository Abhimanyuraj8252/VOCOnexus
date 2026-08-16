package com.voconexus.app.core.engine

import java.security.MessageDigest

object GenerationFingerprint {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256Stream(inputStream: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = inputStream.read(buffer)
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun normalizeText(rawText: String): String {
        return rawText
            .trim()
            .replace("[\u200B\u200C\u200D\uFEFF]".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .replace("[\"'\u201C\u201D\u2018\u2019]".toRegex(), "")
    }

    fun computeChunkFingerprint(
        normalizedTextHash: String,
        engineId: String,
        modelId: String,
        voiceId: String,
        speed: Float,
        pitch: Float
    ): String {
        val compositeKey = "$normalizedTextHash|$engineId|$modelId|$voiceId|${"%.2f".format(speed)}|${"%.2f".format(pitch)}"
        return sha256(compositeKey)
    }

    fun isChunkInvalid(
        oldEngineId: String,
        oldModelId: String,
        oldVoiceId: String,
        oldNormalizedTextHash: String,
        oldSpeed: Float,
        oldPitch: Float,
        newEngineId: String,
        newModelId: String,
        newVoiceId: String,
        newNormalizedTextHash: String,
        newSpeed: Float,
        newPitch: Float
    ): Boolean {
        val oldFingerprint = computeChunkFingerprint(oldNormalizedTextHash, oldEngineId, oldModelId, oldVoiceId, oldSpeed, oldPitch)
        val newFingerprint = computeChunkFingerprint(newNormalizedTextHash, newEngineId, newModelId, newVoiceId, newSpeed, newPitch)
        return oldFingerprint != newFingerprint
    }

    const val PROCESSING_PIPELINE_VERSION = 1

    fun computeDerivedFingerprint(
        sourceAudioChecksum: String,
        timeStretchRatio: Float,
        pitchShiftSemitones: Float,
        pipelineVersion: Int = PROCESSING_PIPELINE_VERSION
    ): String {
        val compositeKey = "$sourceAudioChecksum|v$pipelineVersion|${"%.4f".format(timeStretchRatio)}|${"%.2f".format(pitchShiftSemitones)}"
        return sha256(compositeKey)
    }
}
