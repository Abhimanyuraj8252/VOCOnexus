package com.voconexus.app.core.tts.preview

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Audio preview player for synthesized speech.
 *
 * ExoPlayer MUST be created and accessed only on the main thread.
 * All public methods are safe to call from any thread - they dispatch
 * to the main thread internally.
 */
class AudioPreviewPlayer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeVoiceId = MutableStateFlow<String?>(null)
    val activeVoiceId: StateFlow<String?> = _activeVoiceId.asStateFlow()

    init {
        mainHandler.post {
            initializePlayer()
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (!playing && playbackState == Player.STATE_ENDED) {
                        _activeVoiceId.value = null
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _activeVoiceId.value = null
                    }
                }
            })
        }
    }

    /**
     * Play a preview audio file. Safe to call from any thread.
     */
    fun playPreview(voiceId: String, audioFile: File, pitch: Float = 1.0f) {
        mainHandler.post {
            _activeVoiceId.value = voiceId
            
            if (exoPlayer == null) {
                initializePlayer()
            }
            
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(audioFile))
            exoPlayer?.apply {
                stop()
                setPlaybackParameters(androidx.media3.common.PlaybackParameters(1.0f, pitch))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }
    }

    fun pause() {
        mainHandler.post {
            try {
                exoPlayer?.pause()
            } catch (_: Exception) {}
            _isPlaying.value = false
        }
    }

    fun resume() {
        mainHandler.post {
            try {
                exoPlayer?.play()
            } catch (_: Exception) {}
            _isPlaying.value = true
        }
    }

    /**
     * Stop playback. Safe to call from any thread.
     */
    fun stop() {
        mainHandler.post {
            try {
                exoPlayer?.stop()
            } catch (_: Exception) {}
            _isPlaying.value = false
            _activeVoiceId.value = null
        }
    }

    /**
     * Release ExoPlayer. Safe to call from any thread.
     */
    fun release() {
        mainHandler.post {
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (_: Exception) {}
            exoPlayer = null
            _isPlaying.value = false
            _activeVoiceId.value = null
        }
    }
}
