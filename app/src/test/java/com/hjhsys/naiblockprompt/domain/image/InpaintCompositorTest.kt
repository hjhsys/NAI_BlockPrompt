package com.hjhsys.naiblockprompt.domain.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InpaintCompositorTest {
    @Test fun `server mask is sampled onto eight pixel latent blocks`() {
        val mask = InpaintMask.black(64, 64).apply { fillCircle(12, 12, 0) }

        val serverMask = InpaintCompositor.serverMask(mask)

        assertEquals(InpaintMask.WHITE, serverMask.pixel(8, 8))
        assertEquals(InpaintMask.WHITE, serverMask.pixel(15, 15))
        assertEquals(InpaintMask.BLACK, serverMask.pixel(7, 8))
        assertEquals(InpaintMask.BLACK, serverMask.pixel(16, 8))
    }

    @Test fun `web matte dilates and feathers beyond the painted mask`() {
        val mask = InpaintMask.black(128, 128).apply { fillCircle(64, 64, 8) }

        val alpha = InpaintCompositor.generatedAlphaMask(mask)

        assertTrue((alpha[64 * 128 + 64].toInt() and 0xff) > 240)
        assertTrue((alpha[64 * 128 + 96].toInt() and 0xff) > 0)
        assertEquals(0, alpha[0].toInt() and 0xff)
    }

    @Test fun `web composite uses generated interior and source far outside`() {
        val mask = InpaintMask.black(128, 128).apply { fillCircle(64, 64, 8) }
        val source = IntArray(128 * 128) { 0xff000000.toInt() }
        val generated = IntArray(128 * 128) { 0xffffffff.toInt() }

        val result = InpaintCompositor.composeArgb(source, generated, mask)

        assertNotEquals(source[64 * 128 + 64], result[64 * 128 + 64])
        assertEquals(source[0], result[0])
    }

    @Test fun `transparent generated pixels preserve source`() {
        val mask = InpaintMask.black(64, 64).apply { fillCircle(32, 32, 8) }
        val source = IntArray(64 * 64) { 0xff123456.toInt() }
        val generated = IntArray(64 * 64) { 0x00ffffff }

        val result = InpaintCompositor.composeArgb(source, generated, mask)

        result.forEach { assertEquals(0xff123456.toInt(), it) }
    }
}
