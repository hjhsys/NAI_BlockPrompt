package com.hjhsys.naiblockprompt.domain.image

import org.junit.Assert.*
import org.junit.Test

class ImageDisplayMappingTest {
    @Test fun `same aspect ratio fills display and maps center`() {
        val mapping = ImageDisplayMapping.fit(800, 400, 400f, 200f)
        assertEquals(DisplayRect(0f, 0f, 400f, 200f), mapping.display)
        assertEquals(SourcePixel(399, 199), mapping.toSource(200f, 100f))
    }

    @Test fun `letterbox bounds and center map correctly`() {
        val mapping = ImageDisplayMapping.fit(400, 400, 800f, 400f)
        assertEquals(DisplayRect(200f, 0f, 400f, 400f), mapping.display)
        assertNull(mapping.toSource(199f, 200f))
        assertNull(mapping.toSource(601f, 200f))
        assertEquals(SourcePixel(199, 199), mapping.toSource(400f, 200f))
    }

    @Test fun `corners map inside source and outside touches are ignored`() {
        val mapping = ImageDisplayMapping.fit(832, 1216, 500f, 500f)
        val rect = mapping.display
        assertEquals(SourcePixel(0, 0), mapping.toSource(rect.left, rect.top))
        assertEquals(SourcePixel(831, 1215), mapping.toSource(rect.left + rect.width, rect.top + rect.height))
        assertNull(mapping.toSource(rect.left, rect.top - .1f))
    }
}
