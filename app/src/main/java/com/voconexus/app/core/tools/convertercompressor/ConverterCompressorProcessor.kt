package com.voconexus.app.core.tools.convertercompressor

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.voconexus.app.core.tools.speedpitch.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "ConverterCompressorProcessor"

data class ConverterCompressorParams(
    val inputUri: Uri,
    val inputFileName: String,
    val mediaType: MediaType,
    val sourceDurationMs: Long = 0L,
    val originalBitrateBps: Long = 0L,
    val originalFileSizeBytes: Long = 0L,

    // Compression Mode
    val compressionMode: String = "balanced",   // "original", "balanced", "high", "custom_mb"
    val targetSizeMb: Float = 0f,               // Target size in MB if mode is custom_mb

    // Format & Resolution
    val outputFormat: String = "original",     // "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "m4v", "wmv", "mpg", "mp3", "aac", "wav", "flac", "ogg", "opus", "ac3", "aiff", "amr", "wma"
    val targetResolution: String = "original",  // "original", "4k", "1080p", "720p", "480p", "360p"
    val targetFps: Int = -1,                    // -1 = original, 60, 30, 24

    // Audio Settings
    val audioBitrate: String = "original",      // "original", "320k", "256k", "192k", "128k", "96k"
    val sampleRate: Int = -1,                   // -1 = original, 48000, 44100, 32000, 16000
    val channels: Int = -1,                     // -1 = original, 1 = mono, 2 = stereo

    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,                  // -1 = full duration

    // Volume & Speed
    val volumeDb: Float = 0f,
    val speedMultiplier: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val isPitchLocked: Boolean = true,
    val reverse: Boolean = false,

    // Custom Filename & EQ
    val customFileName: String = "",
    val eqPreset: String = "flat"
)

sealed class ConverterCompressorResult {
    data class Success(val outputFile: File, val outputUri: Uri?) : ConverterCompressorResult()
    data class Failure(val error: String) : ConverterCompressorResult()
}

object ConverterCompressorProcessor {

    suspend fun process(
        context: Context,
        params: ConverterCompressorParams,
        onProgress: suspend (Float) -> Unit
    ): ConverterCompressorResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Format Conversion & Compression for file: ${params.inputFileName}")

            // 1. Cache input file
            val inputFile = copyUriToCache(context, params.inputUri, params.inputFileName)
                ?: return@withContext ConverterCompressorResult.Failure("Could not read input file.")

            // 2. Output extension
            val isAudioOutput = isAudioFormat(params.outputFormat, params.mediaType)
            val ext = getFinalOutputExtension(params, isAudioOutput)
            val tempOutputFile = File(context.cacheDir, "conv_output_${System.currentTimeMillis()}.$ext")

            // 3. Build FFmpeg command
            val cmd = buildCommand(inputFile.absolutePath, tempOutputFile.absolutePath, params, isAudioOutput)
            Log.d(TAG, "FFmpeg command: $cmd")

            // 4. Progress setup
            val rawTrimDur = if (params.trimEndMs > 0 && params.trimEndMs > params.trimStartMs) {
                (params.trimEndMs - params.trimStartMs)
            } else {
                params.sourceDurationMs.coerceAtLeast(1000L)
            }
            val totalTargetMs = (rawTrimDur.toFloat() / params.speedMultiplier).toLong().coerceAtLeast(1000L)

            var maxProgressSoFar = 0.05f

            FFmpegKitConfig.enableStatisticsCallback { stats ->
                val timeMs = stats.time
                if (timeMs > 0 && totalTargetMs > 0) {
                    val rawP = (timeMs.toFloat() / totalTargetMs.toFloat()).coerceIn(0.05f, 0.95f)
                    if (rawP > maxProgressSoFar) {
                        maxProgressSoFar = rawP
                        try {
                            runBlocking(Dispatchers.Main) {
                                onProgress(maxProgressSoFar)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            // 5. Execute FFmpeg with hardware acceleration & fallback
            onProgress(0.05f)
            var session = FFmpegKit.execute(cmd)

            if (!ReturnCode.isSuccess(session.returnCode) && cmd.contains("h264_mediacodec")) {
                Log.w(TAG, "Hardware h264_mediacodec failed, falling back to multi-threaded MPEG-4")
                val fallbackCmd = cmd.replace(Regex("-c:v h264_mediacodec -b:v \\S+"), "-c:v mpeg4 -b:v 2000k -threads 4")
                session = FFmpegKit.execute(fallbackCmd)
            }

            onProgress(0.95f)

            try { inputFile.delete() } catch (_: Throwable) {}

            val returnCode = session.returnCode
            val logs = session.logsAsString ?: ""

            if (ReturnCode.isSuccess(returnCode) && tempOutputFile.exists() && tempOutputFile.length() > 0) {
                val finalFile = saveToFinalDestination(context, tempOutputFile, params, isAudioOutput)
                try { tempOutputFile.delete() } catch (_: Throwable) {}

                var scannedUri: Uri? = null
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null) { _, uri ->
                        scannedUri = uri
                    }
                } catch (_: Throwable) {}

                onProgress(1.0f)
                ConverterCompressorResult.Success(finalFile, scannedUri)
            } else {
                val err = if (logs.isBlank()) "FFmpeg returned code $returnCode" else logs.takeLast(400)
                try { tempOutputFile.delete() } catch (_: Throwable) {}
                ConverterCompressorResult.Failure("Conversion Failed:\n$err")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Conversion exception", e)
            ConverterCompressorResult.Failure(e.localizedMessage ?: "Error during format conversion")
        }
    }

    private fun isAudioFormat(outputFormat: String, inputMediaType: MediaType): Boolean {
        val audioExts = setOf("mp3", "aac", "m4a", "wav", "flac", "ogg", "opus", "ac3", "aiff", "amr", "wma")
        return audioExts.contains(outputFormat.lowercase()) || (outputFormat == "original" && inputMediaType == MediaType.AUDIO)
    }

    private fun getFinalOutputExtension(p: ConverterCompressorParams, isAudioOutput: Boolean): String {
        if (p.outputFormat.isNotBlank() && p.outputFormat != "original") {
            return p.outputFormat.lowercase()
        }
        return p.inputFileName.substringAfterLast('.', if (isAudioOutput) "mp3" else "mp4").lowercase()
    }

    private fun buildCommand(
        inputPath: String,
        outputPath: String,
        p: ConverterCompressorParams,
        isAudioOutput: Boolean
    ): String {
        val sb = StringBuilder()

        sb.append("-y")

        if (p.trimStartMs > 0) {
            sb.append(" -ss ${formatTimeMs(p.trimStartMs)}")
        }

        sb.append(" -i ").append(inputPath)

        if (p.trimEndMs > 0 && p.trimEndMs > p.trimStartMs) {
            val durMs = p.trimEndMs - p.trimStartMs
            sb.append(" -t ${formatTimeMs(durMs)}")
        }

        if (isAudioOutput) {
            sb.append(" -vn -map 0:a:0?")
        } else {
            sb.append(" -map 0:v:0? -map 0:a:0?")
        }

        // Build Video Filters & Video Codec
        if (!isAudioOutput) {
            val videoFilters = buildVideoFilters(p)
            if (videoFilters.isNotEmpty()) {
                sb.append(" -filter:v \"${videoFilters.joinToString(",")}\"")
            }

            // Calculate Target Video Bitrate
            val targetVideoBitrate = calculateTargetVideoBitrate(p)
            sb.append(" -c:v h264_mediacodec -b:v $targetVideoBitrate -pix_fmt yuv420p")
        }

        // Build Audio Filters & Audio Codec
        val audioFilters = buildAudioFilters(p)
        if (audioFilters.isNotEmpty()) {
            sb.append(" -filter:a \"${audioFilters.joinToString(",")}\"")
        }

        val ext = getFinalOutputExtension(p, isAudioOutput)
        val aCodec = audioCodecFor(ext)
        sb.append(" -c:a $aCodec")

        val targetAudioBitrate = if (p.audioBitrate.isNotBlank() && p.audioBitrate != "original") {
            p.audioBitrate
        } else "192k"

        sb.append(" -b:a $targetAudioBitrate")
        if (p.sampleRate > 0) sb.append(" -ar ${p.sampleRate}")
        if (p.channels > 0) sb.append(" -ac ${p.channels}")

        sb.append(" ").append(outputPath)
        return sb.toString()
    }

    private fun calculateTargetVideoBitrate(p: ConverterCompressorParams): String {
        val durSec = if (p.trimEndMs > 0 && p.trimEndMs > p.trimStartMs) {
            (p.trimEndMs - p.trimStartMs) / 1000f
        } else {
            (p.sourceDurationMs / 1000f).coerceAtLeast(1f)
        }

        val effectiveSourceBps = if (p.originalBitrateBps > 0) {
            p.originalBitrateBps
        } else if (p.originalFileSizeBytes > 0 && durSec > 0) {
            ((p.originalFileSizeBytes * 8f) / durSec).toLong()
        } else 3000000L

        // Resolution scaling ratio vs original
        val resRatio = when (p.targetResolution.lowercase()) {
            "4k" -> 2.5f
            "1080p" -> 1.0f
            "720p" -> 0.45f
            "480p" -> 0.20f
            "360p" -> 0.12f
            "240p" -> 0.06f
            else -> 1.0f
        }

        val baseTargetBps = when (p.compressionMode.lowercase()) {
            "original", "ultra_lossless" -> (effectiveSourceBps * 0.95f).toLong()
            "visually_lossless" -> (effectiveSourceBps * 0.75f).toLong()
            "balanced" -> (effectiveSourceBps * 0.50f).toLong()
            "high" -> (effectiveSourceBps * 0.30f).toLong()
            "extreme" -> (effectiveSourceBps * 0.18f).toLong()
            "custom_mb" -> {
                if (p.targetSizeMb > 0f && durSec > 0f) {
                    val totalBits = p.targetSizeMb * 1024f * 1024f * 8f
                    val totalBps = totalBits / durSec
                    val videoBps = (totalBps - 192000f).coerceAtLeast(300000f)
                    return "${(videoBps / 1000f).toLong().coerceIn(250L, 15000L)}k"
                } else (effectiveSourceBps * 0.50f).toLong()
            }
            else -> (effectiveSourceBps * 0.50f).toLong()
        }

        val scaledBps = (baseTargetBps * resRatio).toLong()
        val targetKbps = (scaledBps / 1000L).coerceIn(250L, 15000L)
        return "${targetKbps}k"
    }

    private fun buildVideoFilters(p: ConverterCompressorParams): List<String> {
        val filters = mutableListOf<String>()

        val speed = p.speedMultiplier.coerceIn(0.10f, 8.0f)
        if (speed != 1.0f) {
            val ptsRatio = 1.0f / speed
            filters.add(String.format(Locale.US, "setpts=%.6f*PTS", ptsRatio))
        }

        if (p.reverse) filters.add("reverse")

        val scaleFilter = when (p.targetResolution.lowercase()) {
            "4k" -> "scale=3840:2160:force_original_aspect_ratio=decrease,pad=3840:2160:(ow-iw)/2:(oh-ih)/2"
            "1080p" -> "scale=1920:1080:force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2"
            "720p" -> "scale=1280:720:force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2"
            "480p" -> "scale=854:480:force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2"
            "360p" -> "scale=640:360:force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2"
            else -> ""
        }
        if (scaleFilter.isNotBlank()) filters.add(scaleFilter)

        if (p.targetFps > 0) {
            filters.add("fps=${p.targetFps}")
        }

        return filters
    }

    private fun buildAudioFilters(p: ConverterCompressorParams): List<String> {
        val filters = mutableListOf<String>()

        if (p.reverse) filters.add("areverse")

        val speed = p.speedMultiplier.coerceIn(0.10f, 8.0f)
        if (p.isPitchLocked) {
            if (speed != 1.0f) filters.addAll(buildAtempoChain(speed))
        } else {
            if (p.pitchSemitones != 0.0f || speed != 1.0f) {
                val pitchRatio = Math.pow(2.0, p.pitchSemitones / 12.0).toFloat()
                val baseRate = if (p.sampleRate > 0) p.sampleRate else 44100
                val combinedRate = (baseRate * pitchRatio).toInt().coerceAtLeast(8000)
                filters.add("asetrate=$combinedRate")
                filters.add("aresample=$baseRate")
                if (speed != 1.0f) filters.addAll(buildAtempoChain(speed))
            }
        }

        if (p.volumeDb != 0f) {
            filters.add(String.format(Locale.US, "volume=%.1fdB", p.volumeDb))
        }

        when (p.eqPreset) {
            "bass_boost" -> filters.add("equalizer=f=100:width_type=h:width=200:g=6")
            "vocal_boost" -> filters.add("equalizer=f=3000:width_type=h:width=1500:g=5")
            "treble_boost" -> filters.add("equalizer=f=10000:width_type=h:width=4000:g=6")
            "clarity" -> {
                filters.add("equalizer=f=100:width_type=h:width=200:g=-3")
                filters.add("equalizer=f=3500:width_type=h:width=1500:g=4")
            }
        }

        if (p.channels == 1) filters.add("aformat=channel_layouts=mono")
        else if (p.channels == 2) filters.add("aformat=channel_layouts=stereo")

        return filters
    }

    private fun buildAtempoChain(speed: Float): List<String> {
        val filters = mutableListOf<String>()
        var remaining = speed.toDouble()
        while (remaining > 2.0) {
            filters.add("atempo=2.0")
            remaining /= 2.0
        }
        while (remaining < 0.5) {
            filters.add("atempo=0.5")
            remaining /= 0.5
        }
        if (remaining != 1.0) {
            filters.add(String.format(Locale.US, "atempo=%.6f", remaining))
        }
        return filters
    }

    private fun audioCodecFor(ext: String): String = when (ext) {
        "mp3" -> "mp3"
        "aac", "m4a" -> "aac"
        "ogg" -> "flac"
        "opus", "mka" -> "aac"
        "flac" -> "flac"
        "wav" -> "pcm_s16le"
        "aiff", "aif" -> "pcm_s16be"
        "ac3" -> "ac3"
        "amr" -> "amr_nb"
        else -> "aac"
    }

    private fun saveToFinalDestination(
        context: Context,
        tempFile: File,
        params: ConverterCompressorParams,
        isAudioOutput: Boolean
    ): File {
        val rawBase = if (params.customFileName.isNotBlank()) params.customFileName else params.inputFileName.substringBeforeLast('.')
        val ext = getFinalOutputExtension(params, isAudioOutput)
        val safeName = rawBase.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val fileName = if (params.customFileName.isNotBlank()) "${safeName}.${ext}" else "${safeName}_converted.${ext}"

        val targetSubDir = if (isAudioOutput) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val publicBase = File(File(Environment.getExternalStoragePublicDirectory(targetSubDir), "VocoNexus"), "FormatConverter")

        try {
            if (!publicBase.exists()) publicBase.mkdirs()
            val destFile = File(publicBase, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Saved converted file to public directory: ${destFile.absolutePath}")
            return destFile
        } catch (e: Throwable) {
            Log.w(TAG, "Public dir write failed (${e.message}), falling back to app ext dir", e)
        }

        val appExtDir = File(File(context.getExternalFilesDir(targetSubDir), "VocoNexus"), "FormatConverter")
        if (!appExtDir.exists()) appExtDir.mkdirs()
        val destFile = File(appExtDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        Log.d(TAG, "Saved converted file to app external dir: ${destFile.absolutePath}")
        return destFile
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val ext = fileName.substringAfterLast('.', "tmp")
            val cacheFile = File(context.cacheDir, "conv_input_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
        } catch (e: Throwable) {
            Log.e(TAG, "Error copying URI to cache: $uri", e)
            null
        }
    }

    private fun formatTimeMs(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
    }
}
