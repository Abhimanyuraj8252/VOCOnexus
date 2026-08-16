package com.voconexus.app.core.tools.audioextractor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformationSession
import com.voconexus.app.core.tools.speedpitch.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioExtractorInfo(
    val uri: Uri,
    val fileName: String,
    val mediaType: MediaType,
    val durationMs: Long,
    val audioCodec: String,
    val bitrateBps: Long,
    val sampleRate: Int,
    val channels: Int,
    val fileSizeBytes: Long = 0L,
    val containerFormat: String = ""
)

object AudioExtractorProbe {

    private val audioExtensions = setOf(
        "mp3", "aac", "m4a", "ogg", "opus", "flac", "wav", "aiff", "aif",
        "wma", "amr", "awb", "ape", "ac3", "mp2", "ra", "3ga", "caf", "mka"
    )

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "mts",
        "m4v", "wmv", "mpg", "mpeg", "vob", "asf", "m2ts", "ogv", "divx"
    )

    suspend fun probe(context: Context, uri: Uri): AudioExtractorInfo = withContext(Dispatchers.IO) {
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

            val effectiveBitrate = if (rawBitrate > 0) rawBitrate else {
                if (fileSize > 0 && durationMs > 0) {
                    (fileSize * 8L * 1000L) / durationMs
                } else 0L
            }

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val codecHint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                retriever.extractMetadata(38) ?: ""
            } else {
                mimeType.substringAfterLast('/')
            }

            val sampleRate = probeSampleRate(context, uri)
            val channels = probeChannels(context, uri)

            AudioExtractorInfo(
                uri = uri,
                fileName = fileName,
                mediaType = mediaType,
                durationMs = durationMs,
                audioCodec = codecHint.ifBlank { extension }.ifBlank { "aac" },
                bitrateBps = effectiveBitrate,
                sampleRate = sampleRate,
                channels = channels,
                fileSizeBytes = fileSize,
                containerFormat = extension.uppercase()
            )
        } catch (e: Throwable) {
            AudioExtractorInfo(
                uri = uri,
                fileName = fileName,
                mediaType = mediaType,
                durationMs = 0L,
                audioCodec = extension.ifBlank { "aac" },
                bitrateBps = 0L,
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
            val file = java.io.File(context.cacheDir, "probe_temp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
