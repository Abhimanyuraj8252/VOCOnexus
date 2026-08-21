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
 *
 * Supports both single-file and multi-file (playlist) playback.
 * Multi-file playback enables gapless playback across all generated
 * chunks for a Part, with a continuous seekbar across all chunks.
 */
class AudioPreviewPlayer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeVoiceId = MutableStateFlow<String?>(null)
    val activeVoiceId: StateFlow<String?> = _activeVoiceId.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** Index of the currently-playing media item (chunk) within the playlist. */
    private val _activeChunkIndex = MutableStateFlow(0)
    val activeChunkIndex: StateFlow<Int> = _activeChunkIndex.asStateFlow()

    /** Total number of media items (chunks) in the current playlist. */
    private val _totalChunksCount = MutableStateFlow(0)
    val totalChunksCount: StateFlow<Int> = _totalChunksCount.asStateFlow()

    private var playlistChunkStartOffsets = listOf<Long>()
    private var playlistChunkDurations = listOf<Long>()
    private var totalPlaylistDurationMs = 0L

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    val itemIndex = player.currentMediaItemIndex
                    val itemPos = player.currentPosition.coerceAtLeast(0L)
                    val offset = playlistChunkStartOffsets.getOrNull(itemIndex) ?: 0L

                    _currentPositionMs.value = if (totalPlaylistDurationMs > 0L) {
                        (offset + itemPos).coerceIn(0L, totalPlaylistDurationMs)
                    } else {
                        itemPos
                    }

                    _durationMs.value = if (totalPlaylistDurationMs > 0L) {
                        totalPlaylistDurationMs
                    } else {
                        player.duration.coerceAtLeast(0L)
                    }

                    _activeChunkIndex.value = itemIndex
                    mainHandler.postDelayed(this, 100L)
                }
            }
        }
    }

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
                    if (playing) {
                        mainHandler.post(updateProgressRunnable)
                    } else {
                        mainHandler.removeCallbacks(updateProgressRunnable)
                        if (playbackState == Player.STATE_ENDED) {
                            _activeVoiceId.value = null
                            _currentPositionMs.value = 0L
                            _activeChunkIndex.value = 0
                            _totalChunksCount.value = 0
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _activeVoiceId.value = null
                        _currentPositionMs.value = 0L
                        _activeChunkIndex.value = 0
                        _totalChunksCount.value = 0
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    exoPlayer?.let { player ->
                        _activeChunkIndex.value = player.currentMediaItemIndex
                    }
                }
            })
        }
    }

    /**
     * Play a single preview audio file. Safe to call from any thread.
     */
    fun playPreview(voiceId: String, audioFile: File, pitch: Float = 1.0f) {
        playFiles(voiceId = voiceId, files = listOf(audioFile), pitch = pitch)
    }

    /**
     * Play multiple audio files as a gapless playlist.
     *
     * ExoPlayer natively handles multi-file playback. This implementation maps
     * per-chunk durations into a cumulative playlist timeline so the seekbar and
     * currentPositionMs operate across all chunks seamlessly as a single continuous track.
     *
     * Safe to call from any thread.
     */
    fun playFiles(voiceId: String, files: List<File>, pitch: Float = 1.0f) {
        if (files.isEmpty()) return

        val audioValidator = com.voconexus.app.core.generation.audio.AudioValidator()
        val durations = files.map { file ->
            val result = audioValidator.validateWavFile(file)
            if (result.isValid && result.durationMs > 0L) result.durationMs else 1000L
        }

        val startOffsets = mutableListOf<Long>()
        var accumulated = 0L
        durations.forEach { dur ->
            startOffsets.add(accumulated)
            accumulated += dur
        }

        mainHandler.post {
            playlistChunkStartOffsets = startOffsets
            playlistChunkDurations = durations
            totalPlaylistDurationMs = accumulated

            _activeVoiceId.value = voiceId
            _currentPositionMs.value = 0L
            _durationMs.value = accumulated
            _activeChunkIndex.value = 0
            _totalChunksCount.value = files.size

            if (exoPlayer == null) {
                initializePlayer()
            }

            val mediaItems = files.map { file ->
                MediaItem.fromUri(android.net.Uri.fromFile(file))
            }

            exoPlayer?.apply {
                stop()
                clearMediaItems()
                setPlaybackParameters(androidx.media3.common.PlaybackParameters(1.0f, pitch))
                addMediaItems(mediaItems)
                prepare()
                playWhenReady = true
            }
        }
    }

    fun seekTo(globalPositionMs: Long) {
        mainHandler.post {
            val targetPos = globalPositionMs.coerceIn(0L, totalPlaylistDurationMs.coerceAtLeast(0L))
            if (playlistChunkStartOffsets.isNotEmpty()) {
                var targetIndex = 0
                for (i in playlistChunkStartOffsets.indices.reversed()) {
                    if (targetPos >= playlistChunkStartOffsets[i]) {
                        targetIndex = i
                        break
                    }
                }
                val chunkOffset = playlistChunkStartOffsets[targetIndex]
                val intraChunkPos = (targetPos - chunkOffset).coerceAtLeast(0L)
                exoPlayer?.seekTo(targetIndex, intraChunkPos)
                _currentPositionMs.value = targetPos
            } else {
                exoPlayer?.seekTo(targetPos)
                _currentPositionMs.value = targetPos
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
            _currentPositionMs.value = 0L
            _activeChunkIndex.value = 0
            _totalChunksCount.value = 0
        }
    }

    /**
     * Release ExoPlayer. Safe to call from any thread.
     */
    fun release() {
        mainHandler.post {
            mainHandler.removeCallbacks(updateProgressRunnable)
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (_: Exception) {}
            exoPlayer = null
            _isPlaying.value = false
            _activeVoiceId.value = null
            _activeChunkIndex.value = 0
            _totalChunksCount.value = 0
        }
    }
}
