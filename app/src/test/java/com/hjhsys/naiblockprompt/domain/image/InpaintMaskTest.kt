package com.hjhsys.naiblockprompt.domain.image

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class InpaintMaskTest {
    @Test fun `new mask is entirely black and empty`() {
        val mask = InpaintMask.black(7, 5)
        assertTrue(mask.isEmpty)
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            assertEquals(InpaintMask.BLACK, mask.pixel(x, y))
        }
    }

    @Test fun `painting white makes mask non-empty`() {
        val mask = InpaintMask.black(9, 9)
        mask.fillCircle(4, 4, 2)
        assertFalse(mask.isEmpty)
        assertEquals(InpaintMask.WHITE, mask.pixel(4, 4))
        assertEquals(InpaintMask.BLACK, mask.pixel(0, 0))
        assertTrue(mask.whitePixelCount > 0)
    }

    @Test fun `erasing painted area restores empty mask`() {
        val mask = InpaintMask.black(9, 9)
        mask.fillCircle(4, 4, 2)
        mask.fillCircle(4, 4, 2, regenerate = false)
        assertTrue(mask.isEmpty)
    }

    @Test fun `interpolated stroke has no gaps and copy is independent`() {
        val mask = InpaintMask.black(30, 5)
        mask.drawLine(1, 2, 28, 2, 0)
        for (x in 1..28) assertEquals(InpaintMask.WHITE, mask.pixel(x, 2))
        val copy = mask.copy()
        copy.clear()
        assertFalse(mask.isEmpty)
        assertTrue(copy.isEmpty)
    }

    @Test fun `mask copies into a reusable padded render buffer`() {
        val mask = InpaintMask.black(3, 2).apply { fillCircle(1, 0, 0) }
        val destination = ByteArray(8) { 99 }
        mask.copyPixelsTo(destination, rowStride = 4)
        assertArrayEquals(byteArrayOf(0, -1, 0, 99, 0, 0, 0, 99), destination)
    }

    @Test fun `source size mismatch is detected before request`() {
        val mask = InpaintMask.black(832, 1216)
        assertTrue(mask.matchesSource(832, 1216))
        assertFalse(mask.matchesSource(1216, 832))
        assertThrows(IllegalArgumentException::class.java) { mask.requireMatchesSource(1216, 832) }
    }

    @Test fun `PNG encoding is opaque RGBA and preserves dimensions and polarity`() {
        val mask = InpaintMask.black(321, 123).apply { fillCircle(10, 11, 3) }
        val png = mask.toPngBytes()
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.copyOfRange(0, 8))
        assertEquals(321, readInt(png, 16))
        assertEquals(123, readInt(png, 20))
        assertEquals(8, png[24].toInt())
        assertEquals(6, png[25].toInt())
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(321, decoded.width)
        assertEquals(123, decoded.height)
        assertEquals(0xff000000.toInt(), decoded.getRGB(0, 0))
        assertEquals(0xffffffff.toInt(), decoded.getRGB(10, 11))
        assertTrue(png.size > 50)
    }

    @Test fun `binary brush edge remains opaque black or white without implicit feathering`() {
        val mask = InpaintMask.black(17, 17).apply { fillCircle(8, 8, 4) }
        val decoded = ImageIO.read(ByteArrayInputStream(mask.toPngBytes()))

        var white = 0
        for (y in 0 until decoded.height) for (x in 0 until decoded.width) {
            val pixel = decoded.getRGB(x, y)
            assertEquals(255, pixel ushr 24 and 0xff)
            val gray = pixel and 0xff
            assertTrue(gray == InpaintMask.BLACK || gray == InpaintMask.WHITE)
            assertEquals(gray, pixel ushr 8 and 0xff)
            assertEquals(gray, pixel ushr 16 and 0xff)
            if (gray == InpaintMask.WHITE) white++
        }
        assertEquals(mask.whitePixelCount, white)
    }

    @Test fun `API mask preserves the binary editor boundary as opaque RGBA`() {
        val mask = InpaintMask.black(31, 31).apply { fillCircle(15, 15, 8) }
        val decoded = ImageIO.read(ByteArrayInputStream(mask.toApiPngBytes()))

        assertEquals(255, decoded.getRGB(15, 15) and 0xff)
        assertEquals(0, decoded.getRGB(0, 0) and 0xff)
        assertEquals(0, decoded.getRGB(27, 15) and 0xff)
        for (y in 0 until decoded.height) for (x in 0 until decoded.width) {
            val pixel = decoded.getRGB(x, y)
            assertEquals(255, pixel ushr 24 and 0xff)
            val gray = pixel and 0xff
            assertEquals(gray, pixel ushr 8 and 0xff)
            assertEquals(gray, pixel ushr 16 and 0xff)
            assertTrue(gray == InpaintMask.BLACK || gray == InpaintMask.WHITE)
        }
        assertEquals(InpaintMask.WHITE, mask.pixel(15, 15))
        assertEquals(InpaintMask.BLACK, mask.pixel(24, 15))
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
