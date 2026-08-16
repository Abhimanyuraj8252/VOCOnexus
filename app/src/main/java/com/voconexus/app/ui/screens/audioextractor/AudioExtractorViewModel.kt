package com.voconexus.app.ui.screens.audioextractor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.voconexus.app.core.tools.audioextractor.AudioExtractorInfo
import com.voconexus.app.core.tools.audioextractor.AudioExtractorParams
import com.voconexus.app.core.tools.audioextractor.AudioExtractorProcessor
import com.voconexus.app.core.tools.audioextractor.AudioExtractorProbe
import com.voconexus.app.core.tools.audioextractor.AudioExtractorResult
import com.voconexus.app.core.tools.speedpitch.AudioAnalyzer
import com.voconexus.app.core.tools.speedpitch.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.pow

data class AudioExtractorUiState(
    // File
    val selectedUri: Uri? = null,
    val info: AudioExtractorInfo? = null,
    val isLoadingFile: Boolean = false,
    val waveform: FloatArray? = null,

    // Live Playback
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,

    // Format & Quality (Default: Original)
    val outputFormat: String = "original",      // "original", "mp3", "aac", "wav", "flac", "m4a", "ogg"
    val audioBitrate: String = "original",       // "original", "320k", "256k", "192k", "128k", "96k"
    val sampleRate: Int = -1,                    // -1 = original, 48000, 44100, 32000, 16000
    val channels: Int = -1,                      // -1 = original, 1 = mono, 2 = stereo

    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,

    // Volume & Fade
    val volumeDb: Float = 0f,
    val normalize: Boolean = false,
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,

    // Speed & Pitch
    val speedMultiplier: Float = 1.0f,
    val speedText: String = "1.000",
    val pitchSemitones: Float = 0.0f,
    val pitchText: String = "0.0",
    val isPitchLocked: Boolean = true,
    val reverse: Boolean = false,

    // Custom Filename & EQ
    val customFileName: String = "",
    val eqPreset: String = "flat",

    // Processing
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,

    // Result
    val exportResult: ExtractedAudioResult? = null,
    val errorMessage: String? = null
)

data class ExtractedAudioResult(
    val outputFile: File,
    val outputUri: Uri?
)

class AudioExtractorViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(AudioExtractorUiState())
    val state: StateFlow<AudioExtractorUiState> = _state.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var positionUpdateJob: Job? = null
    private var processingJob: Job? = null

    init {
        setupPlayer()
    }

    private fun setupPlayer() {
        try {
            player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
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
            while (isActive) {
                player?.let { p ->
                    _state.update { it.copy(currentPositionMs = p.currentPosition) }
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingFile = true, selectedUri = uri, errorMessage = null) }

            val info = AudioExtractorProbe.probe(context, uri)

            player?.let { p ->
                p.stop()
                p.setMediaItem(MediaItem.fromUri(uri))
                p.prepare()
            }

            _state.update {
                it.copy(
                    info = info,
                    isLoadingFile = false,
                    currentPositionMs = 0L,
                    trimStartMs = 0L,
                    trimEndMs = info.durationMs,
                    outputFormat = "original",
                    audioBitrate = "original",
                    sampleRate = -1,
                    channels = -1,
                    volumeDb = 0f,
                    normalize = false,
                    fadeInSec = 0f,
                    fadeOutSec = 0f,
                    speedMultiplier = 1.0f,
                    pitchSemitones = 0f,
                    exportResult = null
                )
            }

            // Extract waveform in background
            val waveform = AudioAnalyzer.extractWaveform(context, uri)
            _state.update { it.copy(waveform = waveform) }
        }
    }

    // ── Live Playback Controls ────────────────────────────────────────────────

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun seekTo(ms: Long) {
        player?.let { p ->
            val clamped = ms.coerceIn(0L, p.duration.coerceAtLeast(1L))
            p.seekTo(clamped)
            _state.update { it.copy(currentPositionMs = clamped) }
        }
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

    // ── Format & Quality ──────────────────────────────────────────────────────

    fun onOutputFormatChanged(fmt: String) { _state.update { it.copy(outputFormat = fmt) } }
    fun onAudioBitrateChanged(br: String) { _state.update { it.copy(audioBitrate = br) } }
    fun onSampleRateChanged(sr: Int) { _state.update { it.copy(sampleRate = sr) } }
    fun onChannelsChanged(ch: Int) { _state.update { it.copy(channels = ch) } }

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
        val dur = _state.value.info?.durationMs ?: -1L
        _state.update { it.copy(trimStartMs = 0L, trimEndMs = dur) }
    }

    // ── Volume & Fade ─────────────────────────────────────────────────────────

    fun onVolumeChanged(db: Float) { _state.update { it.copy(volumeDb = db.coerceIn(-30f, 30f)) } }
    fun onNormalizeToggled() { _state.update { it.copy(normalize = !it.normalize) } }
    fun onFadeInChanged(sec: Float) { _state.update { it.copy(fadeInSec = sec.coerceIn(0f, 10f)) } }
    fun onFadeOutChanged(sec: Float) { _state.update { it.copy(fadeOutSec = sec.coerceIn(0f, 10f)) } }

    // ── Speed & Pitch ─────────────────────────────────────────────────────────

    // ── Speed & Pitch ─────────────────────────────────────────────────────────

    fun onSpeedChanged(speed: Float) {
        val clamped = speed.coerceIn(0.1f, 8.0f)
        _state.update {
            it.copy(
                speedMultiplier = clamped,
                speedText = String.format(java.util.Locale.US, "%.3f", clamped)
            )
        }
        applyPlaybackParameters()
    }

    fun onSpeedTextChanged(text: String) {
        _state.update { it.copy(speedText = text) }
        text.toFloatOrNull()?.let { speed ->
            val clamped = speed.coerceIn(0.1f, 8.0f)
            _state.update { it.copy(speedMultiplier = clamped) }
            applyPlaybackParameters()
        }
    }

    fun onPitchChanged(semitones: Float) {
        val clamped = semitones.coerceIn(-24f, 24f)
        _state.update {
            it.copy(
                pitchSemitones = clamped,
                pitchText = String.format(java.util.Locale.US, "%.1f", clamped)
            )
        }
        applyPlaybackParameters()
    }

    fun onPitchTextChanged(text: String) {
        _state.update { it.copy(pitchText = text) }
        text.toFloatOrNull()?.let { pitch ->
            val clamped = pitch.coerceIn(-24f, 24f)
            _state.update { it.copy(pitchSemitones = clamped) }
            applyPlaybackParameters()
        }
    }

    fun onPitchLockToggled() {
        _state.update { it.copy(isPitchLocked = !it.isPitchLocked, pitchSemitones = 0f, pitchText = "0.0") }
        applyPlaybackParameters()
    }

    fun onReverseToggled() { _state.update { it.copy(reverse = !it.reverse) } }
    fun onCustomFileNameChanged(name: String) { _state.update { it.copy(customFileName = name) } }
    fun onEqPresetChanged(preset: String) { _state.update { it.copy(eqPreset = preset) } }

    private fun applyPlaybackParameters() {
        val speed = _state.value.speedMultiplier
        val pitchSemitones = _state.value.pitchSemitones
        val pitchLocked = _state.value.isPitchLocked

        val pitchFactor = if (pitchLocked) {
            1.0f
        } else {
            2.0.pow(pitchSemitones / 12.0).toFloat()
        }

        player?.playbackParameters = PlaybackParameters(speed, pitchFactor)
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun onStartProcessing() {
        val s = _state.value
        val uri = s.selectedUri ?: return
        val info = s.info ?: return

        player?.pause()
        processingJob?.cancel()

        processingJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, processingProgress = 0f, errorMessage = null, exportResult = null) }

            val params = AudioExtractorParams(
                inputUri = uri,
                inputFileName = info.fileName,
                sourceDurationMs = info.durationMs,
                originalBitrateBps = info.bitrateBps,
                originalAudioCodec = info.audioCodec,
                outputFormat = s.outputFormat,
                audioBitrate = s.audioBitrate,
                sampleRate = s.sampleRate,
                channels = s.channels,
                trimStartMs = s.trimStartMs,
                trimEndMs = if (s.trimEndMs == info.durationMs) -1L else s.trimEndMs,
                volumeDb = s.volumeDb,
                normalize = s.normalize,
                fadeInSec = s.fadeInSec,
                fadeOutSec = s.fadeOutSec,
                speedMultiplier = s.speedMultiplier,
                pitchSemitones = s.pitchSemitones,
                isPitchLocked = s.isPitchLocked,
                reverse = s.reverse,
                customFileName = s.customFileName,
                eqPreset = s.eqPreset
            )

            val result = AudioExtractorProcessor.process(context, params) { progress ->
                _state.update { it.copy(processingProgress = progress) }
            }

            when (result) {
                is AudioExtractorResult.Success -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            processingProgress = 1f,
                            exportResult = ExtractedAudioResult(result.outputFile, result.outputUri)
                        )
                    }
                }
                is AudioExtractorResult.Failure -> {
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

    fun onDismissResult() {
        _state.update { it.copy(exportResult = null, errorMessage = null) }
    }

    class Factory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioExtractorViewModel(context.applicationContext) as T
        }
    }
}
