package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.playback.PlayableItem
import com.voconexus.app.core.playback.PlaybackController
import com.voconexus.app.core.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackControllerTest {

    private lateinit var controller: PlaybackController
    private lateinit var dummyFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        controller = PlaybackController(context)
        dummyFile = File(System.getProperty("java.io.tmpdir"), "test_play_${System.currentTimeMillis()}.wav")
            .also { it.writeText("RIFF_DUMMY_AUDIO_DATA_FOR_UNIT_TESTING_1234567890") }
    }

    @Test
    fun testPlayableItemStateAndControl() {
        val item = PlayableItem(
            chunkId = "c1",
            title = "Chunk #1",
            audioPath = dummyFile.absolutePath,
            durationMs = 2000L
        )

        controller.playItem(item)

        val state = controller.state.value
        assertNotNull(state.currentItem)
        assertEquals("c1", state.currentItem?.chunkId)

        controller.stop()
        assertEquals(PlaybackStatus.IDLE, controller.state.value.status)

        controller.release()
    }
}
