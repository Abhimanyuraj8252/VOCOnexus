package com.voconexus.app.core.util

import com.voconexus.app.core.domain.DurationEstimator
import com.voconexus.app.core.domain.StandardDurationEstimator
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class TextStatistics(
    val characterCount: Int = 0,
    val wordCount: Int = 0,
    val paragraphCount: Int = 0,
    val lineCount: Int = 0,
    val estimatedDurationMs: Long = 0L
)

object Formatters {

    private val unicodeWordRegex = "[\\p{L}\\p{N}]+".toRegex()

    fun formatDurationMs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00:00"

        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return "%.1f %s".format(Locale.US, bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    fun formatNumber(number: Long): String {
        return NumberFormat.getNumberInstance(Locale.US).format(number)
    }

    fun countUnicodeWords(text: String): Int {
        if (text.isBlank()) return 0
        return unicodeWordRegex.findAll(text).count()
    }

    fun calculateTextStatistics(
        text: String,
        durationEstimator: DurationEstimator = StandardDurationEstimator()
    ): TextStatistics {
        if (text.isBlank()) return TextStatistics()

        // Character count excluding carriage returns
        val cleanText = text.replace("\r", "")
        val charCount = cleanText.length
        val wordCount = countUnicodeWords(cleanText)

        val paragraphs = cleanText.split("\n\n").filter { it.trim().isNotEmpty() }
        val paragraphCount = paragraphs.size.coerceAtLeast(1)
        val lineCount = cleanText.split("\n").size

        val durationMs = durationEstimator.estimateDurationMs(cleanText, wordCount)

        return TextStatistics(
            characterCount = charCount,
            wordCount = wordCount,
            paragraphCount = paragraphCount,
            lineCount = lineCount,
            estimatedDurationMs = durationMs
        )
    }
}
