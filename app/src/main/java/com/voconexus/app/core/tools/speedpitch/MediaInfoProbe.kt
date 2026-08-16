package com.voconexus.app.core.tools.speedpitch

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MediaType { NONE, AUDIO, VIDEO }

data class MediaInfo(
    val uri: Uri,
    val fileName: String,
    val mediaType: MediaType,
    val durationMs: Long,
    val codec: String,
    val bitrate: Long,          // bps
    val sampleRate: Int,        // Hz (audio)
    val channels: Int,          // audio channels
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoFrameRate: Float = 0f,
    val fileSizeBytes: Long = 0L,
    val containerFormat: String = ""
)

object MediaInfoProbe {

    private val audioExtensions = setOf(
        "mp3", "aac", "m4a", "ogg", "opus", "flac", "wav", "aiff", "aif",
        "wma", "amr", "awb", "ape", "ac3", "mp2", "ra", "3ga", "caf", "mka"
    )

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "mts",
        "m4v", "wmv", "mpg", "mpeg", "vob", "asf", "m2ts", "ogv", "divx",
        "xvid", "f4v", "rmvb", "rm"
    )

    suspend fun probe(context: Context, uri: Uri): MediaInfo = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri) ?: "Unknown"
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mediaType = when {
            videoExtensions.contains(extension) -> MediaType.VIDEO
            audioExtensions.contains(extension) -> MediaType.AUDIO
            else -> detectTypeFromRetriever(context, uri)
        }

        // Use MediaMetadataRetriever for basic info (fast, no FFmpeg overhead for simple metadata)
        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val codec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // METADATA_KEY_VIDEO_CODEC = 39, METADATA_KEY_AUDIO_CODEC = 38 (API 33+)
                when (mediaType) {
                    MediaType.VIDEO -> retriever.extractMetadata(39) ?: ""
                    MediaType.AUDIO -> retriever.extractMetadata(38) ?: ""
                    MediaType.NONE -> ""
                }
            } else {
                // Derive codec hint from MIME type on older APIs
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
                when {
                    mime.contains("mp4") || mime.contains("avc") -> "h264"
                    mime.contains("hevc") || mime.contains("h265") -> "h265"
                    mime.contains("vp8") -> "vp8"
                    mime.contains("vp9") -> "vp9"
                    mime.contains("mp3") -> "mp3"
                    mime.contains("aac") -> "aac"
                    mime.contains("ogg") || mime.contains("vorbis") -> "vorbis"
                    mime.contains("opus") -> "opus"
                    mime.contains("flac") -> "flac"
                    mime.contains("wav") || mime.contains("pcm") -> "pcm"
                    else -> mime.substringAfterLast("/").take(8)
                }
            }
            val sampleRate = if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
                // FFprobeKit is more reliable for sample rate
                probeSampleRate(context, uri)
            } else 0
            val channels = probeChannels(context, uri)
            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            val fileSize = getFileSize(context, uri)
            val effectiveBitrate = if (bitrate > 0) bitrate else {
                if (fileSize > 0 && durationMs > 0) {
                    (fileSize * 8L * 1000L) / durationMs
                } else 0L
            }
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

            MediaInfo(
                uri = uri,
                fileName = fileName,
                mediaType = mediaType,
                durationMs = durationMs,
                codec = codec.ifBlank { mimeType.substringAfterLast('/') },
                bitrate = effectiveBitrate,
                sampleRate = sampleRate,
                channels = channels,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoFrameRate = frameRate,
                fileSizeBytes = fileSize,
                containerFormat = extension.uppercase()
            )
        } catch (e: Throwable) {
            MediaInfo(
                uri = uri,
                fileName = fileName,
                mediaType = mediaType,
                durationMs = 0L,
                codec = extension.uppercase(),
                bitrate = 0L,
                sampleRate = 44100,
                channels = 2
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
                    if (sr != null) return sr.toInt()
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
            if (hasVideo == "yes") MediaType.VIDEO else MediaType.AUDIO
        } catch (_: Exception) {
            MediaType.AUDIO
        } finally {
            retriever.release()
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    fun getRealPath(context: Context, uri: Uri): String? {
        return try {
            // Create a temp copy for FFmpeg (content:// URIs need a file path)
            val ext = getFileName(context, uri)?.substringAfterLast('.') ?: "tmp"
            val tempFile = java.io.File(context.cacheDir, "probe_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }
}
