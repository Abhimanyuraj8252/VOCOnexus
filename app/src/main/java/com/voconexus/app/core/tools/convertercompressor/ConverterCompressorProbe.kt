package com.voconexus.app.core.tools.convertercompressor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformationSession
import com.voconexus.app.core.tools.speedpitch.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConverterCompressorInfo(
    val uri: Uri,
    val fileName: String,
    val mediaType: MediaType,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val containerFormat: String,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val frameRate: Float = 0f,
    val totalBitrateBps: Long = 0L,
    val videoBitrateBps: Long = 0L,
    val audioBitrateBps: Long = 0L,
    val sampleRate: Int = 44100,
    val channels: Int = 2
)

object ConverterCompressorProbe {

    private val audioExtensions = setOf(
        "mp3", "aac", "m4a", "ogg", "opus", "flac", "wav", "aiff", "aif",
        "wma", "amr", "awb", "ape", "ac3", "mp2", "ra", "3ga", "caf", "mka"
    )

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "mts",
        "m4v", "wmv", "mpg", "mpeg", "vob", "asf", "m2ts", "ogv", "divx"
    )

    suspend fun probe(context: Context, uri: Uri): ConverterCompressorInfo = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri) ?: "Unknown"
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mediaType = when {
            videoExtensions.contains(extension) -> MediaType.VIDEO
            audioExtensions.contains(extension) -> MediaType.AUDIO
            else -> detectTypeFromRetriever(context, uri)
        }

        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val fileSize = getFileSize(context, uri)

            val effectiveTotalBitrate = if (rawBitrate > 0) rawBitrate else {
                if (fileSize > 0 && durationMs > 0) {
                    (fileSize * 8L * 1000L) / durationMs
                } else 0L
            }

            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

            val sampleRate = probeSampleRate(context, uri)
            val channels = probeChannels(context, uri)

            // Audio bitrate vs Video bitrate split estimation
            val audioBps = if (mediaType == MediaType.AUDIO) effectiveTotalBitrate else 192000L
            val videoBps = if (mediaType == MediaType.VIDEO) (effectiveTotalBitrate - audioBps).coerceAtLeast(500000L) else 0L

            ConverterCompressorInfo(
                uri = uri,
                fileName = fileName,
                mediaType = mediaType,
                durationMs = durationMs,
                fileSizeBytes = fileSize,
                containerFormat = extension.uppercase(),
                videoCodec = if (mediaType == MediaType.VIDEO) "H.264" else "",
                audioCodec = mimeType.substringAfterLast('/'),
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                frameRate = if (frameRate > 0) frameRate else 30f,
                totalBitrateBps = effectiveTotalBitrate,
                videoBitrateBps = videoBps,
                audioBitrateBps = audioBps,
                sampleRate = sampleRate,
                channels = channels
            )
        } catch (e: Throwable) {
            ConverterCompressorInfo(
                uri = uri,
                fileName = fileName,
                mediaType = mediaType,
                durationMs = 0L,
                fileSizeBytes = 0L,
                containerFormat = extension.uppercase()
            )
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun probeSampleRate(context: Context, uri: Uri): Int {
        return try {
            val path = getRealPath(context, uri) ?: return 44100
            val session: MediaInformationSession = FFprobeKit.getMediaInformation(path)
            val info = session.mediaInformation ?: return 44100
            val streams = info.streams ?: return 44100
            for (stream in streams) {
                try {
                    val sr = stream.sampleRate
                    if (sr != null) return sr.toIntOrNull() ?: 44100
                } catch (_: Throwable) {}
            }
            44100
        } catch (_: Throwable) {
            44100
        }
    }

    private fun probeChannels(context: Context, uri: Uri): Int {
        return try {
            val path = getRealPath(context, uri) ?: return 2
            val session: MediaInformationSession = FFprobeKit.getMediaInformation(path)
            val info = session.mediaInformation ?: return 2
            val streams = info.streams ?: return 2
            for (stream in streams) {
                try {
                    val ch = stream.channelLayout
                    if (ch != null) {
                        return when {
                            ch.contains("mono") -> 1
                            ch.contains("stereo") -> 2
                            ch.contains("5.1") -> 6
                            ch.contains("7.1") -> 8
                            else -> stream.allProperties?.optInt("channels", 2) ?: 2
                        }
                    }
                } catch (_: Throwable) {}
            }
            2
        } catch (_: Throwable) {
            2
        }
    }

    private fun detectTypeFromRetriever(context: Context, uri: Uri): MediaType {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            when {
                hasVideo == "yes" -> MediaType.VIDEO
                hasAudio == "yes" -> MediaType.AUDIO
                else -> MediaType.NONE
            }
        } catch (_: Throwable) {
            MediaType.NONE
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) return cursor.getString(nameIdx)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIdx != -1) return cursor.getLong(sizeIdx)
                    }
                }
            }
            val path = getRealPath(context, uri) ?: return 0L
            java.io.File(path).length()
        } catch (_: Throwable) {
            0L
        }
    }

    private fun getRealPath(context: Context, uri: Uri): String? {
        return try {
            val file = java.io.File(context.cacheDir, "conv_temp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
