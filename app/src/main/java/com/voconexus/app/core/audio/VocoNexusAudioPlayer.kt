package com.voconexus.app.core.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentAudioPath: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

class VocoNexusAudioPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var positionUpdateJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startPositionUpdater()
                } else {
                    stopPositionUpdater()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _playerState.value = _playerState.value.copy(
                        durationMs = player.duration.coerceAtLeast(0L)
                    )
                } else if (playbackState == Player.STATE_ENDED) {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        currentPositionMs = player.duration.coerceAtLeast(0L)
                    )
                    stopPositionUpdater()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Audio playback error"
                )
                stopPositionUpdater()
            }
        })
    }

    fun playFile(audioPath: String) {
        val file = File(audioPath)
        if (!file.exists()) {
            _playerState.value = _playerState.value.copy(
                errorMessage = "Audio file does not exist: $audioPath"
            )
            return
        }

        val mediaItem = MediaItem.fromUri(file.toURI().toString())
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        _playerState.value = PlayerState(
            isPlaying = true,
            currentAudioPath = audioPath,
            durationMs = 0L
        )
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    fun stop() {
        player.stop()
        stopPositionUpdater()
        _playerState.value = PlayerState()
    }

    fun release() {
        stopPositionUpdater()
        player.release()
    }

    private fun startPositionUpdater() {
        stopPositionUpdater()
        positionUpdateJob = scope.launch {
            while (player.isPlaying) {
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L)
                )
                delay(200L)
            }
        }
    }

    private fun stopPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
}
