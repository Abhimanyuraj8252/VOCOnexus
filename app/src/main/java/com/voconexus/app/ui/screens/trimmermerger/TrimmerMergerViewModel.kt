package com.voconexus.app.ui.screens.trimmermerger

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.voconexus.app.core.tools.trimmermerger.AspectRatioCrop
import com.voconexus.app.core.tools.trimmermerger.MediaItemProbeResult
import com.voconexus.app.core.tools.trimmermerger.MergeTransition
import com.voconexus.app.core.tools.trimmermerger.MergerOptions
import com.voconexus.app.core.tools.trimmermerger.ProcessorProgress
import com.voconexus.app.core.tools.trimmermerger.ResolutionPreset
import com.voconexus.app.core.tools.trimmermerger.SplitType
import com.voconexus.app.core.tools.trimmermerger.SplitterOptions
import com.voconexus.app.core.tools.trimmermerger.TrimMode
import com.voconexus.app.core.tools.trimmermerger.TrimmerMergerProbe
import com.voconexus.app.core.tools.trimmermerger.TrimmerMergerProcessor
import com.voconexus.app.core.tools.trimmermerger.TrimmerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ToolOperationMode {
    TRIM,
    MERGE,
    SPLIT,
    SILENCE_CUT
}

class TrimmerMergerViewModel(
    private val context: Context,
    private val probe: TrimmerMergerProbe,
    private val processor: TrimmerMergerProcessor
) : ViewModel() {

    private val _activeMode = MutableStateFlow(ToolOperationMode.TRIM)
    val activeMode: StateFlow<ToolOperationMode> = _activeMode.asStateFlow()

    private val _mediaList = MutableStateFlow<List<MediaItemProbeResult>>(emptyList())
    val mediaList: StateFlow<List<MediaItemProbeResult>> = _mediaList.asStateFlow()

    private val _selectedMediaIndex = MutableStateFlow(0)
    val selectedMediaIndex: StateFlow<Int> = _selectedMediaIndex.asStateFlow()

    // Trimmer Handles (A to B)
    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(0L)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    private val _trimMode = MutableStateFlow(TrimMode.KEEP_SELECTED_RANGE)
    val trimMode: StateFlow<TrimMode> = _trimMode.asStateFlow()

    private val _useFastStreamCopy = MutableStateFlow(true)
    val useFastStreamCopy: StateFlow<Boolean> = _useFastStreamCopy.asStateFlow()

    // Advanced Trimmer Controls
    private val _speedMultiplier = MutableStateFlow(1.0f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    private val _fadeInSeconds = MutableStateFlow(0)
    val fadeInSeconds: StateFlow<Int> = _fadeInSeconds.asStateFlow()

    private val _fadeOutSeconds = MutableStateFlow(0)
    val fadeOutSeconds: StateFlow<Int> = _fadeOutSeconds.asStateFlow()

    private val _extractAudioOnly = MutableStateFlow(false)
    val extractAudioOnly: StateFlow<Boolean> = _extractAudioOnly.asStateFlow()

    private val _muteVideoAudio = MutableStateFlow(false)
    val muteVideoAudio: StateFlow<Boolean> = _muteVideoAudio.asStateFlow()

    private val _volumeBoost = MutableStateFlow(1.0f)
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _cropRatio = MutableStateFlow(AspectRatioCrop.ORIGINAL)
    val cropRatio: StateFlow<AspectRatioCrop> = _cropRatio.asStateFlow()

    private val _targetResolution = MutableStateFlow(ResolutionPreset.NATIVE)
    val targetResolution: StateFlow<ResolutionPreset> = _targetResolution.asStateFlow()

    private val _audioBitrateKbps = MutableStateFlow(320)
    val audioBitrateKbps: StateFlow<Int> = _audioBitrateKbps.asStateFlow()

    private val _audioChannels = MutableStateFlow(2)
    val audioChannels: StateFlow<Int> = _audioChannels.asStateFlow()

    private val _videoRotation = MutableStateFlow(0)
    val videoRotation: StateFlow<Int> = _videoRotation.asStateFlow()

    // Player Live State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    // Splitter Controls
    private val _splitType = MutableStateFlow(SplitType.BY_DURATION_SECONDS)
    val splitType: StateFlow<SplitType> = _splitType.asStateFlow()

    private val _splitSegmentSeconds = MutableStateFlow(30)
    val splitSegmentSeconds: StateFlow<Int> = _splitSegmentSeconds.asStateFlow()

    private val _splitPartsCount = MutableStateFlow(4)
    val splitPartsCount: StateFlow<Int> = _splitPartsCount.asStateFlow()

    // Silence Cut Controls
    private val _silenceThresholdDb = MutableStateFlow(-35)
    val silenceThresholdDb: StateFlow<Int> = _silenceThresholdDb.asStateFlow()

    private val _silenceMinDurationSec = MutableStateFlow(0.5f)
    val silenceMinDurationSec: StateFlow<Float> = _silenceMinDurationSec.asStateFlow()

    // Merger Controls
    private val _mergeTransition = MutableStateFlow(MergeTransition.NONE)
    val mergeTransition: StateFlow<MergeTransition> = _mergeTransition.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(false)
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    // Export State
    private val _customOutputName = MutableStateFlow("")
    val customOutputName: StateFlow<String> = _customOutputName.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow<ProcessorProgress?>(null)
    val exportProgress: StateFlow<ProcessorProgress?> = _exportProgress.asStateFlow()

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // ExoPlayer for Live Media Preview
    private var player: ExoPlayer? = null

    init {
        initializePlayer()
        startPositionTracker()
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                        _isPlaying.value = isPlayingNow
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val currentIdx = player?.currentMediaItemIndex ?: 0
                        if (currentIdx in _mediaList.value.indices) {
                            _selectedMediaIndex.value = currentIdx
                        }
                    }
                })
            }
        }
    }

    private fun startPositionTracker() {
        viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    val items = _mediaList.value
                    if (items.isNotEmpty()) {
                        val currentIdx = p.currentMediaItemIndex
                        if (_activeMode.value == ToolOperationMode.MERGE) {
                            var accumulatedMs = 0L
                            for (i in 0 until currentIdx.coerceAtMost(items.size)) {
                                accumulatedMs += items[i].durationMs
                            }
                            _currentPositionMs.value = accumulatedMs + p.currentPosition.coerceAtLeast(0L)
                            if (currentIdx in items.indices && currentIdx != _selectedMediaIndex.value) {
                                _selectedMediaIndex.value = currentIdx
                            }
                        } else {
                            if (p.isPlaying) {
                                _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    fun getPlayer(): ExoPlayer? = player

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun seekRelative(offsetMs: Long) {
        val newPos = (_currentPositionMs.value + offsetMs).coerceAtLeast(0L)
        seekToMs(newPos)
    }

    fun seekToMs(positionMs: Long) {
        player?.let { p ->
            val items = _mediaList.value
            if (_activeMode.value == ToolOperationMode.MERGE && items.isNotEmpty()) {
                val totalMergedMs = items.sumOf { it.durationMs }
                var targetMs = positionMs.coerceIn(0L, totalMergedMs)
                var itemIdx = 0
                while (itemIdx < items.size - 1 && targetMs > items[itemIdx].durationMs) {
                    targetMs -= items[itemIdx].durationMs
                    itemIdx++
                }
                p.seekTo(itemIdx, targetMs)
                _selectedMediaIndex.value = itemIdx
                _currentPositionMs.value = positionMs
            } else {
                val target = positionMs.coerceIn(0L, p.duration.coerceAtLeast(0L))
                p.seekTo(target)
                _currentPositionMs.value = target
            }
        }
    }

    fun selectMode(mode: ToolOperationMode) {
        _activeMode.value = mode
        updatePlayerMedia()
    }

    fun addMediaUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val probed = probe.probeMediaUri(uri)
                val current = _mediaList.value.toMutableList()
                current.add(probed)
                _mediaList.value = current
                _userMessage.value = "Loaded ${probed.file.name}"
                updatePlayerMedia()
            } catch (e: Exception) {
                _userMessage.value = "Failed to load media: ${e.message}"
            }
        }
    }

    fun removeMediaIndex(index: Int) {
        val current = _mediaList.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _mediaList.value = current
            if (current.isNotEmpty()) {
                _selectedMediaIndex.value = _selectedMediaIndex.value.coerceIn(0, current.size - 1)
                updatePlayerMedia()
            } else {
                player?.stop()
                player?.clearMediaItems()
                _trimStartMs.value = 0L
                _trimEndMs.value = 0L
                _currentPositionMs.value = 0L
            }
        }
    }

    fun moveMediaIndex(fromIndex: Int, toIndex: Int) {
        val current = _mediaList.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _mediaList.value = current
            updatePlayerMedia()
        }
    }

    fun selectMediaIndex(index: Int) {
        if (index in _mediaList.value.indices) {
            _selectedMediaIndex.value = index
            if (_activeMode.value == ToolOperationMode.MERGE) {
                var accumulatedMs = 0L
                for (i in 0 until index) {
                    accumulatedMs += _mediaList.value[i].durationMs
                }
                player?.seekTo(index, 0L)
                _currentPositionMs.value = accumulatedMs
            } else {
                updatePlayerMedia()
            }
        }
    }

    private fun updatePlayerMedia() {
        val items = _mediaList.value
        if (items.isEmpty()) return

        player?.let { p ->
            p.stop()
            p.clearMediaItems()

            if (_activeMode.value == ToolOperationMode.MERGE) {
                val mediaItems = items.map { MediaItem.fromUri(Uri.fromFile(it.file)) }
                p.setMediaItems(mediaItems)
                p.prepare()

                val totalMergedDuration = items.sumOf { it.durationMs }
                _trimStartMs.value = 0L
                _trimEndMs.value = totalMergedDuration
                _currentPositionMs.value = 0L
            } else {
                val selectedItem = items.getOrNull(_selectedMediaIndex.value) ?: items.first()
                p.setMediaItem(MediaItem.fromUri(Uri.fromFile(selectedItem.file)))
                p.prepare()

                _trimStartMs.value = 0L
                _trimEndMs.value = selectedItem.durationMs
                _currentPositionMs.value = 0L
            }
        }
    }

    fun setTrimStartMs(startMs: Long) {
        val endMs = _trimEndMs.value
        if (startMs in 0L..endMs) {
            _trimStartMs.value = startMs
            seekToMs(startMs)
        }
    }

    fun setTrimEndMs(endMs: Long) {
        val totalMs = if (_activeMode.value == ToolOperationMode.MERGE) _mediaList.value.sumOf { it.durationMs } else (_mediaList.value.getOrNull(_selectedMediaIndex.value)?.durationMs ?: Long.MAX_VALUE)
        val startMs = _trimStartMs.value
        if (endMs in startMs..totalMs) {
            _trimEndMs.value = endMs
            seekToMs(endMs)
        }
    }

    fun setTrimMode(mode: TrimMode) {
        _trimMode.value = mode
    }

    fun setUseFastStreamCopy(copy: Boolean) {
        _useFastStreamCopy.value = copy
    }

    fun setSpeedMultiplier(speed: Float) {
        _speedMultiplier.value = speed
    }

    fun setFadeInSeconds(sec: Int) {
        _fadeInSeconds.value = sec
    }

    fun setFadeOutSeconds(sec: Int) {
        _fadeOutSeconds.value = sec
    }

    fun setExtractAudioOnly(extract: Boolean) {
        _extractAudioOnly.value = extract
    }

    fun setMuteVideoAudio(mute: Boolean) {
        _muteVideoAudio.value = mute
    }

    fun setVolumeBoost(volume: Float) {
        _volumeBoost.value = volume
    }

    fun setCropRatio(ratio: AspectRatioCrop) {
        _cropRatio.value = ratio
    }

    fun setTargetResolution(res: ResolutionPreset) {
        _targetResolution.value = res
    }

    fun setAudioBitrateKbps(bitrate: Int) {
        _audioBitrateKbps.value = bitrate
    }

    fun setAudioChannels(channels: Int) {
        _audioChannels.value = channels
    }

    fun setVideoRotation(angle: Int) {
        _videoRotation.value = angle
    }

    fun setSplitType(type: SplitType) {
        _splitType.value = type
    }

    fun setSplitSegmentSeconds(seconds: Int) {
        if (seconds > 0) _splitSegmentSeconds.value = seconds
    }

    fun setSplitPartsCount(count: Int) {
        if (count >= 2) _splitPartsCount.value = count
    }

    fun setSilenceThresholdDb(db: Int) {
        _silenceThresholdDb.value = db
    }

    fun setSilenceMinDurationSec(sec: Float) {
        _silenceMinDurationSec.value = sec
    }

    fun setMergeTransition(transition: MergeTransition) {
        _mergeTransition.value = transition
    }

    fun setNormalizeVolume(normalize: Boolean) {
        _normalizeVolume.value = normalize
    }

    fun setCustomOutputName(name: String) {
        _customOutputName.value = name
    }

    fun cancelExport() {
        processor.cancelProcessing()
        _isExporting.value = false
        _exportProgress.value = null
        _userMessage.value = "Export Cancelled"
    }

    fun processOperation() {
        viewModelScope.launch {
            val items = _mediaList.value
            if (items.isEmpty()) {
                _userMessage.value = "Please import at least one media file first"
                return@launch
            }

            _isExporting.value = true
            _exportProgress.value = ProcessorProgress(5, "Initializing processing engine...")

            try {
                when (_activeMode.value) {
                    ToolOperationMode.TRIM -> {
                        val currentItem = items.getOrNull(_selectedMediaIndex.value) ?: return@launch
                        val resultFile = processor.trimMedia(
                            options = TrimmerOptions(
                                inputFile = currentItem.file,
                                isVideo = currentItem.isVideo,
                                startMs = _trimStartMs.value,
                                endMs = _trimEndMs.value,
                                mode = _trimMode.value,
                                useFastStreamCopy = _useFastStreamCopy.value,
                                speedMultiplier = _speedMultiplier.value,
                                fadeInSeconds = _fadeInSeconds.value,
                                fadeOutSeconds = _fadeOutSeconds.value,
                                extractAudioOnly = _extractAudioOnly.value,
                                muteVideoAudio = _muteVideoAudio.value,
                                volumeBoost = _volumeBoost.value,
                                cropRatio = _cropRatio.value,
                                targetResolution = _targetResolution.value,
                                customFileName = _customOutputName.value.takeIf { it.isNotBlank() }
                            ),
                            onProgress = { p -> _exportProgress.value = p }
                        )
                        _exportedFile.value = resultFile
                        _userMessage.value = "Trimmed file saved to ${resultFile.parentFile?.name}/${resultFile.name}"
                    }
                    ToolOperationMode.MERGE -> {
                        val isAnyVideo = items.any { it.isVideo }
                        val resultFile = processor.mergeMediaFiles(
                            options = MergerOptions(
                                inputFiles = items.map { it.file },
                                isVideo = isAnyVideo,
                                transition = _mergeTransition.value,
                                useFastStreamCopy = _useFastStreamCopy.value,
                                normalizeVolume = _normalizeVolume.value,
                                targetResolution = _targetResolution.value,
                                customFileName = _customOutputName.value.takeIf { it.isNotBlank() }
                            ),
                            onProgress = { p -> _exportProgress.value = p }
                        )
                        _exportedFile.value = resultFile
                        _userMessage.value = "Merged ${items.size} files into ${resultFile.name}"
                    }
                    ToolOperationMode.SPLIT -> {
                        val currentItem = items.getOrNull(_selectedMediaIndex.value) ?: return@launch
                        val segSeconds = if (_splitType.value == SplitType.BY_PARTS_COUNT) {
                            ((currentItem.durationMs / 1000) / _splitPartsCount.value).toInt().coerceAtLeast(5)
                        } else {
                            _splitSegmentSeconds.value
                        }

                        val resultFiles = processor.splitMediaIntoEqualParts(
                            options = SplitterOptions(
                                inputFile = currentItem.file,
                                isVideo = currentItem.isVideo,
                                splitType = _splitType.value,
                                segmentLengthSeconds = segSeconds,
                                totalPartsCount = _splitPartsCount.value,
                                customFileName = _customOutputName.value.takeIf { it.isNotBlank() }
                            ),
                            onProgress = { p -> _exportProgress.value = p }
                        )
                        _exportedFile.value = resultFiles.firstOrNull()
                        _userMessage.value = "Split into ${resultFiles.size} clips successfully!"
                    }
                    ToolOperationMode.SILENCE_CUT -> {
                        val currentItem = items.getOrNull(_selectedMediaIndex.value) ?: return@launch
                        val resultFile = processor.removeSilence(
                            inputFile = currentItem.file,
                            isVideo = currentItem.isVideo,
                            customFileName = _customOutputName.value.takeIf { it.isNotBlank() },
                            onProgress = { p -> _exportProgress.value = p }
                        )
                        _exportedFile.value = resultFile
                        _userMessage.value = "Trimmed silent gaps: ${resultFile.name}"
                    }
                }
            } catch (e: Exception) {
                if (_isExporting.value) {
                    _userMessage.value = "Operation failed: ${e.message}"
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }

    class Factory(
        private val context: Context,
        private val probe: TrimmerMergerProbe,
        private val processor: TrimmerMergerProcessor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TrimmerMergerViewModel(context, probe, processor) as T
        }
    }
}
