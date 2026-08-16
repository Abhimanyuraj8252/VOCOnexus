package com.voconexus.app.core.tools.trimmermerger

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class MediaItemProbeResult(
    val file: File,
    val isVideo: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String,
    val audioCodec: String,
    val frameRate: Float,
    val videoBitrate: Long,
    val audioBitrate: Long,
    val channelCount: Int,
    val sampleRate: Int,
    val fileSize: Long,
    val formatName: String
)

class TrimmerMergerProbe(private val context: Context) {

    suspend fun probeMediaUri(uri: Uri): MediaItemProbeResult = withContext(Dispatchers.IO) {
        val tempFile = copyUriToTempFile(uri)
        probeMediaFile(tempFile)
    }

    suspend fun probeMediaFile(file: File): MediaItemProbeResult = withContext(Dispatchers.IO) {
        val mediaPath = file.absolutePath
        val command = "-v error -show_entries format=duration,format_name,size,bit_rate:stream=codec_type,codec_name,width,height,r_frame_rate,bit_rate,channels,sample_rate -of json \"$mediaPath\""

        val session = FFprobeKit.execute(command)
        val output = session.output ?: ""

        var isVideo = false
        var durationMs = 0L
        var width = 0
        var height = 0
        var videoCodec = "none"
        var audioCodec = "none"
        var frameRate = 30f
        var videoBitrate = 0L
        var audioBitrate = 0L
        var channelCount = 2
        var sampleRate = 44100
        var formatName = file.extension.lowercase()

        try {
            val json = JSONObject(output)
            
            if (json.has("format")) {
                val formatObj = json.getJSONObject("format")
                if (formatObj.has("duration")) {
                    val durSec = formatObj.getString("duration").toDoubleOrNull() ?: 0.0
                    durationMs = (durSec * 1000).toLong()
                }
                if (formatObj.has("format_name")) {
                    formatName = formatObj.getString("format_name")
                }
            }

            if (json.has("streams")) {
                val streams = json.getJSONArray("streams")
                for (i in 0 until streams.length()) {
                    val stream = streams.getJSONObject(i)
                    val codecType = stream.optString("codec_type")

                    if (codecType == "video" && !isVideo) {
                        isVideo = true
                        width = stream.optInt("width", 0)
                        height = stream.optInt("height", 0)
                        videoCodec = stream.optString("codec_name", "h264")
                        videoBitrate = stream.optLong("bit_rate", 0L)

                        val rFrameRate = stream.optString("r_frame_rate", "30/1")
                        if (rFrameRate.contains("/")) {
                            val parts = rFrameRate.split("/")
                            val num = parts[0].toFloatOrNull() ?: 30f
                            val den = parts[1].toFloatOrNull() ?: 1f
                            if (den > 0) frameRate = num / den
                        }
                    } else if (codecType == "audio" && audioCodec == "none") {
                        audioCodec = stream.optString("codec_name", "aac")
                        audioBitrate = stream.optLong("bit_rate", 0L)
                        channelCount = stream.optInt("channels", 2)
                        sampleRate = stream.optInt("sample_rate", 44100)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        MediaItemProbeResult(
            file = file,
            isVideo = isVideo,
            durationMs = durationMs,
            width = width,
            height = height,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            frameRate = frameRate,
            videoBitrate = videoBitrate,
            audioBitrate = audioBitrate,
            channelCount = channelCount,
            sampleRate = sampleRate,
            fileSize = file.length(),
            formatName = formatName
        )
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")
        val fileExtension = getExtensionFromUri(uri)
        val tempFile = File.createTempFile("tm_probe_", ".$fileExtension", context.cacheDir)

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun getExtensionFromUri(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("video/mp4") == true -> "mp4"
            mimeType?.contains("video/x-matroska") == true -> "mkv"
            mimeType?.contains("video/avi") == true -> "avi"
            mimeType?.contains("video/quicktime") == true -> "mov"
            mimeType?.contains("video/webm") == true -> "webm"
            mimeType?.contains("audio/mpeg") == true -> "mp3"
            mimeType?.contains("audio/aac") == true -> "aac"
            mimeType?.contains("audio/wav") == true -> "wav"
            mimeType?.contains("audio/flac") == true -> "flac"
            else -> "mp4"
        }
    }
}
