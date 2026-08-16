package com.voconexus.app.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class SrtParser : DocumentParser {

    private val timestampRegex = "^\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}.*".toRegex()
    private val numberOnlyRegex = "^\\d+$".toRegex()
    private val htmlTagRegex = "</?[a-zA-Z][^>]*>".toRegex()

    override suspend fun parse(inputStream: InputStream, fileName: String?): ParseResult = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                lines.add(line)
                line = reader.readLine()
            }
        }

        val warnings = mutableListOf<ImportWarning>()
        val dialogueParagraphs = mutableListOf<String>()
        var currentDialogueLines = mutableListOf<String>()
        var blocksParsed = 0
        var blocksFailed = 0

        var idx = 0
        val totalLines = lines.size

        while (idx < totalLines) {
            val line = lines[idx].trim()

            if (line.isEmpty()) {
                if (currentDialogueLines.isNotEmpty()) {
                    val paragraphText = cleanSubtitleLines(currentDialogueLines)
                    if (paragraphText.isNotBlank()) {
                        dialogueParagraphs.add(paragraphText)
                        blocksParsed++
                    }
                    currentDialogueLines = mutableListOf()
                }
                idx++
                continue
            }

            // Check if line is a subtitle block index (followed by timestamp)
            if (numberOnlyRegex.matches(line)) {
                val nextIdx = idx + 1
                if (nextIdx < totalLines && timestampRegex.matches(lines[nextIdx].trim())) {
                    // Confirmed block index -> skip index and timestamp line
                    idx += 2
                    continue
                }
            }

            // Check if line is timestamp alone (in case index was missing)
            if (timestampRegex.matches(line)) {
                idx++
                continue
            }

            // Spoken dialogue line
            currentDialogueLines.add(line)
            idx++
        }

        // Process remaining block if no trailing blank line
        if (currentDialogueLines.isNotEmpty()) {
            val paragraphText = cleanSubtitleLines(currentDialogueLines)
            if (paragraphText.isNotBlank()) {
                dialogueParagraphs.add(paragraphText)
                blocksParsed++
            }
        }

        val extractedText = dialogueParagraphs.joinToString("\n\n").trim()
        if (extractedText.isBlank()) {
            warnings.add(ImportWarning(0, "No readable spoken dialogue found in SRT file"))
            blocksFailed++
        }

        ParseResult(
            extractedText = extractedText,
            sourceType = "SRT",
            originalFileName = fileName,
            warnings = warnings,
            blocksParsed = blocksParsed,
            blocksFailed = blocksFailed
        )
    }

    private fun cleanSubtitleLines(lines: List<String>): String {
        return lines.joinToString(" ") { line ->
            // Strip HTML/formatting tags like <i>, <b>, <font>
            line.replace(htmlTagRegex, "").trim()
        }.trim()
    }
}
