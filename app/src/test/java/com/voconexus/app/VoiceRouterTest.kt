package com.voconexus.app

import com.voconexus.app.core.multilingual.language.LanguageSegment
import com.voconexus.app.core.multilingual.language.VoiceProfile
import com.voconexus.app.core.multilingual.routing.VoiceRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRouterTest {

    private val router = VoiceRouter()

    private val defaultVoice = VoiceProfile("af_heart", "kokoro-82m", "kokoro-82m-v1.0", "en", "Heart (English)", true)
    private val hindiVoice = VoiceProfile("hi_aarti", "sherpa-onnx", "vits-hindi-v1.0", "hi", "Aarti (Hindi)", true)

    @Test
    fun testAutomaticCompatibleVoiceRouting() {
        val segment = LanguageSegment(id = "seg1", text = "नमस्ते", languageCode = "hi")
        val result = router.routeSegment(
            segment = segment,
            availableVoices = listOf(defaultVoice, hindiVoice),
            projectDefaultVoice = defaultVoice
        )

        assertEquals("hi_aarti", result.voiceProfile.voiceId)
        assertFalse(result.isFallbackUsed)
    }

    @Test
    fun testSpeakerOverridePriority() {
        val segment = LanguageSegment(id = "seg2", text = "Hello", languageCode = "en", speakerId = "Alice")
        val aliceVoice = VoiceProfile("af_bella", "kokoro-82m", "kokoro-82m-v1.0", "en", "Bella (Alice)", true)

        val result = router.routeSegment(
            segment = segment,
            availableVoices = listOf(defaultVoice, hindiVoice, aliceVoice),
            projectDefaultVoice = defaultVoice,
            speakerMappings = mapOf("Alice" to aliceVoice)
        )

        assertEquals("af_bella", result.voiceProfile.voiceId)
        assertFalse(result.isFallbackUsed)
    }

    @Test
    fun testFallbackToProjectDefault() {
        val segment = LanguageSegment(id = "seg3", text = "Bonjour", languageCode = "fr")
        val result = router.routeSegment(
            segment = segment,
            availableVoices = listOf(defaultVoice, hindiVoice),
            projectDefaultVoice = defaultVoice
        )

        assertEquals("af_heart", result.voiceProfile.voiceId)
        assertTrue(result.isFallbackUsed)
    }
}
