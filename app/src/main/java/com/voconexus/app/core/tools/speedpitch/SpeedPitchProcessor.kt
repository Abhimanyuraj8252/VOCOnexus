package com.voconexus.app.core.tools.speedpitch

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "SpeedPitchProcessor"

data class ProcessingParams(
    val inputUri: Uri,
    val inputFileName: String,
    val mediaType: MediaType,
    val sourceDurationMs: Long = 0L,
    val originalBitrateBps: Long = 0L,

    // Speed & Pitch
    val speedMultiplier: Float = 1.0f,          // 0.10 – 8.0
    val pitchSemitones: Float = 0.0f,           // -24 to +24 (0 = auto-correct)
    val isPitchLocked: Boolean = true,           // true = pitch preserved on speed change

    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,                   // -1 = end of file

    // Fade
    val fadeInDurationSec: Float = 0f,
    val fadeOutDurationSec: Float = 0f,

    // Volume
    val volumeDb: Float = 0f,                    // -30 to +30
    val normalize: Boolean = false,

    // Equalizer (gain in dB for each band)
    val eqBass: Float = 0f,       // ~80 Hz
    val eqLowMid: Float = 0f,     // ~250 Hz
    val eqMid: Float = 0f,        // ~1000 Hz
    val eqHighMid: Float = 0f,    // ~4000 Hz
    val eqTreble: Float = 0f,     // ~12000 Hz

    // Advanced Audio
    val channels: Int = -1,          // -1 = keep original, 1 = mono, 2 = stereo
    val sampleRate: Int = -1,        // -1 = keep original
    val reverse: Boolean = false,

    // Export
    val outputFormat: String = "",   // e.g. "mp3", "aac", "opus", "" = same as input
    val audioBitrate: String = "",   // e.g. "320k", "" = auto
    val videoBitrate: String = "",   // e.g. "4M", "" = auto
    val preserveMetadata: Boolean = true
)

sealed class ProcessingResult {
    data class Success(val outputFile: File, val outputUri: Uri?) : ProcessingResult()
    data class Failure(val error: String) : ProcessingResult()
}

object SpeedPitchProcessor {

    suspend fun process(
        context: Context,
        params: ProcessingParams,
        onProgress: suspend (Float) -> Unit
    ): ProcessingResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Speed & Pitch export for file: ${params.inputFileName}")

            // 1. Copy input to cache file (without spaces in path)
            val inputFile = copyUriToCache(context, params.inputUri, params.inputFileName)
                ?: return@withContext ProcessingResult.Failure("Could not read input file. Please verify file access.")

            Log.d(TAG, "Input file cached to: ${inputFile.absolutePath} (size: ${inputFile.length()} bytes)")

            // 2. Prepare temporary output file in cache
            val ext = getOutputExtension(params)
            val tempOutputFile = File(context.cacheDir, "spc_output_${System.currentTimeMillis()}.$ext")

            // 3. Build FFmpeg command (NO literal quotes in file paths)
            val cmd = buildCommand(inputFile.absolutePath, tempOutputFile.absolutePath, params)
            Log.d(TAG, "Generated FFmpeg command: $cmd")

            // 4. Set real-time stats callback for UI progress bar
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

            // 5. Execute FFmpeg with hardware acceleration & automatic fallback
            onProgress(0.05f)
            var session = FFmpegKit.execute(cmd)

            if (!ReturnCode.isSuccess(session.returnCode) && cmd.contains("h264_mediacodec")) {
                Log.w(TAG, "Hardware h264_mediacodec unavailable on this build, falling back to multi-threaded MPEG-4 QScale 2")
                val fallbackCmd = cmd.replace(Regex("-c:v h264_mediacodec -b:v \\S+"), "-c:v mpeg4 -qscale:v 2 -threads 8")
                session = FFmpegKit.execute(fallbackCmd)
            }
            onProgress(0.95f)

            // Delete input cache file
            try { inputFile.delete() } catch (_: Throwable) {}

            val returnCode = session.returnCode
            val logs = session.logsAsString ?: "No log output"
            Log.d(TAG, "FFmpeg return code: $returnCode, logs: ${logs.takeLast(300)}")

            if (ReturnCode.isSuccess(returnCode) && tempOutputFile.exists() && tempOutputFile.length() > 0) {
                Log.d(TAG, "Export succeeded. Output temp size: ${tempOutputFile.length()} bytes")

                // 6. Save to final public or app directory
                val finalFile = saveToFinalDestination(context, tempOutputFile, params)
                try { tempOutputFile.delete() } catch (_: Throwable) {}

                var scannedUri: Uri? = null
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null) { _, uri ->
                        scannedUri = uri
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "MediaScanner error", e)
                }

                onProgress(1.0f)
                ProcessingResult.Success(finalFile, scannedUri)
            } else {
                val errorDetails = if (logs.isBlank()) "FFmpeg returned code $returnCode" else logs.takeLast(450)
                Log.e(TAG, "Export Failed: $errorDetails")
                try { tempOutputFile.delete() } catch (_: Throwable) {}
                ProcessingResult.Failure("Export Failed:\n$errorDetails")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Process exception", e)
            ProcessingResult.Failure(e.localizedMessage ?: "Processing error occurred")
        }
    }

    private fun getOutputExtension(params: ProcessingParams): String {
        val isAudio = params.mediaType == MediaType.AUDIO
        return if (params.outputFormat.isNotBlank()) params.outputFormat.lowercase()
        else params.inputFileName.substringAfterLast('.', if (isAudio) "mp3" else "mp4").lowercase()
    }

    private fun buildCommand(inputPath: String, outputPath: String, p: ProcessingParams): String {
        val sb = StringBuilder()

        sb.append("-y")  // overwrite output

        // Fast seeking input start
        if (p.trimStartMs > 0) {
            sb.append(" -ss ${formatTimeMs(p.trimStartMs)}")
        }

        // Input file path without quotes
        sb.append(" -i ").append(inputPath)

        // Duration limit AFTER input file so it applies to trimmed range
        if (p.trimEndMs > 0 && p.trimEndMs > p.trimStartMs) {
            val durationMs = p.trimEndMs - p.trimStartMs
            sb.append(" -t ${formatTimeMs(durationMs)}")
        }

        // Map primary video & audio streams (ignoring attached cover pictures)
        if (p.mediaType == MediaType.VIDEO) {
            sb.append(" -map 0:v:0? -map 0:a:0?")
        } else {
            sb.append(" -map 0:a:0?")
        }

        val audioFilters = buildAudioFilters(p)
        val videoFilters = buildVideoFilters(p)

        if (audioFilters.isNotEmpty()) {
            sb.append(" -filter:a \"${audioFilters.joinToString(",")}\"")
        }
        if (p.mediaType == MediaType.VIDEO && videoFilters.isNotEmpty()) {
            sb.append(" -filter:v \"${videoFilters.joinToString(",")}\"")
        }

        // Codec & Quality settings
        val ext = getOutputExtension(p)
        val isVideo = p.mediaType == MediaType.VIDEO
        val isVideoSpeedModified = p.speedMultiplier != 1.0f || p.reverse

        if (!isVideo) {
            val codec = audioCodecFor(ext)
            sb.append(" -c:a $codec")
            val aBitrate = if (p.audioBitrate.isNotBlank()) p.audioBitrate else {
                if (p.originalBitrateBps > 0 && p.originalBitrateBps < 500000L) {
                    "${(p.originalBitrateBps / 1000L).coerceIn(96L, 320L)}k"
                } else "192k"
            }
            sb.append(" -b:a $aBitrate")
            if (p.sampleRate > 0) sb.append(" -ar ${p.sampleRate}")
            if (p.channels > 0) sb.append(" -ac ${p.channels}")
        } else {
            if (!isVideoSpeedModified && p.videoBitrate.isBlank()) {
                Log.d(TAG, "Using ultra-fast 0.2s stream copy for video track!")
                sb.append(" -c:v copy")
            } else {
                val targetBitrateStr = if (p.videoBitrate.isNotBlank()) {
                    p.videoBitrate
                } else if (p.originalBitrateBps > 0) {
                    val origKbps = (p.originalBitrateBps / 1000L).coerceIn(600L, 8000L)
                    "${origKbps}k"
                } else {
                    "2200k"
                }
                Log.d(TAG, "Using GPU Hardware MediaCodec video encoder with original matching bitrate: $targetBitrateStr (Source Bps: ${p.originalBitrateBps})")
                sb.append(" -c:v h264_mediacodec -b:v $targetBitrateStr -pix_fmt yuv420p")
            }
            val aCodec = audioCodecFor(ext)
            sb.append(" -c:a $aCodec")
            if (p.audioBitrate.isNotBlank()) sb.append(" -b:a ${p.audioBitrate}")
            if (p.sampleRate > 0) sb.append(" -ar ${p.sampleRate}")
            if (p.channels > 0) sb.append(" -ac ${p.channels}")
        }

        // Metadata
        if (p.preserveMetadata) sb.append(" -map_metadata 0")

        // Output file path without quotes
        sb.append(" ").append(outputPath)
        return sb.toString()
    }

    private fun buildAudioFilters(p: ProcessingParams): List<String> {
        val filters = mutableListOf<String>()

        // Reverse (must be first)
        if (p.reverse) filters.add("areverse")

        // Speed + Pitch
        val speed = p.speedMultiplier.coerceIn(0.10f, 8.0f)
        if (p.isPitchLocked) {
            // Pitch-preserving time stretch via atempo chain
            if (speed != 1.0f) {
                filters.addAll(buildAtempoChain(speed))
            }
        } else {
            // Manual pitch shift via asetrate + aresample + optional tempo correction
            if (p.pitchSemitones != 0.0f || speed != 1.0f) {
                val pitchRatio = Math.pow(2.0, p.pitchSemitones / 12.0).toFloat()
                val baseRate = if (p.sampleRate > 0) p.sampleRate else 44100
                val combinedRate = (baseRate * pitchRatio).toInt().coerceAtLeast(8000)
                filters.add("asetrate=$combinedRate")
                filters.add("aresample=$baseRate")
                if (speed != 1.0f) {
                    filters.addAll(buildAtempoChain(speed))
                }
            }
        }

        // Volume
        if (p.volumeDb != 0f) {
            filters.add(String.format(Locale.US, "volume=%.1fdB", p.volumeDb))
        }

        // Normalize (loudnorm)
        if (p.normalize) {
            filters.add("loudnorm=I=-14:TP=-1.5:LRA=11")
        }

        // Equalizer
        val hasEq = listOf(p.eqBass, p.eqLowMid, p.eqMid, p.eqHighMid, p.eqTreble).any { it != 0f }
        if (hasEq) {
            filters.add(String.format(Locale.US, "equalizer=f=80:t=o:w=1:g=%.1f", p.eqBass))
            filters.add(String.format(Locale.US, "equalizer=f=250:t=o:w=1:g=%.1f", p.eqLowMid))
            filters.add(String.format(Locale.US, "equalizer=f=1000:t=o:w=1:g=%.1f", p.eqMid))
            filters.add(String.format(Locale.US, "equalizer=f=4000:t=o:w=1:g=%.1f", p.eqHighMid))
            filters.add(String.format(Locale.US, "equalizer=f=12000:t=o:w=1:g=%.1f", p.eqTreble))
        }

        // Channels
        if (p.channels == 1) filters.add("aformat=channel_layouts=mono")
        else if (p.channels == 2) filters.add("aformat=channel_layouts=stereo")

        // Fade In
        if (p.fadeInDurationSec > 0f) {
            filters.add(String.format(Locale.US, "afade=t=in:st=0:d=%.2f", p.fadeInDurationSec))
        }

        // Fade Out
        if (p.fadeOutDurationSec > 0f) {
            val rawDurSec = if (p.trimEndMs > 0 && p.trimEndMs > p.trimStartMs) {
                (p.trimEndMs - p.trimStartMs) / 1000f
            } else -1f
            if (rawDurSec > 0) {
                val outputDurSec = rawDurSec / p.speedMultiplier
                val fadeStart = outputDurSec - p.fadeOutDurationSec
                if (fadeStart > 0) {
                    filters.add(String.format(Locale.US, "afade=t=out:st=%.2f:d=%.2f", fadeStart, p.fadeOutDurationSec))
                }
            } else {
                filters.add(String.format(Locale.US, "afade=t=out:d=%.2f:eval=frame", p.fadeOutDurationSec))
            }
        }

        return filters
    }

    private fun buildVideoFilters(p: ProcessingParams): List<String> {
        val filters = mutableListOf<String>()
        val speed = p.speedMultiplier.coerceIn(0.10f, 8.0f)
        if (speed != 1.0f) {
            val pts = 1.0 / speed
            filters.add(String.format(Locale.US, "setpts=%.6f*PTS", pts))
        }
        if (p.reverse) filters.add("reverse")
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
        "mp2" -> "mp2"
        "wma" -> "aac"
        "amr" -> "aac"
        else -> "aac"
    }

    private fun saveToFinalDestination(context: Context, tempFile: File, params: ProcessingParams): File {
        val isAudio = params.mediaType == MediaType.AUDIO
        val baseName = params.inputFileName.substringBeforeLast('.')
        val ext = getOutputExtension(params)
        val suffix = buildSuffix(params)
        val safeName = baseName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val fileName = "${safeName}${suffix}.${ext}"

        val targetSubDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val publicBase = File(File(Environment.getExternalStoragePublicDirectory(targetSubDir), "VocoNexus"), "SpeedPitchController")

        try {
            if (!publicBase.exists()) publicBase.mkdirs()
            val destFile = File(publicBase, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Saved output file to public directory: ${destFile.absolutePath}")
            return destFile
        } catch (e: Throwable) {
            Log.w(TAG, "Public directory write failed (${e.message}), falling back to app external dir", e)
        }

        val appExtDir = File(File(context.getExternalFilesDir(targetSubDir), "VocoNexus"), "SpeedPitchController")
        if (!appExtDir.exists()) appExtDir.mkdirs()
        val destFile = File(appExtDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        Log.d(TAG, "Saved output file to app external directory: ${destFile.absolutePath}")
        return destFile
    }

    private fun buildSuffix(p: ProcessingParams): String {
        val parts = mutableListOf<String>()
        if (p.speedMultiplier != 1.0f) parts.add(String.format(Locale.US, "%.2fx", p.speedMultiplier))
        if (p.pitchSemitones != 0.0f && !p.isPitchLocked) parts.add("${p.pitchSemitones}st")
        if (p.trimStartMs > 0 || (p.trimEndMs > 0)) parts.add("trim")
        if (p.fadeInDurationSec > 0 || p.fadeOutDurationSec > 0) parts.add("fade")
        if (p.volumeDb != 0f) parts.add("vol")
        if (p.normalize) parts.add("norm")
        if (p.reverse) parts.add("rev")
        return if (parts.isEmpty()) "" else "_${parts.joinToString("_")}"
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val ext = fileName.substringAfterLast('.', "tmp")
            val cacheFile = File(context.cacheDir, "spc_input_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile
            } else {
                Log.e(TAG, "Copied cache file is empty or missing")
                null
            }
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
