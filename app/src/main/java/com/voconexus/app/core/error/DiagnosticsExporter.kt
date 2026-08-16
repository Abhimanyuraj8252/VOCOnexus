package com.voconexus.app.core.error

import android.content.Context
import android.os.Build
import com.voconexus.app.core.storage.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsExporter(
    private val context: Context,
    private val storageManager: StorageManager
) {

    fun generateDiagnosticReport(recentErrors: List<VocoNexusError> = emptyList()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = dateFormat.format(Date())

        val breakdown = storageManager.getStorageBreakdown()
        val availableStorageMb = breakdown.freeSystemStorageBytes / (1024 * 1024)

        val report = StringBuilder()
        report.appendLine("==========================================")
        report.appendLine("          VIRTUAL DIAGNOSTIC REPORT        ")
        report.appendLine("==========================================")
        report.appendLine("Application: VocoNexus v1.0.0")
        report.appendLine("Generated At: $timestamp")
        report.appendLine("Android OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        report.appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
        report.appendLine("Available Memory: ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB")
        report.appendLine("Storage Available: ${availableStorageMb} MB free")
        report.appendLine("App Storage Used: ${breakdown.totalAppSizeBytes / (1024 * 1024)} MB")
        report.appendLine("Privacy Mode: On-Device (No cloud processing)")
        report.appendLine("------------------------------------------")
        report.appendLine("RECENT SYSTEM EVENTS / ERRORS:")

        if (recentErrors.isEmpty()) {
            report.appendLine("No active system errors logged.")
        } else {
            recentErrors.forEachIndexed { index, error ->
                report.appendLine("[${index + 1}] Code: ${error.errorCode} | ${dateFormat.format(Date(error.timestamp))}")
                report.appendLine("    Title: ${error.title}")
                report.appendLine("    Message: ${error.userMessage}")
                if (!error.technicalDetails.isNull_or_empty()) {
                    report.appendLine("    Details: ${error.technicalDetails}")
                }
            }
        }

        report.appendLine("==========================================")
        report.appendLine("END OF REPORT (Sanitized: No script content)")
        report.appendLine("==========================================")

        return report.toString()
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    fun exportReportToFile(reportContent: String): File {
        val diagDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val reportFile = File(diagDir, "voconexus_diag_${System.currentTimeMillis()}.txt")
        reportFile.writeText(reportContent)
        return reportFile
    }
}
