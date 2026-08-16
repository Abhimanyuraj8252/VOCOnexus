package com.voconexus.app.core.parser

object DocumentParserFactory {

    fun getParser(fileName: String?, mimeType: String? = null): DocumentParser {
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        return when {
            extension == "srt" || mimeType == "application/x-subrip" -> SrtParser()
            else -> TxtParser()
        }
    }
}
