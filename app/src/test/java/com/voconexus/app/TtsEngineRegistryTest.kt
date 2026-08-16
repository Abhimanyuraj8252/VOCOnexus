package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.tts.engine.FakeTtsEngine
import com.voconexus.app.core.tts.engine.KokoroEngine
import com.voconexus.app.core.tts.engine.PiperEngine
import com.voconexus.app.core.tts.engine.TtsEngineRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsEngineRegistryTest {

    @Test
    fun testEngineRegistrationAndLookup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = TtsEngineRegistry(context)

        val kokoro = registry.getEngine("kokoro-82m")
        assertNotNull(kokoro)
        assertEquals("Kokoro 82M (Sherpa-ONNX)", kokoro?.displayName)

        val piper = registry.getEngine("piper-onnx")
        assertNotNull(piper)
        assertEquals("Piper ONNX Engine", piper?.displayName)

        val fake = registry.getEngine("fake-tts")
        assertNotNull(fake)
        assertEquals("Fake Testing Engine", fake?.displayName)
    }

    @Test
    fun testRequiredEngineFallback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = TtsEngineRegistry(context)
        val engine = registry.getRequiredEngine("non-existent-engine")

        assertNotNull(engine)
        assertEquals("fake-tts", engine.id)
    }
}
