package com.voconexus.app.ui.screens.speedpitch

import android.content.Context
import android.net.Uri
import kotlin.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.voconexus.app.core.tools.speedpitch.AudioAnalyzer
import com.voconexus.app.core.tools.speedpitch.MediaInfo
import com.voconexus.app.core.tools.speedpitch.MediaInfoProbe
import com.voconexus.app.core.tools.speedpitch.MediaType
import com.voconexus.app.core.tools.speedpitch.ProcessingParams
import com.voconexus.app.core.tools.speedpitch.ProcessingResult
import com.voconexus.app.core.tools.speedpitch.SpeedPitchProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

data class SpeedPitchUiState(
    // File
    val selectedUri: Uri? = null,
    val mediaInfo: MediaInfo? = null,
    val isLoadingFile: Boolean = false,
    val waveform: FloatArray? = null,

    // Live Playback
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,

    // Speed
    val speedMultiplier: Float = 1.0f,
    val speedText: String = "1.000",

    // Pitch
    val pitchSemitones: Float = 0.0f,
    val pitchText: String = "0.0",
    val isPitchLocked: Boolean = true,

    // Duration target
    val targetHours: Int = 0,
    val targetMinutes: Int = 0,
    val targetSeconds: Int = 0,
    val isDurationMode: Boolean = false,

    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,

    // Fade
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,

    // Volume
    val volumeDb: Float = 0f,
    val normalize: Boolean = false,

    // EQ
    val eqBass: Float = 0f,
    val eqLowMid: Float = 0f,
    val eqMid: Float = 0f,
    val eqHighMid: Float = 0f,
    val eqTreble: Float = 0f,
    val isEqExpanded: Boolean = false,

    // Channels / sample rate
    val outputChannels: Int = -1,        // -1 = original
    val outputSampleRate: Int = -1,      // -1 = original

    // Advanced
    val reverse: Boolean = false,
    val outputFormat: String = "",       // "" = same as input
    val audioBitrate: String = "",
    val videoBitrate: String = "",
    val preserveMetadata: Boolean = true,

    // Processing
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val processingLog: String = "",

    // Result
    val exportResult: ExportResult? = null,
    val errorMessage: String? = null
)

data class ExportResult(
    val outputFile: File,
    val outputUri: Uri?,
    val mediaType: MediaType
)

class SpeedPitchViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(SpeedPitchUiState())
    val state: StateFlow<SpeedPitchUiState> = _state.asStateFlow()

    private var processingJob: Job? = null
    private var positionUpdateJob: Job? = null

    // ExoPlayer instance for live preview
    var player: ExoPlayer? = null
        private set

    init {
        initPlayer()
    }

    private fun initPlayer() {
        try {
            player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        if (isPlaying) {
                            startPositionUpdates()
                        } else {
                            stopPositionUpdates()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                            stopPositionUpdates()
                        }
                    }
                })
            }
        } catch (_: Throwable) {}
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    if (p.isPlaying) {
                        _state.update { it.copy(currentPositionMs = p.currentPosition.coerceAtLeast(0L)) }
                    }
                }
                delay(150)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    // ── Live Playback Controls ───────────────────────────────────────────────

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _state.update { it.copy(currentPositionMs = positionMs) }
    }

    fun rewind10s() {
        player?.let { p ->
            val newPos = (p.currentPosition - 10000L).coerceAtLeast(0L)
            p.seekTo(newPos)
            _state.update { it.copy(currentPositionMs = newPos) }
        }
    }

    fun forward10s() {
        player?.let { p ->
            val maxDur = p.duration.coerceAtLeast(1L)
            val newPos = (p.currentPosition + 10000L).coerceAtMost(maxDur)
            p.seekTo(newPos)
            _state.update { it.copy(currentPositionMs = newPos) }
        }
    }

    private fun applyPlaybackParameters() {
        val p = player ?: return
        val s = _state.value
        val speed = s.speedMultiplier.coerceIn(0.1f, 8.0f)
        val pitchFactor = if (s.isPitchLocked) 1.0f else 2.0.pow(s.pitchSemitones / 12.0).toFloat().coerceIn(0.1f, 4.0f)
        try {
            p.playbackParameters = PlaybackParameters(speed, pitchFactor)
        } catch (_: Throwable) {}
    }

    // ── File Loading ─────────────────────────────────────────────────────────

    fun onFileSelected(uri: Uri) {
        player?.pause()
        _state.update { it.copy(isLoadingFile = true, waveform = null, errorMessage = null, isPlaying = false, currentPositionMs = 0L) }

        viewModelScope.launch {
            val info = MediaInfoProbe.probe(context, uri)
            val trimEnd = info.durationMs.takeIf { it > 0 } ?: -1L

            // Prepare player with selected file
            try {
                player?.let { p ->
                    p.setMediaItem(MediaItem.fromUri(uri))
                    p.prepare()
                    applyPlaybackParameters()
                }
            } catch (_: Throwable) {}

            _state.update { s ->
                s.copy(
                    selectedUri = uri,
                    mediaInfo = info,
                    isLoadingFile = false,
                    trimEndMs = trimEnd,
                    outputFormat = "",
                    // Reset controls
                    speedMultiplier = 1.0f,
                    speedText = "1.000",
                    pitchSemitones = 0f,
                    pitchText = "0.0",
                    isPitchLocked = true,
                    targetHours = 0, targetMinutes = 0, targetSeconds = 0,
                    trimStartMs = 0L,
                    fadeInSec = 0f, fadeOutSec = 0f,
                    volumeDb = 0f, normalize = false,
                    reverse = false, exportResult = null
                )
            }
            // Extract waveform in background for audio/video files
            if (info.mediaType == MediaType.AUDIO || info.mediaType == MediaType.VIDEO) {
                val waveform = AudioAnalyzer.extractWaveform(context, uri)
                _state.update { it.copy(waveform = waveform) }
            }
        }
    }

    // ── Speed ─────────────────────────────────────────────────────────────────

    fun onSpeedSliderChanged(speed: Float) {
        val clamped = speed.coerceIn(0.10f, 8.0f)
        _state.update { it.copy(speedMultiplier = clamped, speedText = String.format("%.3f", clamped), isDurationMode = false) }
        applyPlaybackParameters()
    }

    fun onSpeedTextChanged(text: String) {
        _state.update { it.copy(speedText = text) }
        text.toFloatOrNull()?.let { v ->
            val clamped = v.coerceIn(0.10f, 8.0f)
            _state.update { it.copy(speedMultiplier = clamped, isDurationMode = false) }
            applyPlaybackParameters()
        }
    }

    fun onPresetSpeed(speed: Float) {
        _state.update { it.copy(speedMultiplier = speed, speedText = String.format("%.3f", speed), isDurationMode = false) }
        applyPlaybackParameters()
    }

    // ── Pitch ─────────────────────────────────────────────────────────────────

    fun onPitchLockToggled() {
        _state.update { it.copy(isPitchLocked = !it.isPitchLocked, pitchSemitones = 0f, pitchText = "0.0") }
        applyPlaybackParameters()
    }

    fun onPitchSliderChanged(semitones: Float) {
        _state.update { it.copy(pitchSemitones = semitones, pitchText = String.format("%.1f", semitones)) }
        applyPlaybackParameters()
    }

    fun onPitchTextChanged(text: String) {
        _state.update { it.copy(pitchText = text) }
        text.toFloatOrNull()?.let { v ->
            val clamped = v.coerceIn(-24f, 24f)
            _state.update { it.copy(pitchSemitones = clamped) }
            applyPlaybackParameters()
        }
    }

    // ── Duration Target ────────────────────────────────────────────────────────

    fun onTargetHoursChanged(h: Int) { _state.update { it.copy(targetHours = h.coerceIn(0, 23)) } }
    fun onTargetMinutesChanged(m: Int) { _state.update { it.copy(targetMinutes = m.coerceIn(0, 59)) } }
    fun onTargetSecondsChanged(s: Int) { _state.update { it.copy(targetSeconds = s.coerceIn(0, 59)) } }

    fun onAutoCalculateSpeed() {
        val info = _state.value.mediaInfo ?: return
        val s = _state.value
        val targetMs = ((s.targetHours * 3600L) + (s.targetMinutes * 60L) + s.targetSeconds) * 1000L
        if (targetMs <= 0 || info.durationMs <= 0) return
        val computedSpeed = (info.durationMs.toFloat() / targetMs).coerceIn(0.10f, 8.0f)
        _state.update { it.copy(speedMultiplier = computedSpeed, speedText = String.format("%.3f", computedSpeed), isDurationMode = true) }
        applyPlaybackParameters()
    }

    // Computed output duration in ms
    fun computedOutputDurationMs(): Long {
        val info = _state.value.mediaInfo ?: return 0L
        val s = _state.value
        val trimDuration = if (s.trimEndMs > 0) s.trimEndMs - s.trimStartMs else info.durationMs
        return if (s.speedMultiplier > 0) (trimDuration / s.speedMultiplier).toLong() else 0L
    }

    // ── Trim ──────────────────────────────────────────────────────────────────

    fun onTrimStartChanged(ms: Long) {
        val endMs = _state.value.trimEndMs
        if (endMs < 0 || ms < endMs) _state.update { it.copy(trimStartMs = ms.coerceAtLeast(0L)) }
    }

    fun onTrimEndChanged(ms: Long) {
        val startMs = _state.value.trimStartMs
        if (ms > startMs || ms < 0) _state.update { it.copy(trimEndMs = ms) }
    }

    fun onSetTrimStartToCurrent() {
        val current = _state.value.currentPositionMs
        val endMs = _state.value.trimEndMs
        if (endMs < 0 || current < endMs) {
            _state.update { it.copy(trimStartMs = current) }
        }
    }

    fun onSetTrimEndToCurrent() {
        val current = _state.value.currentPositionMs
        val startMs = _state.value.trimStartMs
        if (current > startMs) {
            _state.update { it.copy(trimEndMs = current) }
        }
    }

    fun onResetTrim() {
        val dur = _state.value.mediaInfo?.durationMs ?: -1L
        _state.update { it.copy(trimStartMs = 0L, trimEndMs = dur) }
    }

    // ── Fade ──────────────────────────────────────────────────────────────────

    fun onFadeInChanged(sec: Float) { _state.update { it.copy(fadeInSec = sec.coerceIn(0f, 10f)) } }
    fun onFadeOutChanged(sec: Float) { _state.update { it.copy(fadeOutSec = sec.coerceIn(0f, 10f)) } }

    // ── Volume ────────────────────────────────────────────────────────────────

    fun onVolumeChanged(db: Float) {
        _state.update { it.copy(volumeDb = db.coerceIn(-30f, 30f)) }
        // Volume adjustment on player
        val volFactor = 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 2f)
        player?.volume = volFactor
    }

    fun onNormalizeToggled() { _state.update { it.copy(normalize = !it.normalize) } }

    // ── Equalizer ─────────────────────────────────────────────────────────────

    fun onEqBassChanged(db: Float) { _state.update { it.copy(eqBass = db.coerceIn(-15f, 15f)) } }
    fun onEqLowMidChanged(db: Float) { _state.update { it.copy(eqLowMid = db.coerceIn(-15f, 15f)) } }
    fun onEqMidChanged(db: Float) { _state.update { it.copy(eqMid = db.coerceIn(-15f, 15f)) } }
    fun onEqHighMidChanged(db: Float) { _state.update { it.copy(eqHighMid = db.coerceIn(-15f, 15f)) } }
    fun onEqTrebleChanged(db: Float) { _state.update { it.copy(eqTreble = db.coerceIn(-15f, 15f)) } }
    fun onResetEq() { _state.update { it.copy(eqBass = 0f, eqLowMid = 0f, eqMid = 0f, eqHighMid = 0f, eqTreble = 0f) } }
    fun onEqExpandToggled() { _state.update { it.copy(isEqExpanded = !it.isEqExpanded) } }

    // ── Advanced ──────────────────────────────────────────────────────────────

    fun onChannelsChanged(ch: Int) { _state.update { it.copy(outputChannels = ch) } }
    fun onSampleRateChanged(sr: Int) { _state.update { it.copy(outputSampleRate = sr) } }
    fun onReverseToggled() { _state.update { it.copy(reverse = !it.reverse) } }
    fun onOutputFormatChanged(fmt: String) { _state.update { it.copy(outputFormat = fmt) } }
    fun onAudioBitrateChanged(br: String) { _state.update { it.copy(audioBitrate = br) } }
    fun onVideoBitrateChanged(br: String) { _state.update { it.copy(videoBitrate = br) } }
    fun onPreserveMetadataToggled() { _state.update { it.copy(preserveMetadata = !it.preserveMetadata) } }

    // ── Export ────────────────────────────────────────────────────────────────

    fun onStartProcessing() {
        val s = _state.value
        val uri = s.selectedUri ?: return
        val info = s.mediaInfo ?: return

        player?.pause()
        processingJob?.cancel()

        processingJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, processingProgress = 0f, errorMessage = null, exportResult = null) }

            val params = ProcessingParams(
                inputUri = uri,
                inputFileName = info.fileName,
                mediaType = info.mediaType,
                sourceDurationMs = info.durationMs,
                originalBitrateBps = info.bitrate,
                speedMultiplier = s.speedMultiplier,
                pitchSemitones = s.pitchSemitones,
                isPitchLocked = s.isPitchLocked,
                trimStartMs = s.trimStartMs,
                trimEndMs = if (s.trimEndMs == info.durationMs) -1L else s.trimEndMs,
                fadeInDurationSec = s.fadeInSec,
                fadeOutDurationSec = s.fadeOutSec,
                volumeDb = s.volumeDb,
                normalize = s.normalize,
                eqBass = s.eqBass,
                eqLowMid = s.eqLowMid,
                eqMid = s.eqMid,
                eqHighMid = s.eqHighMid,
                eqTreble = s.eqTreble,
                channels = s.outputChannels,
                sampleRate = s.outputSampleRate,
                reverse = s.reverse,
                outputFormat = s.outputFormat,
                audioBitrate = s.audioBitrate,
                videoBitrate = s.videoBitrate,
                preserveMetadata = s.preserveMetadata
            )

            val result = SpeedPitchProcessor.process(context, params) { progress ->
                _state.update { it.copy(processingProgress = progress) }
            }

            when (result) {
                is ProcessingResult.Success -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            processingProgress = 1f,
                            exportResult = ExportResult(result.outputFile, result.outputUri, info.mediaType)
                        )
                    }
                }
                is ProcessingResult.Failure -> {
                    _state.update {
                        it.copy(isProcessing = false, processingProgress = 0f, errorMessage = result.error)
                    }
                }
            }
        }
    }

    fun onCancelProcessing() {
        processingJob?.cancel()
        _state.update { it.copy(isProcessing = false, processingProgress = 0f) }
    }

    fun onDismissResult() { _state.update { it.copy(exportResult = null, errorMessage = null) } }

    fun onClearFile() {
        player?.stop()
        player?.clearMediaItems()
        _state.update { SpeedPitchUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SpeedPitchViewModel(context.applicationContext) as T
    }
}

// Helpers
fun Long.toHms(): Triple<Int, Int, Int> {
    val totalSec = this / 1000
    return Triple((totalSec / 3600).toInt(), ((totalSec % 3600) / 60).toInt(), (totalSec % 60).toInt())
}

fun formatDurationMs(ms: Long): String {
    val (h, m, s) = ms.toHms()
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

fun formatDurationFull(ms: Long): String {
    val (h, m, s) = ms.toHms()
    return String.format("%02d:%02d:%02d", h, m, s)
}
