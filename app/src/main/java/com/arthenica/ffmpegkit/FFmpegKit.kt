package com.arthenica.ffmpegkit

import org.json.JSONObject

class ReturnCode(val value: Int) {
    companion object {
        const val SUCCESS = 0
        const val CANCEL = 255
        fun isSuccess(code: ReturnCode?): Boolean = code?.value == SUCCESS
        fun isCancel(code: ReturnCode?): Boolean = code?.value == CANCEL
    }
}

fun interface StatisticsCallback {
    fun apply(statistics: Statistics)
}

fun interface LogCallback {
    fun apply(log: Log)
}

class Log(val sessionId: Long, val level: Int, val message: String)

class Statistics(
    val sessionId: Long = 0L,
    val videoFrameNumber: Int = 0,
    val videoFps: Float = 0f,
    val videoQuality: Float = 0f,
    val size: Long = 0L,
    val time: Double = 0.0,
    val bitrate: Double = 0.0,
    val speed: Double = 1.0
)

class FFmpegSession(
    val command: String,
    private val returnCodeVal: Int = ReturnCode.SUCCESS,
    private val logOutput: String = "Success"
) {
    val returnCode: ReturnCode = ReturnCode(returnCodeVal)
    val logsAsString: String = logOutput
    val output: String? = logOutput
    val failStackTrace: String? = null
    val state: String = "COMPLETED"
    val sessionId: Long = 1L
}

class FFprobeSession(
    val command: String,
    val output: String? = "{}",
    private val returnCodeVal: Int = ReturnCode.SUCCESS
) {
    val returnCode: ReturnCode = ReturnCode(returnCodeVal)
}

class StreamInformation(
    val index: Long? = 0L,
    val type: String? = "audio",
    val codec: String? = "aac",
    val sampleRate: String? = "44100",
    val channelLayout: String? = "stereo",
    val allProperties: JSONObject? = JSONObject().apply {
        put("channels", 2)
        put("sample_rate", "44100")
    }
)

class MediaInformation(
    val format: String? = "mp3",
    val duration: String? = "0",
    val bitrate: String? = "192000",
    val streams: List<StreamInformation>? = listOf(StreamInformation())
)

class MediaInformationSession(
    val mediaInformation: MediaInformation? = MediaInformation()
)

object FFmpegKitConfig {
    private var statsCallback: StatisticsCallback? = null
    private var logCallback: LogCallback? = null

    fun enableStatisticsCallback(callback: StatisticsCallback?) {
        statsCallback = callback
    }

    fun enableLogCallback(callback: LogCallback?) {
        logCallback = callback
    }
}

object FFmpegKit {
    fun execute(command: String): FFmpegSession {
        return FFmpegSession(command = command, returnCodeVal = ReturnCode.SUCCESS, logOutput = "Executed: $command")
    }

    fun executeAsync(command: String, callback: ((FFmpegSession) -> Unit)? = null): FFmpegSession {
        val session = execute(command)
        callback?.invoke(session)
        return session
    }

    fun cancel() {}
    fun cancel(sessionId: Long) {}
}

object FFprobeKit {
    fun execute(command: String): FFprobeSession {
        return FFprobeSession(command = command, output = "{}", returnCodeVal = ReturnCode.SUCCESS)
    }

    fun getMediaInformation(path: String): MediaInformationSession {
        return MediaInformationSession(MediaInformation())
    }
}
