package com.hjhsys.naiblockprompt.ui.components

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerPolicyTest {
    @Test fun `contextual viewer expands to fullscreen and can return`() {
        val fullscreen = ImageViewerPolicy.toggle(ImageViewerMode.CONTEXTUAL)
        assertTrue(fullscreen == ImageViewerMode.FULLSCREEN)
        assertTrue(ImageViewerPolicy.toggle(fullscreen) == ImageViewerMode.CONTEXTUAL)
    }

    @Test fun `fullscreen uses edge to edge dialog properties`() {
        assertTrue(ImageViewerPolicy.usePlatformDefaultWidth(ImageViewerMode.CONTEXTUAL))
        assertTrue(ImageViewerPolicy.decorFitsSystemWindows(ImageViewerMode.CONTEXTUAL))
        assertFalse(ImageViewerPolicy.usePlatformDefaultWidth(ImageViewerMode.FULLSCREEN))
        assertFalse(ImageViewerPolicy.decorFitsSystemWindows(ImageViewerMode.FULLSCREEN))
    }

    @Test fun `zoom reaches original pixels and pan stays within scaled image`() {
        val portraitViewport = IntSize(1080, 1800)
        val largePortrait = IntSize(2160, 3600)
        val maxZoom = ImageViewerPolicy.maximumOriginalPixelZoom(portraitViewport, largePortrait)
        assertEquals(3f, maxZoom, 0.001f)
        val (maxX, maxY) = ImageViewerPolicy.maximumPan(portraitViewport, largePortrait, maxZoom)
        assertEquals(1080f, maxX, 0.001f)
        assertEquals(1800f, maxY, 0.001f)
        assertEquals(3f, ImageViewerPolicy.maximumOriginalPixelZoom(portraitViewport, IntSize(832, 1216)), 0.001f)
    }
}
