package com.voconexus.app

import com.voconexus.app.core.tts.EngineLifecycleState
import com.voconexus.app.core.tts.SynthesisSettings
import com.voconexus.app.core.tts.engine.FakeTtsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTtsEngineTest {

    private val engine = FakeTtsEngine()

    @Test
    fun testLifecycleAndSynthesis() = runBlocking {
        assertEquals(EngineLifecycleState.UNLOADED, engine.lifecycleState.value)

        val models = engine.getModels()
        assertTrue(models.isNotEmpty())

        val model = models[0]
        engine.loadModel(model.id, "/fake/path")
        assertEquals(EngineLifecycleState.READY, engine.lifecycleState.value)

        val voices = engine.getVoices(model.id)
        assertTrue(voices.isNotEmpty())

        val audio = engine.synthesize("Hello world testing FakeTtsEngine", voices[0].id, SynthesisSettings())
        assertNotNull(audio)
        assertEquals(24000, audio.sampleRate)
        assertTrue(audio.durationMs > 0)
        assertTrue(audio.pcmData.isNotEmpty())

        engine.unloadModel()
        assertEquals(EngineLifecycleState.UNLOADED, engine.lifecycleState.value)
    }
}
