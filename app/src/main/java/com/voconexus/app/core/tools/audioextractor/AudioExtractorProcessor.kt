package com.voconexus.app.core.tools.audioextractor

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

private const val TAG = "AudioExtractorProcessor"

data class AudioExtractorParams(
    val inputUri: Uri,
    val inputFileName: String,
    val sourceDurationMs: Long = 0L,
    val originalBitrateBps: Long = 0L,
    val originalAudioCodec: String = "",

    // Output Format & Quality
    val outputFormat: String = "original",     // "original", "mp3", "aac", "wav", "flac", "ogg", "m4a"
    val audioBitrate: String = "original",      // "original", "320k", "256k", "192k", "128k", "96k"
    val sampleRate: Int = -1,                   // -1 = original, 48000, 44100, 32000, 16000
    val channels: Int = -1,                     // -1 = original, 1 = mono, 2 = stereo

    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,                  // -1 = full duration

    // Volume & Fade
    val volumeDb: Float = 0f,
    val normalize: Boolean = false,
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,

    // Speed & Pitch
    val speedMultiplier: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val isPitchLocked: Boolean = true,
    val reverse: Boolean = false,

    // Custom Filename & EQ
    val customFileName: String = "",
    val eqPreset: String = "flat"
)

sealed class AudioExtractorResult {
    data class Success(val outputFile: File, val outputUri: Uri?) : AudioExtractorResult()
    data class Failure(val error: String) : AudioExtractorResult()
}

object AudioExtractorProcessor {

    suspend fun process(
        context: Context,
        params: AudioExtractorParams,
        onProgress: suspend (Float) -> Unit
    ): AudioExtractorResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Audio Extraction for file: ${params.inputFileName}")

            // 1. Copy input to cache file
            val inputFile = copyUriToCache(context, params.inputUri, params.inputFileName)
                ?: return@withContext AudioExtractorResult.Failure("Could not read input video/audio file.")

            Log.d(TAG, "Input file cached to: ${inputFile.absolutePath} (size: ${inputFile.length()} bytes)")

            // 2. Determine output format & extension
            val ext = getFinalOutputExtension(params)
            val tempOutputFile = File(context.cacheDir, "ext_audio_${System.currentTimeMillis()}.$ext")

            // 3. Build FFmpeg command
            val cmd = buildCommand(inputFile.absolutePath, tempOutputFile.absolutePath, params)
            Log.d(TAG, "Generated FFmpeg Audio Extraction command: $cmd")

            // 4. Set statistics callback for progress updates
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

            // 5. Execute FFmpeg
            onProgress(0.05f)
            val session = FFmpegKit.execute(cmd)
            onProgress(0.95f)

            // Delete input cache file
            try { inputFile.delete() } catch (_: Throwable) {}

            val returnCode = session.returnCode
            val logs = session.logsAsString ?: "No log output"
            Log.d(TAG, "FFmpeg return code: $returnCode, logs: ${logs.takeLast(300)}")

            if (ReturnCode.isSuccess(returnCode) && tempOutputFile.exists() && tempOutputFile.length() > 0) {
                Log.d(TAG, "Audio extraction successful. Size: ${tempOutputFile.length()} bytes")

                // 6. Save to Music/VocoNexus directory
                val finalFile = saveToMusicDirectory(context, tempOutputFile, params)
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
                AudioExtractorResult.Success(finalFile, scannedUri)
            } else {
                val errorDetails = if (logs.isBlank()) "FFmpeg returned code $returnCode" else logs.takeLast(450)
                Log.e(TAG, "Audio Extraction Failed: $errorDetails")
                try { tempOutputFile.delete() } catch (_: Throwable) {}
                AudioExtractorResult.Failure("Extraction Failed:\n$errorDetails")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Extraction exception", e)
            AudioExtractorResult.Failure(e.localizedMessage ?: "Audio extraction error occurred")
        }
    }

    private fun getFinalOutputExtension(p: AudioExtractorParams): String {
        return if (p.outputFormat.isNotBlank() && p.outputFormat != "original") {
            p.outputFormat.lowercase()
        } else {
            // Infer extension from original codec
            when (p.originalAudioCodec.lowercase()) {
                "mp3" -> "mp3"
                "aac" -> "m4a"
                "flac" -> "flac"
                "vorbis", "ogg" -> "ogg"
                "opus" -> "opus"
                "pcm", "wav" -> "wav"
                else -> "m4a"
            }
        }
    }

    private fun buildCommand(inputPath: String, outputPath: String, p: AudioExtractorParams): String {
        val sb = StringBuilder()

        sb.append("-y")  // overwrite output

        // Fast seek trim start
        if (p.trimStartMs > 0) {
            sb.append(" -ss ${formatTimeMs(p.trimStartMs)}")
        }

        sb.append(" -i ").append(inputPath)

        // Duration limit
        if (p.trimEndMs > 0 && p.trimEndMs > p.trimStartMs) {
            val durationMs = p.trimEndMs - p.trimStartMs
            sb.append(" -t ${formatTimeMs(durationMs)}")
        }

        // Disable video stream (audio extraction)
        sb.append(" -vn -map 0:a:0?")

        val isOriginalFormat = p.outputFormat == "original" || p.outputFormat.isBlank()
        val isOriginalBitrate = p.audioBitrate == "original" || p.audioBitrate.isBlank()
        val isOriginalSampleRate = p.sampleRate <= 0
        val isOriginalChannels = p.channels <= 0

        val hasAudioFilters = p.reverse || p.volumeDb != 0f || p.normalize ||
                p.fadeInSec > 0f || p.fadeOutSec > 0f || p.speedMultiplier != 1.0f ||
                p.pitchSemitones != 0.0f

        // ⚡ INSTANT 0.1s STREAM COPY WHEN ORIGINAL IS SELECTED WITH NO FILTERS!
        if (isOriginalFormat && isOriginalBitrate && isOriginalSampleRate && isOriginalChannels && !hasAudioFilters) {
            Log.d(TAG, "Using Instant 0.1s Stream Copy (-c:a copy) for 100% bit-for-bit original audio extraction!")
            sb.append(" -c:a copy")
        } else {
            val audioFilters = buildAudioFilters(p)
            if (audioFilters.isNotEmpty()) {
                sb.append(" -filter:a \"${audioFilters.joinToString(",")}\"")
            }

            val ext = getFinalOutputExtension(p)
            val codec = audioCodecFor(ext)
            sb.append(" -c:a $codec")

            // Bitrate selection
            val targetBitrate = if (p.audioBitrate.isNotBlank() && p.audioBitrate != "original") {
                p.audioBitrate
            } else if (p.originalBitrateBps > 0) {
                "${(p.originalBitrateBps / 1000L).coerceIn(96L, 320L)}k"
            } else {
                "192k"
            }
            sb.append(" -b:a $targetBitrate")

            if (p.sampleRate > 0) sb.append(" -ar ${p.sampleRate}")
            if (p.channels > 0) sb.append(" -ac ${p.channels}")
        }

        // Output path without quotes
        sb.append(" ").append(outputPath)
        return sb.toString()
    }

    private fun buildAudioFilters(p: AudioExtractorParams): List<String> {
        val filters = mutableListOf<String>()

        if (p.reverse) filters.add("areverse")

        val speed = p.speedMultiplier.coerceIn(0.10f, 8.0f)
        if (p.isPitchLocked) {
            if (speed != 1.0f) {
                filters.addAll(buildAtempoChain(speed))
            }
        } else {
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

        if (p.volumeDb != 0f) {
            filters.add(String.format(Locale.US, "volume=%.1fdB", p.volumeDb))
        }

        if (p.normalize) {
            filters.add("loudnorm=I=-14:TP=-1.5:LRA=11")
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

        if (p.fadeInSec > 0f) {
            filters.add(String.format(Locale.US, "afade=t=in:st=0:d=%.2f", p.fadeInSec))
        }

        if (p.fadeOutSec > 0f) {
            val rawDurSec = if (p.trimEndMs > 0 && p.trimEndMs > p.trimStartMs) {
                (p.trimEndMs - p.trimStartMs) / 1000f
            } else -1f
            if (rawDurSec > 0) {
                val outputDurSec = rawDurSec / p.speedMultiplier
                val fadeStart = outputDurSec - p.fadeOutSec
                if (fadeStart > 0) {
                    filters.add(String.format(Locale.US, "afade=t=out:st=%.2f:d=%.2f", fadeStart, p.fadeOutSec))
                }
            } else {
                filters.add(String.format(Locale.US, "afade=t=out:d=%.2f:eval=frame", p.fadeOutSec))
            }
        }

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
        else -> "aac"
    }

    private fun saveToMusicDirectory(context: Context, tempFile: File, params: AudioExtractorParams): File {
        val rawBase = if (params.customFileName.isNotBlank()) params.customFileName else params.inputFileName.substringBeforeLast('.')
        val ext = getFinalOutputExtension(params)
        val safeName = rawBase.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val fileName = if (params.customFileName.isNotBlank()) "${safeName}.${ext}" else "${safeName}_extracted.${ext}"

        val publicMusic = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "VocoNexus"), "AudioExtractor")

        try {
            if (!publicMusic.exists()) publicMusic.mkdirs()
            val destFile = File(publicMusic, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Saved extracted audio to public Music directory: ${destFile.absolutePath}")
            return destFile
        } catch (e: Throwable) {
            Log.w(TAG, "Public Music directory write failed (${e.message}), falling back to app external dir", e)
        }

        val appExtDir = File(File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "VocoNexus"), "AudioExtractor")
        if (!appExtDir.exists()) appExtDir.mkdirs()
        val destFile = File(appExtDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        Log.d(TAG, "Saved extracted audio to app external directory: ${destFile.absolutePath}")
        return destFile
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val ext = fileName.substringAfterLast('.', "tmp")
            val cacheFile = File(context.cacheDir, "ext_input_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile
            } else {
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
