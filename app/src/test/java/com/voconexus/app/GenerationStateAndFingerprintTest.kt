package com.voconexus.app

import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.engine.GenerationFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationStateAndFingerprintTest {

    @Test
    fun testTextNormalizationAndSha256() {
        val raw = "  Hello   World! \"Smart quotes\"  "
        val normalized = GenerationFingerprint.normalizeText(raw)
        assertEquals("Hello World! Smart quotes", normalized)

        val hash1 = GenerationFingerprint.sha256(normalized)
        val hash2 = GenerationFingerprint.sha256(normalized)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 hex string length
    }

    @Test
    fun testFingerprintInvalidationOnVoiceChange() {
        val textHash = GenerationFingerprint.sha256("test chunk text")

        val isInvalid = GenerationFingerprint.isChunkInvalid(
            oldEngineId = "kokoro-82m",
            oldModelId = "kokoro-v1.0",
            oldVoiceId = "af_heart",
            oldNormalizedTextHash = textHash,
            oldSpeed = 1.0f,
            oldPitch = 1.0f,
            newEngineId = "kokoro-82m",
            newModelId = "kokoro-v1.0",
            newVoiceId = "am_adam", // Voice changed!
            newNormalizedTextHash = textHash,
            newSpeed = 1.0f,
            newPitch = 1.0f
        )

        assertTrue(isInvalid)
    }

    @Test
    fun testFingerprintValidWhenUnchanged() {
        val textHash = GenerationFingerprint.sha256("test chunk text")

        val isInvalid = GenerationFingerprint.isChunkInvalid(
            oldEngineId = "kokoro-82m",
            oldModelId = "kokoro-v1.0",
            oldVoiceId = "af_heart",
            oldNormalizedTextHash = textHash,
            oldSpeed = 1.0f,
            oldPitch = 1.0f,
            newEngineId = "kokoro-82m",
            newModelId = "kokoro-v1.0",
            newVoiceId = "af_heart",
            newNormalizedTextHash = textHash,
            newSpeed = 1.0f,
            newPitch = 1.0f
        )

        assertFalse(isInvalid)
    }

    @Test
    fun testChunkStatusEnumValues() {
        assertEquals("PENDING", ChunkStatus.PENDING.name)
        assertEquals("QUEUED", ChunkStatus.QUEUED.name)
        assertEquals("GENERATING", ChunkStatus.GENERATING.name)
        assertEquals("COMPLETED", ChunkStatus.COMPLETED.name)
        assertEquals("NEEDS_REGENERATION", ChunkStatus.NEEDS_REGENERATION.name)
    }
}
