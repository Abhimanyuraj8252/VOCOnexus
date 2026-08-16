package com.voconexus.app

import com.voconexus.app.core.parser.TxtParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class TxtParserTest {

    private val parser = TxtParser()

    @Test
    fun testParseStandardUtf8Text() = runBlocking {
        val sampleText = "Hello World.\nThis is line 2.\nयह एक हिंदी वाक्य है।"
        val inputStream = ByteArrayInputStream(sampleText.toByteArray(StandardCharsets.UTF_8))

        val result = parser.parse(inputStream, "sample.txt")
        assertEquals("TXT", result.sourceType)
        assertEquals("sample.txt", result.originalFileName)
        assertEquals(sampleText, result.extractedText)
        assertEquals(0, result.warnings.size)
    }

    @Test
    fun testParseUtf8WithBom() = runBlocking {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val textBytes = "BOM Header Text".toByteArray(StandardCharsets.UTF_8)
        val combined = bom + textBytes

        val result = parser.parse(ByteArrayInputStream(combined), "bom.txt")
        assertEquals("BOM Header Text", result.extractedText)
    }

    @Test
    fun testParseUtf16Le() = runBlocking {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val textBytes = "UTF-16 LE Text".toByteArray(StandardCharsets.UTF_16LE)
        val combined = bom + textBytes

        val result = parser.parse(ByteArrayInputStream(combined), "utf16.txt")
        assertEquals("UTF-16 LE Text", result.extractedText)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyFileThrowsException() = runBlocking {
        parser.parse(ByteArrayInputStream(byteArrayOf()), "empty.txt")
        Unit
    }
}
