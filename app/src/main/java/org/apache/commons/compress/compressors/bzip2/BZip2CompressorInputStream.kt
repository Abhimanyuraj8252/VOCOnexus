package org.apache.commons.compress.compressors.bzip2

import java.io.InputStream

class BZip2CompressorInputStream(private val inputStream: InputStream) : InputStream() {
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
