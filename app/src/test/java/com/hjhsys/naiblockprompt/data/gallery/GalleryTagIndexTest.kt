package com.hjhsys.naiblockprompt.data.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryTagIndexTest {
    @Test fun `weighted tags are indexed by their canonical text`() {
        val index = GalleryTagIndex.build("girl, 1.2::long_hair::, blue eyes")

        assertTrue(index.contains(GalleryTagIndex.queryToken("long hair")))
        assertTrue(index.contains(GalleryTagIndex.queryToken("blue_eyes")))
        assertFalse(index.contains(GalleryTagIndex.queryToken("hair")))
        assertTrue(index.contains(GalleryTagIndex.partialQueryToken("hair")))
        assertTrue(index.contains(GalleryTagIndex.partialQueryToken("long")))
    }

    @Test fun `all prompt sections participate in the index`() {
        val index = GalleryTagIndex.build("solo", "lowres", "red dress", "bad hands")

        assertTrue(index.contains(GalleryTagIndex.queryToken("bad hands")))
    }
}
