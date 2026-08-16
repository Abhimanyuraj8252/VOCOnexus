package com.voconexus.app.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.voconexus.app.core.parser.DocumentParserFactory
import com.voconexus.app.core.parser.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileImportManager(private val context: Context) {

    suspend fun importFileFromUri(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = getFileName(uri) ?: "imported_script.txt"
            val mimeType = contentResolver.getType(uri)

            val parser = DocumentParserFactory.getParser(fileName, mimeType)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                parser.parse(inputStream, fileName)
            } ?: throw IllegalStateException("Unable to open input stream for selected file URI: $uri")
        } catch (e: SecurityException) {
            throw SecurityException("Permission denied to read the selected file. Please grant storage permissions.", e)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return name
    }
}
