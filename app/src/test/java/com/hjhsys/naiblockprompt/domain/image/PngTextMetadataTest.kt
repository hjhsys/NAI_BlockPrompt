package com.hjhsys.naiblockprompt.domain.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

class PngTextMetadataTest {
    @Test fun `NovelAI text chunks survive target pixel replacement`() {
        val plain = InpaintMask.black(2, 2).toPngBytes()
        val source = insertBeforeIend(plain, chunk("tEXt", "Comment\u0000{\"seed\":42}".toByteArray()))

        val transferred = PngTextMetadata.transfer(source, plain)

        assertTrue(transferred.toString(Charsets.ISO_8859_1).contains("Comment"))
        assertTrue(transferred.toString(Charsets.ISO_8859_1).contains("seed"))
        assertFalse(plain.toString(Charsets.ISO_8859_1).contains("Comment"))
    }

    private fun insertBeforeIend(png: ByteArray, chunk: ByteArray): ByteArray =
        png.copyOfRange(0, png.size - 12) + chunk + png.copyOfRange(png.size - 12, png.size)

    private fun chunk(type: String, data: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { stream ->
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            stream.writeInt(data.size)
            stream.write(typeBytes)
            stream.write(data)
            stream.writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
        }
    }.toByteArray()
}
