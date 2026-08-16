package com.voconexus.app.core.tools.trimmermerger

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TrimMode {
    KEEP_SELECTED_RANGE,
    REMOVE_SELECTED_RANGE
}

enum class AspectRatioCrop {
    ORIGINAL,
    RATIO_16_9,
    RATIO_9_16,
    RATIO_1_1,
    RATIO_4_5
}

enum class ResolutionPreset {
    NATIVE,
    FHD_1080P,
    HD_720P,
    SD_480P
}

enum class MergeTransition {
    NONE,
    CROSSFADE_1S,
    CROSSFADE_2S,
    DISSOLVE
}

enum class SplitType {
    BY_DURATION_SECONDS,
    BY_PARTS_COUNT
}

data class TrimmerOptions(
    val inputFile: File,
    val isVideo: Boolean,
    val startMs: Long,
    val endMs: Long,
    val mode: TrimMode = TrimMode.KEEP_SELECTED_RANGE,
    val useFastStreamCopy: Boolean = true,
    val speedMultiplier: Float = 1.0f,
    val fadeInSeconds: Int = 0,
    val fadeOutSeconds: Int = 0,
    val extractAudioOnly: Boolean = false,
    val muteVideoAudio: Boolean = false,
    val volumeBoost: Float = 1.0f,
    val cropRatio: AspectRatioCrop = AspectRatioCrop.ORIGINAL,
    val targetResolution: ResolutionPreset = ResolutionPreset.NATIVE,
    val outputFormat: String = "mp4",
    val customFileName: String? = null
)

data class MergerOptions(
    val inputFiles: List<File>,
    val isVideo: Boolean,
    val transition: MergeTransition = MergeTransition.NONE,
    val useFastStreamCopy: Boolean = true,
    val normalizeVolume: Boolean = false,
    val targetResolution: ResolutionPreset = ResolutionPreset.NATIVE,
    val outputFormat: String = "mp4",
    val customFileName: String? = null
)

data class SplitterOptions(
    val inputFile: File,
    val isVideo: Boolean,
    val splitType: SplitType = SplitType.BY_DURATION_SECONDS,
    val segmentLengthSeconds: Int = 30,
    val totalPartsCount: Int = 3,
    val customFileName: String? = null
)

data class ProcessorProgress(
    val percent: Int,
    val currentStepText: String
)

class TrimmerMergerProcessor(private val context: Context) {

    fun cancelProcessing() {
        try {
            FFmpegKit.cancel()
        } catch (e: Exception) {
            Log.e("TrimmerProcessor", "Error cancelling FFmpeg session: ${e.message}")
        }
    }

    // --- Action 1: Precision Advanced Media Trimmer ---
    suspend fun trimMedia(
        options: TrimmerOptions,
        onProgress: (ProcessorProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        onProgress(ProcessorProgress(5, "Initializing advanced trimmer..."))

        val outputDir = getOutputDir(if (options.extractAudioOnly) false else options.isVideo)
        val fileExt = if (options.extractAudioOnly) "mp3" else options.outputFormat.lowercase().replace(".", "")
        val fileName = options.customFileName?.takeIf { it.isNotBlank() }
            ?: "Trimmed_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val outputFile = File(outputDir, "$fileName.$fileExt")

        val startSec = options.startMs / 1000.0
        val endSec = options.endMs / 1000.0
        val durationSec = (options.endMs - options.startMs) / 1000.0
        val totalDurationMs = (options.endMs - options.startMs).coerceAtLeast(1000L)

        val hasAdvancedEffects = options.speedMultiplier != 1.0f ||
                options.fadeInSeconds > 0 ||
                options.fadeOutSeconds > 0 ||
                options.volumeBoost != 1.0f ||
                options.cropRatio != AspectRatioCrop.ORIGINAL ||
                options.targetResolution != ResolutionPreset.NATIVE ||
                options.extractAudioOnly ||
                options.muteVideoAudio

        val command = if (!hasAdvancedEffects && options.useFastStreamCopy && options.mode == TrimMode.KEEP_SELECTED_RANGE) {
            "-ss %.3f -to %.3f -i \"%s\" -c copy -avoid_negative_ts make_zero \"%s\" -y"
                .format(Locale.US, startSec, endSec, options.inputFile.absolutePath, outputFile.absolutePath)
        } else {
            buildAdvancedTrimCommand(options, startSec, endSec, durationSec, outputFile)
        }

        onProgress(ProcessorProgress(15, "Exporting 15%"))
        FFmpegKitConfig.enableStatisticsCallback { stats ->
            if (stats != null && totalDurationMs > 0) {
                val pct = ((stats.time / totalDurationMs.toFloat()) * 100).toInt().coerceIn(5, 95)
                onProgress(ProcessorProgress(pct, "Exporting $pct%"))
            }
        }

        val session = FFmpegKit.execute(command)
        FFmpegKitConfig.enableStatisticsCallback(null)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            val fallbackCmd = "-ss %.3f -to %.3f -i \"%s\" -c:v mpeg4 -b:v 3M -c:a aac \"%s\" -y"
                .format(Locale.US, startSec, endSec, options.inputFile.absolutePath, outputFile.absolutePath)
            val fallbackSession = FFmpegKit.execute(fallbackCmd)
            if (!ReturnCode.isSuccess(fallbackSession.returnCode)) {
                val err = fallbackSession.failStackTrace ?: fallbackSession.output ?: "Unknown FFmpeg Error"
                Log.e("TrimmerProcessor", "Trim Failed: $err")
                throw IllegalStateException("Trim Failed: ${fallbackSession.returnCode}")
            }
        }

        onProgress(ProcessorProgress(98, "Scanning output media file..."))
        scanMediaFile(outputFile)
        onProgress(ProcessorProgress(100, "Exporting 100% - Trimming Complete!"))
        outputFile
    }

    private fun buildAdvancedTrimCommand(
        options: TrimmerOptions,
        startSec: Double,
        endSec: Double,
        durationSec: Double,
        outputFile: File
    ): String {
        val inputPath = options.inputFile.absolutePath
        val outputPath = outputFile.absolutePath

        val aFilters = mutableListOf<String>()
        if (options.volumeBoost != 1.0f) {
            aFilters.add("volume=%.2f".format(Locale.US, options.volumeBoost))
        }
        if (options.speedMultiplier != 1.0f) {
            aFilters.add("atempo=%.2f".format(Locale.US, options.speedMultiplier.coerceIn(0.5f, 2.0f)))
        }
        if (options.fadeInSeconds > 0) {
            aFilters.add("afade=t=in:ss=0:d=%d".format(options.fadeInSeconds))
        }
        if (options.fadeOutSeconds > 0) {
            val fadeOutStartSec = (durationSec / options.speedMultiplier) - options.fadeOutSeconds
            if (fadeOutStartSec > 0) {
                aFilters.add("afade=t=out:st=%.3f:d=%d".format(Locale.US, fadeOutStartSec, options.fadeOutSeconds))
            }
        }

        val vFilters = mutableListOf<String>()
        if (options.cropRatio != AspectRatioCrop.ORIGINAL) {
            when (options.cropRatio) {
                AspectRatioCrop.RATIO_16_9 -> vFilters.add("crop=w='min(iw,ih*16/9)':h='min(ih,iw*9/16)'")
                AspectRatioCrop.RATIO_9_16 -> vFilters.add("crop=w='min(iw,ih*9/16)':h='min(ih,iw*16/9)'")
                AspectRatioCrop.RATIO_1_1 -> vFilters.add("crop=w='min(iw,ih)':h='min(iw,ih)'")
                AspectRatioCrop.RATIO_4_5 -> vFilters.add("crop=w='min(iw,ih*4/5)':h='min(ih,iw*5/4)'")
                else -> {}
            }
        }
        if (options.targetResolution != ResolutionPreset.NATIVE) {
            when (options.targetResolution) {
                ResolutionPreset.FHD_1080P -> vFilters.add("scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2")
                ResolutionPreset.HD_720P -> vFilters.add("scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2")
                ResolutionPreset.SD_480P -> vFilters.add("scale=854:480:force_original_aspect_ratio=decrease,pad=854:480:(ow-iw)/2:(oh-ih)/2")
                else -> {}
            }
        }
        if (options.speedMultiplier != 1.0f) {
            vFilters.add("setpts=%.4f*PTS".format(Locale.US, 1.0f / options.speedMultiplier))
        }

        return if (options.extractAudioOnly) {
            val filterStr = if (aFilters.isNotEmpty()) "-af \"${aFilters.joinToString(",")}\"" else ""
            "-ss %.3f -to %.3f -i \"%s\" %s -vn -c:a mp3 -b:a 320k \"%s\" -y"
                .format(Locale.US, startSec, endSec, inputPath, filterStr, outputPath)
        } else if (!options.isVideo) {
            val filterStr = if (aFilters.isNotEmpty()) "-af \"${aFilters.joinToString(",")}\"" else ""
            "-ss %.3f -to %.3f -i \"%s\" %s -c:a mp3 -b:a 320k \"%s\" -y"
                .format(Locale.US, startSec, endSec, inputPath, filterStr, outputPath)
        } else {
            val vFilterStr = if (vFilters.isNotEmpty()) "-vf \"${vFilters.joinToString(",")}\"" else ""
            val aFilterStr = if (options.muteVideoAudio) "-an" else if (aFilters.isNotEmpty()) "-af \"${aFilters.joinToString(",")}\"" else ""
            "-ss %.3f -to %.3f -i \"%s\" %s %s -c:v mpeg4 -b:v 4M -c:a aac -b:a 192k \"%s\" -y"
                .format(Locale.US, startSec, endSec, inputPath, vFilterStr, aFilterStr, outputPath)
        }
    }

    // --- Action 2: Multi-File Media Merger ---
    suspend fun mergeMediaFiles(
        options: MergerOptions,
        onProgress: (ProcessorProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (options.inputFiles.isEmpty()) throw IllegalArgumentException("No input files provided for merger")

        onProgress(ProcessorProgress(5, "Preparing multi-file merger..."))

        val outputDir = getOutputDir(options.isVideo)
        val fileExt = options.outputFormat.lowercase().replace(".", "")
        val fileName = options.customFileName?.takeIf { it.isNotBlank() }
            ?: "Merged_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val outputFile = File(outputDir, "$fileName.$fileExt")

        return@withContext mergeMediaWithReencode(options, outputFile, onProgress)
    }

    private suspend fun mergeMediaWithReencode(
        options: MergerOptions,
        outputFile: File,
        onProgress: (ProcessorProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val inputsCmd = options.inputFiles.joinToString(" ") { "-i \"${it.absolutePath}\"" }
        val filterComplex = StringBuilder()
        val n = options.inputFiles.size

        if (options.isVideo) {
            val res = when (options.targetResolution) {
                ResolutionPreset.FHD_1080P -> "1920:1080"
                ResolutionPreset.SD_480P -> "854:480"
                else -> "1280:720"
            }
            val normFilter = if (options.normalizeVolume) ",loudnorm" else ""

            for (i in 0 until n) {
                filterComplex.append("[$i:v]scale=$res:force_original_aspect_ratio=decrease,pad=$res:(ow-iw)/2:(oh-ih)/2,fps=30,setsar=1[v$i];")
                filterComplex.append("[$i:a]aformat=sample_rates=44100:channel_layouts=stereo$normFilter[a$i];")
            }
            for (i in 0 until n) {
                filterComplex.append("[v$i][a$i]")
            }
            filterComplex.append("concat=n=$n:v=1:a=1[outv][outa]")

            val command = "%s -filter_complex \"%s\" -map \"[outv]\" -map \"[outa]\" -c:v mpeg4 -b:v 4M -c:a aac -b:a 192k \"%s\" -y"
                .format(Locale.US, inputsCmd, filterComplex.toString(), outputFile.absolutePath)

            onProgress(ProcessorProgress(25, "Exporting 25%"))
            FFmpegKitConfig.enableStatisticsCallback { stats ->
                if (stats != null) {
                    val pct = (stats.time / 1000.0).toInt().coerceIn(10, 95)
                    onProgress(ProcessorProgress(pct, "Exporting $pct%"))
                }
            }
            val session = FFmpegKit.execute(command)
            FFmpegKitConfig.enableStatisticsCallback(null)

            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e("TrimmerProcessor", "Video Concat Failed: ${session.output}")
                throw IllegalStateException("Video Concat Failed: ${session.returnCode}")
            }
        } else {
            val normFilter = if (options.normalizeVolume) ",loudnorm" else ""
            for (i in 0 until n) {
                filterComplex.append("[$i:a]aformat=sample_rates=44100:channel_layouts=stereo$normFilter[a$i];")
            }
            for (i in 0 until n) {
                filterComplex.append("[a$i]")
            }
            filterComplex.append("concat=n=$n:v=0:a=1[outa]")

            val command = "%s -filter_complex \"%s\" -map \"[outa]\" -c:a mp3 -b:a 320k \"%s\" -y"
                .format(Locale.US, inputsCmd, filterComplex.toString(), outputFile.absolutePath)

            onProgress(ProcessorProgress(30, "Exporting 30%"))
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e("TrimmerProcessor", "Audio Concat Failed: ${session.output}")
                throw IllegalStateException("Audio Concat Failed: ${session.returnCode}")
            }
        }

        onProgress(ProcessorProgress(98, "Scanning output file..."))
        scanMediaFile(outputFile)
        onProgress(ProcessorProgress(100, "Exporting 100% - Merging Complete!"))
        outputFile
    }

    // --- Action 3: Auto Splitter into Equal Clips ---
    suspend fun splitMediaIntoEqualParts(
        options: SplitterOptions,
        onProgress: (ProcessorProgress) -> Unit = {}
    ): List<File> = withContext(Dispatchers.IO) {
        onProgress(ProcessorProgress(10, "Preparing splitter..."))

        val outputDir = getOutputDir(options.isVideo)
        val baseFileName = options.customFileName?.takeIf { it.isNotBlank() }
            ?: "Split_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val fileExt = if (options.isVideo) "mp4" else "mp3"

        val segmentDuration = options.segmentLengthSeconds
        val outputPattern = File(outputDir, "${baseFileName}_part%%03d.$fileExt").absolutePath

        val command = if (options.isVideo) {
            "-i \"%s\" -c copy -map 0 -segment_time %d -f segment -reset_timestamps 1 \"%s\" -y"
                .format(Locale.US, options.inputFile.absolutePath, segmentDuration, outputPattern)
        } else {
            "-i \"%s\" -c copy -segment_time %d -f segment -reset_timestamps 1 \"%s\" -y"
                .format(Locale.US, options.inputFile.absolutePath, segmentDuration, outputPattern)
        }

        onProgress(ProcessorProgress(40, "Exporting split clips..."))
        val session = FFmpegKit.execute(command)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            Log.e("TrimmerProcessor", "Split Failed: ${session.output}")
            throw IllegalStateException("Split Failed: ${session.returnCode}")
        }

        val generatedFiles = outputDir.listFiles { _, name ->
            name.startsWith(baseFileName) && name.endsWith(".$fileExt")
        }?.toList() ?: emptyList()

        generatedFiles.forEach { scanMediaFile(it) }

        onProgress(ProcessorProgress(100, "Exporting 100% - Split Complete! (${generatedFiles.size} clips)"))
        generatedFiles
    }

    // --- Action 4: Auto Silence Cut & Gap Remover ---
    suspend fun removeSilence(
        inputFile: File,
        isVideo: Boolean,
        customFileName: String? = null,
        onProgress: (ProcessorProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        onProgress(ProcessorProgress(10, "Detecting silence gaps..."))

        val outputDir = getOutputDir(isVideo)
        val fileExt = if (isVideo) "mp4" else "mp3"
        val fileName = customFileName?.takeIf { it.isNotBlank() }
            ?: "SilenceCut_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val outputFile = File(outputDir, "$fileName.$fileExt")

        val command = if (isVideo) {
            "-i \"%s\" -af \"silenceremove=stop_periods=-1:stop_duration=0.5:stop_threshold=-35dB\" -c:v copy -c:a aac \"%s\" -y"
                .format(Locale.US, inputFile.absolutePath, outputFile.absolutePath)
        } else {
            "-i \"%s\" -af \"silenceremove=stop_periods=-1:stop_duration=0.5:stop_threshold=-35dB\" -c:a mp3 -b:a 320k \"%s\" -y"
                .format(Locale.US, inputFile.absolutePath, outputFile.absolutePath)
        }

        onProgress(ProcessorProgress(50, "Exporting silence cut media..."))
        val session = FFmpegKit.execute(command)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            Log.e("TrimmerProcessor", "Silence Cut Failed: ${session.output}")
            throw IllegalStateException("Silence Cut Failed: ${session.returnCode}")
        }

        scanMediaFile(outputFile)
        onProgress(ProcessorProgress(100, "Exporting 100% - Silence Cut Complete!"))
        outputFile
    }

    private fun getOutputDir(isVideo: Boolean): File {
        val subFolder = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val publicDir = File(Environment.getExternalStoragePublicDirectory(subFolder), "VocoNexus/TrimmerMerger")
        try {
            if (!publicDir.exists()) publicDir.mkdirs()
            if (publicDir.exists() && publicDir.canWrite()) {
                return publicDir
            }
        } catch (_: Exception) {}

        val appDir = File(context.getExternalFilesDir(subFolder), "VocoNexus/TrimmerMerger")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    private fun scanMediaFile(file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (_: Exception) {}
    }
}
