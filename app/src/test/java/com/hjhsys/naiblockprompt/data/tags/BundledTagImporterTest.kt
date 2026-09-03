package com.hjhsys.naiblockprompt.data.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class BundledTagImporterTest {
    @Test fun `parses quoted aliases without splitting the alias column`() {
        val row = BundledTagImporter.parseCsvLine("smile,0,123,\"happy_face,:},smiling\"")
        assertEquals(listOf("smile", "0", "123", "happy_face,:},smiling"), row)
    }

    @Test fun `maps Danbooru category numbers`() {
        assertEquals("general", BundledTagImporter.categoryName(0))
        assertEquals("artist", BundledTagImporter.categoryName(1))
        assertEquals("copyright", BundledTagImporter.categoryName(3))
        assertEquals("character", BundledTagImporter.categoryName(4))
        assertEquals("meta", BundledTagImporter.categoryName(5))
        assertEquals(null, BundledTagImporter.categoryName(2))
    }
}
