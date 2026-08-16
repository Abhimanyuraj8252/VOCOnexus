package com.voconexus.app.ui.screens.convertercompressor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.voconexus.app.core.tools.convertercompressor.ConverterCompressorInfo
import com.voconexus.app.core.tools.convertercompressor.ConverterCompressorParams
import com.voconexus.app.core.tools.convertercompressor.ConverterCompressorProcessor
import com.voconexus.app.core.tools.convertercompressor.ConverterCompressorProbe
import com.voconexus.app.core.tools.convertercompressor.ConverterCompressorResult
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
import java.util.Locale
import kotlin.math.pow

data class ConverterCompressorUiState(
    // File
    val selectedUri: Uri? = null,
    val info: ConverterCompressorInfo? = null,
    val isLoadingFile: Boolean = false,
    val waveform: FloatArray? = null,

    // Live Playback
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,

    // Compression Mode & Real-time Estimation
    val compressionMode: String = "balanced",   // "original", "balanced", "high", "custom_mb"
    val targetSizeMb: Float = 0f,
    val customSizeMbText: String = "",
    val estimatedOutputSizeBytes: Long = 0L,
    val estimatedReductionPercent: Int = 0,

    // Output Formats & Resolutions
    val outputFormat: String = "original",     // "original", "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "m4v", "wmv", "mpg", "mp3", "aac", "wav", "flac", "ogg", "opus", "ac3", "aiff", "amr", "wma"
    val targetResolution: String = "original",  // "original", "4k", "1080p", "720p", "480p", "360p"
    val targetFps: Int = -1,                    // -1 = original, 60, 30, 24

    // Audio Options
    val audioBitrate: String = "original",      // "original", "320k", "256k", "192k", "128k", "96k"
    val sampleRate: Int = -1,                   // -1 = original, 48000, 44100, 32000, 16000
    val channels: Int = -1,                     // -1 = original, 1 = mono, 2 = stereo

    // Speed & Pitch
    val speedMultiplier: Float = 1.0f,
    val speedText: String = "1.000",
    val pitchSemitones: Float = 0.0f,
    val pitchText: String = "0.0",
    val isPitchLocked: Boolean = true,
    val reverse: Boolean = false,
    val volumeDb: Float = 0f,
    val eqPreset: String = "flat",

    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,

    // Custom Filename
    val customFileName: String = "",

    // Processing & Result
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val exportResult: ConvertedMediaResult? = null,
    val errorMessage: String? = null
)

data class ConvertedMediaResult(
    val outputFile: File,
    val outputUri: Uri?
)

class ConverterCompressorViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(ConverterCompressorUiState())
    val state: StateFlow<ConverterCompressorUiState> = _state.asStateFlow()

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

            val info = ConverterCompressorProbe.probe(context, uri)

            player?.let { p ->
                p.stop()
                p.setMediaItem(MediaItem.fromUri(uri))
                p.prepare()
            }

            val defaultTargetMb = ((info.fileSizeBytes * 0.60f) / (1024f * 1024f)).coerceAtLeast(5f)

            _state.update {
                it.copy(
                    info = info,
                    isLoadingFile = false,
                    currentPositionMs = 0L,
                    trimStartMs = 0L,
                    trimEndMs = info.durationMs,
                    compressionMode = "balanced",
                    targetSizeMb = defaultTargetMb,
                    customSizeMbText = String.format(Locale.US, "%.1f", defaultTargetMb),
                    outputFormat = "original",
                    targetResolution = "original",
                    targetFps = -1,
                    audioBitrate = "original",
                    sampleRate = -1,
                    channels = -1,
                    speedMultiplier = 1.0f,
                    speedText = "1.000",
                    pitchSemitones = 0f,
                    pitchText = "0.0",
                    isPitchLocked = true,
                    reverse = false,
                    volumeDb = 0f,
                    eqPreset = "flat",
                    customFileName = "",
                    exportResult = null
                )
            }

            recalculateEstimation()

            // Waveform in background
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

    // ── Compression Mode & Options ───────────────────────────────────────────

    fun onCompressionModeChanged(mode: String) {
        _state.update { it.copy(compressionMode = mode) }
        recalculateEstimation()
    }

    fun onTargetSizeMbChanged(mb: Float) {
        _state.update {
            it.copy(
                targetSizeMb = mb,
                customSizeMbText = String.format(Locale.US, "%.1f", mb)
            )
        }
        recalculateEstimation()
    }

    fun onCustomSizeMbTextChanged(text: String) {
        _state.update { it.copy(customSizeMbText = text) }
        text.toFloatOrNull()?.let { mb ->
            _state.update { it.copy(targetSizeMb = mb) }
            recalculateEstimation()
        }
    }

    fun onOutputFormatChanged(fmt: String) {
        _state.update { it.copy(outputFormat = fmt) }
        recalculateEstimation()
    }

    fun onTargetResolutionChanged(res: String) {
        _state.update { it.copy(targetResolution = res) }
        recalculateEstimation()
    }

    fun onTargetFpsChanged(fps: Int) {
        _state.update { it.copy(targetFps = fps) }
        recalculateEstimation()
    }

    fun onAudioBitrateChanged(br: String) { _state.update { it.copy(audioBitrate = br) } }
    fun onSampleRateChanged(sr: Int) { _state.update { it.copy(sampleRate = sr) } }
    fun onChannelsChanged(ch: Int) { _state.update { it.copy(channels = ch) } }

    // ── Trim & Speed & FX ─────────────────────────────────────────────────────

    fun onTrimStartChanged(ms: Long) {
        val endMs = _state.value.trimEndMs
        if (endMs < 0 || ms < endMs) {
            _state.update { it.copy(trimStartMs = ms.coerceAtLeast(0L)) }
            recalculateEstimation()
        }
    }

    fun onTrimEndChanged(ms: Long) {
        val startMs = _state.value.trimStartMs
        if (ms > startMs || ms < 0) {
            _state.update { it.copy(trimEndMs = ms) }
            recalculateEstimation()
        }
    }

    fun onSetTrimStartToCurrent() {
        val current = _state.value.currentPositionMs
        val endMs = _state.value.trimEndMs
        if (endMs < 0 || current < endMs) {
            _state.update { it.copy(trimStartMs = current) }
            recalculateEstimation()
        }
    }

    fun onSetTrimEndToCurrent() {
        val current = _state.value.currentPositionMs
        val startMs = _state.value.trimStartMs
        if (current > startMs) {
            _state.update { it.copy(trimEndMs = current) }
            recalculateEstimation()
        }
    }

    fun onResetTrim() {
        val dur = _state.value.info?.durationMs ?: -1L
        _state.update { it.copy(trimStartMs = 0L, trimEndMs = dur) }
        recalculateEstimation()
    }

    fun onSpeedChanged(speed: Float) {
        val clamped = speed.coerceIn(0.1f, 8.0f)
        _state.update {
            it.copy(
                speedMultiplier = clamped,
                speedText = String.format(Locale.US, "%.3f", clamped)
            )
        }
        applyPlaybackParameters()
        recalculateEstimation()
    }

    fun onSpeedTextChanged(text: String) {
        _state.update { it.copy(speedText = text) }
        text.toFloatOrNull()?.let { speed ->
            val clamped = speed.coerceIn(0.1f, 8.0f)
            _state.update { it.copy(speedMultiplier = clamped) }
            applyPlaybackParameters()
            recalculateEstimation()
        }
    }

    fun onPitchChanged(semitones: Float) {
        val clamped = semitones.coerceIn(-24f, 24f)
        _state.update {
            it.copy(
                pitchSemitones = clamped,
                pitchText = String.format(Locale.US, "%.1f", clamped)
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
    fun onVolumeChanged(db: Float) { _state.update { it.copy(volumeDb = db.coerceIn(-30f, 30f)) } }
    fun onEqPresetChanged(preset: String) { _state.update { it.copy(eqPreset = preset) } }
    fun onCustomFileNameChanged(name: String) { _state.update { it.copy(customFileName = name) } }

    private fun applyPlaybackParameters() {
        val speed = _state.value.speedMultiplier
        val pitchSemitones = _state.value.pitchSemitones
        val pitchLocked = _state.value.isPitchLocked
        val pitchFactor = if (pitchLocked) 1.0f else 2.0.pow(pitchSemitones / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(speed, pitchFactor)
    }

    // ── Estimation Calculator ─────────────────────────────────────────────────

    private fun recalculateEstimation() {
        val info = _state.value.info ?: return
        val origBytes = info.fileSizeBytes.coerceAtLeast(1L)
        val origDurMs = info.durationMs.coerceAtLeast(1000L)

        val trimDurMs = if (_state.value.trimEndMs > 0 && _state.value.trimEndMs > _state.value.trimStartMs) {
            (_state.value.trimEndMs - _state.value.trimStartMs)
        } else origDurMs

        val speed = _state.value.speedMultiplier.coerceIn(0.1f, 8.0f)
        val targetDurSec = (trimDurMs / 1000f) / speed
        val origDurSec = origDurMs / 1000f

        val durRatio = targetDurSec / origDurSec

        val resRatio = when (_state.value.targetResolution.lowercase()) {
            "4k" -> 2.5f
            "1080p" -> 1.0f
            "720p" -> 0.45f
            "480p" -> 0.20f
            "360p" -> 0.12f
            "240p" -> 0.06f
            else -> 1.0f
        }

        val modeRatio = when (_state.value.compressionMode.lowercase()) {
            "original", "ultra_lossless" -> 0.95f
            "visually_lossless" -> 0.75f
            "balanced" -> 0.50f
            "high" -> 0.30f
            "extreme" -> 0.18f
            "custom_mb" -> -1f
            else -> 0.50f
        }

        val estBytes = if (modeRatio < 0f) {
            (_state.value.targetSizeMb * 1024f * 1024f).toLong()
        } else {
            (origBytes * modeRatio * resRatio * durRatio).toLong()
        }

        val clampedEstBytes = estBytes.coerceAtLeast(500000L)
        val reductionFrac = 1.0f - (clampedEstBytes.toFloat() / origBytes.toFloat())
        val reductionPct = (reductionFrac * 100).toInt()

        _state.update {
            it.copy(
                estimatedOutputSizeBytes = clampedEstBytes,
                estimatedReductionPercent = reductionPct
            )
        }
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

            val params = ConverterCompressorParams(
                inputUri = uri,
                inputFileName = info.fileName,
                mediaType = info.mediaType,
                sourceDurationMs = info.durationMs,
                originalBitrateBps = info.totalBitrateBps,
                originalFileSizeBytes = info.fileSizeBytes,
                compressionMode = s.compressionMode,
                targetSizeMb = s.targetSizeMb,
                outputFormat = s.outputFormat,
                targetResolution = s.targetResolution,
                targetFps = s.targetFps,
                audioBitrate = s.audioBitrate,
                sampleRate = s.sampleRate,
                channels = s.channels,
                trimStartMs = s.trimStartMs,
                trimEndMs = if (s.trimEndMs == info.durationMs) -1L else s.trimEndMs,
                volumeDb = s.volumeDb,
                speedMultiplier = s.speedMultiplier,
                pitchSemitones = s.pitchSemitones,
                isPitchLocked = s.isPitchLocked,
                reverse = s.reverse,
                customFileName = s.customFileName,
                eqPreset = s.eqPreset
            )

            val result = ConverterCompressorProcessor.process(context, params) { progress ->
                _state.update { it.copy(processingProgress = progress) }
            }

            when (result) {
                is ConverterCompressorResult.Success -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            processingProgress = 1f,
                            exportResult = ConvertedMediaResult(result.outputFile, result.outputUri)
                        )
                    }
                }
                is ConverterCompressorResult.Failure -> {
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

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        player?.release()
        player = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConverterCompressorViewModel(context.applicationContext) as T
        }
    }
}
