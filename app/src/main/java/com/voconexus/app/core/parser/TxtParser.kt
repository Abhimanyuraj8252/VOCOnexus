package com.voconexus.app.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TxtParser : DocumentParser {

    override suspend fun parse(inputStream: InputStream, fileName: String?): ParseResult = withContext(Dispatchers.IO) {
        val bufferedInput = BufferedInputStream(inputStream)
        bufferedInput.mark(4)

        val bom = ByteArray(4)
        val n = bufferedInput.read(bom, 0, 4)
        bufferedInput.reset()

        val charset: Charset = when {
            n >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> {
                bufferedInput.skip(3)
                StandardCharsets.UTF_8
            }
            n >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> {
                bufferedInput.skip(2)
                StandardCharsets.UTF_16BE
            }
            n >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> {
                bufferedInput.skip(2)
                StandardCharsets.UTF_16LE
            }
            else -> StandardCharsets.UTF_8
        }

        val stringBuilder = StringBuilder()
        val warnings = mutableListOf<ImportWarning>()
        var lineCount = 0

        BufferedReader(InputStreamReader(bufferedInput, charset)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                lineCount++
                stringBuilder.append(line).append("\n")
                line = reader.readLine()
            }
        }

        val text = stringBuilder.toString().trim()
        require(text.isNotBlank()) { "Text file is empty or contains no readable content" }

        ParseResult(
            extractedText = text,
            sourceType = "TXT",
            originalFileName = fileName,
            warnings = warnings,
            blocksParsed = lineCount,
            blocksFailed = 0
        )
    }
}
