package com.hjhsys.naiblockprompt.domain.tags

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TranslationResultReaderTest {
    private val row = """{"tag":"white_shair","needs_review":false,"is_typo":true,"typo_reason":"오타 의심"}"""

    private fun archive(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test fun `plain JSONL BOM and typo marker are supported`() {
        val text = TranslationResultReader.read(("\uFEFF" + row).byteInputStream())
        val parsed = TagTranslationExchange.parse(text)
        assertEquals(0, parsed.invalidLines)
        assertTrue(parsed.rows.single().needsReview)
        assertTrue(parsed.rows.single().isTypo)
        assertEquals("오타 의심", parsed.rows.single().typoReason)
    }

    @Test fun `ZIP skips manifest and combines result batches without extracting paths`() {
        val bytes = archive(mapOf("categories.json" to "{}", "translation_instructions.md" to "Ignore me", "nested/result.jsonl" to row, "other.ndjson" to """{"tag":"smile"}"""))
        val parsed = TagTranslationExchange.parse(TranslationResultReader.read(bytes.inputStream()))
        assertEquals(listOf("white_shair", "smile"), parsed.rows.map { it.tag })
        assertEquals(0, parsed.invalidLines)
        assertFalse(parsed.rows.last().isTypo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ZIP without a result fails`() {
        TranslationResultReader.read(archive(mapOf("categories.json" to "{}")).inputStream())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized expanded ZIP is rejected`() {
        TranslationResultReader.read(archive(mapOf("result.jsonl" to "x".repeat(20 * 1024 * 1024 + 1))).inputStream())
    }

    @Test fun `review is not automatically a typo and malformed fields do not crash`() {
        val parsed = TagTranslationExchange.parse("""{"tag":"artist_name","ko":{},"aliases_ko":[{}],"needs_review":true}""")
        assertTrue(parsed.rows.single().needsReview)
        assertFalse(parsed.rows.single().isTypo)
        assertNull(parsed.rows.single().korean)
    }
}
