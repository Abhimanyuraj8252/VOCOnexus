package com.voconexus.app.core.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PlaybackController(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var positionTickerJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        initPlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // Handle Audio Focus automatically
            .setHandleAudioBecomingNoisy(true) // Handle Headphone disconnects automatically
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateStateFromPlayer()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateStateFromPlayer()
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentIndex = player.currentMediaItemIndex
                val playlist = _state.value.playlist
                val currentItem = if (currentIndex in playlist.indices) playlist[currentIndex] else null

                _state.value = _state.value.copy(
                    currentItem = currentItem,
                    playlistIndex = currentIndex
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = _state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.localizedMessage ?: "Playback error"
                )
            }
        })

        exoPlayer = player

        runCatching {
            mediaSession = MediaSession.Builder(context, player).build()
        }
    }

    fun playItem(item: PlayableItem) {
        playPlaylist(listOf(item), 0)
    }

    fun playPlaylist(items: List<PlayableItem>, startIndex: Int = 0) {
        val player = exoPlayer ?: return
        if (items.isEmpty()) return

        val validIndex = startIndex.coerceIn(0, items.lastIndex)
        val mediaItems = items.map { playable ->
            val uri = Uri.fromFile(File(playable.audioPath))
            MediaItem.Builder()
                .setMediaId(playable.chunkId)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(playable.title)
                        .setSubtitle(playable.subtitle)
                        .build()
                )
                .build()
        }

        _state.value = _state.value.copy(
            playlist = items,
            playlistIndex = validIndex,
            currentItem = items[validIndex],
            status = PlaybackStatus.LOADING,
            errorMessage = null
        )

        player.setMediaItems(mediaItems, validIndex, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    fun next() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun previous() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun stop() {
        exoPlayer?.stop()
        stopPositionTicker()
        _state.value = _state.value.copy(
            status = PlaybackStatus.IDLE,
            currentPositionMs = 0L
        )
    }

    fun release() {
        stopPositionTicker()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun updateStateFromPlayer() {
        val player = exoPlayer ?: return

        val status = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            Player.STATE_READY -> if (player.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
            Player.STATE_ENDED -> PlaybackStatus.COMPLETED
            Player.STATE_IDLE -> PlaybackStatus.IDLE
            else -> PlaybackStatus.IDLE
        }

        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)

        _state.value = _state.value.copy(
            status = status,
            currentPositionMs = pos,
            totalDurationMs = dur
        )
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        positionTickerJob = scope.launch {
            while (true) {
                exoPlayer?.let { p ->
                    if (p.isPlaying) {
                        _state.value = _state.value.copy(
                            currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                            totalDurationMs = p.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }
}
