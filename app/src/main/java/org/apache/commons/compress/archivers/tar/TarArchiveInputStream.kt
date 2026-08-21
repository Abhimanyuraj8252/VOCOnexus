package org.apache.commons.compress.archivers.tar

import java.io.InputStream

class TarArchiveEntry(
    val name: String,
    val isDirectory: Boolean
)

class TarArchiveInputStream(private val inputStream: InputStream) : InputStream() {
    val nextEntry: TarArchiveEntry?
        get() = null

    override fun read(): Int {
        return inputStream.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return inputStream.read(b, off, len)
    }

    override fun close() {
        inputStream.close()
    }
}
