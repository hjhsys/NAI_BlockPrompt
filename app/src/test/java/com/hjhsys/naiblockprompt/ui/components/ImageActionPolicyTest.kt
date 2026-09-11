package com.hjhsys.naiblockprompt.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageActionPolicyTest {
    @Test
    fun `missing original disables every image action`() {
        val result = ImageActionPolicy.availability(false, "nai-diffusion-5-full")

        assertFalse(result.imageToImage)
        assertFalse(result.vibeTransfer)
        assertFalse(result.preciseReference)
        assertFalse(result.inpaint)
    }

    @Test
    fun `V4_5 full exposes all image actions`() {
        val result = ImageActionPolicy.availability(true, "nai-diffusion-4-5-full")

        assertTrue(result.imageToImage)
        assertTrue(result.vibeTransfer)
        assertTrue(result.preciseReference)
        assertTrue(result.inpaint)
    }

    @Test
    fun `V5 keeps general actions but follows model restrictions`() {
        val result = ImageActionPolicy.availability(true, "nai-diffusion-5-full")

        assertTrue(result.imageToImage)
        assertTrue(result.vibeTransfer)
        assertFalse(result.preciseReference)
        assertTrue(result.inpaint)
    }
}
