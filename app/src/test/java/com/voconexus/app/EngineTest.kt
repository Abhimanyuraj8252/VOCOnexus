package com.voconexus.app

import com.voconexus.app.core.tts.engine.EdgeTtsEngine
import com.voconexus.app.core.tts.engine.GoogleCloudTtsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class EngineTest {

    @Test
    fun testGoogleTts() = runBlocking {
        try {
            val engine = GoogleCloudTtsEngine(ApplicationProvider.getApplicationContext())
            val audio = engine.synthesize("Hello", "g_en_us_female", com.voconexus.app.core.tts.SynthesisSettings())
            println("Google TTS Success: " + audio.pcmData.size)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testEdgeTts() = runBlocking {
        try {
            val engine = EdgeTtsEngine(ApplicationProvider.getApplicationContext())
            val audio = engine.synthesize("Hello", "en-US-AriaNeural", com.voconexus.app.core.tts.SynthesisSettings())
            println("Edge TTS Success: " + audio.pcmData.size)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
