package com.voconexus.app.core.playback

enum class PlaybackStatus {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    BUFFERING,
    COMPLETED,
    ERROR
}

data class PlayableItem(
    val chunkId: String,
    val partId: String = "",
    val projectId: String = "",
    val title: String,
    val subtitle: String = "",
    val audioPath: String,
    val durationMs: Long = 0L
)

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentItem: PlayableItem? = null,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playlist: List<PlayableItem> = emptyList(),
    val playlistIndex: Int = -1,
    val skipUnavailable: Boolean = false,
    val errorMessage: String? = null
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val hasActiveMedia: Boolean get() = currentItem != null
    val progressFraction: Float get() = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs.toFloat() else 0f
}
