package com.voconexus.app.core.parser

import java.io.InputStream

data class ImportWarning(
    val lineNumber: Int,
    val message: String
)

data class ParseResult(
    val extractedText: String,
    val sourceType: String, // "TXT" or "SRT"
    val originalFileName: String? = null,
    val warnings: List<ImportWarning> = emptyList(),
    val blocksParsed: Int = 0,
    val blocksFailed: Int = 0
)

interface DocumentParser {
    suspend fun parse(inputStream: InputStream, fileName: String? = null): ParseResult
}
