package com.hjhsys.naiblockprompt.domain.tags

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class TagTranslationExchangeTest {
    @Test
    fun `missing plus selected unions five candidates and preserves translated selection fields`() {
        val missing = (1..3).map { index ->
            TagTranslationCandidate("missing_$index", "general", index.toLong(), null, null)
        }
        val selected = listOf(
            TagTranslationCandidate("translated_1", "character", 10, "번역 1", "character", "별칭 1"),
            TagTranslationCandidate("translated_2", "copyright", 20, "번역 2", "copyright", "별칭 2"),
        )

        val resolved = AiTranslationExportSelection.resolve(
            AiTranslationExportScope.MISSING_AND_SELECTED,
            missing,
            selected,
        )

        assertEquals(5, resolved.size)
        assertEquals("번역 1", resolved.single { it.tag == "translated_1" }.korean)
        assertEquals("별칭 1", resolved.single { it.tag == "translated_1" }.koreanAliases)
    }

    @Test
    fun `export scopes use effective translation and dedupe canonical tags`() {
        val untranslated = TagTranslationCandidate("needs_ko", "general", 1, null, null)
        val overlapping = TagTranslationCandidate("overlap", "general", 2, "", null)
        val effectivelyTranslated = TagTranslationCandidate("base_translated", "general", 3, "기본 번역", "general", "기존 별칭")
        val selectedTranslated = TagTranslationCandidate("selected_translated", "character", 4, "선택 번역", "character", "선택 별칭")

        val missingOnly = AiTranslationExportSelection.resolve(
            AiTranslationExportScope.MISSING_ONLY,
            listOf(untranslated, overlapping, effectivelyTranslated),
            listOf(selectedTranslated),
        )
        val selectedOnly = AiTranslationExportSelection.resolve(
            AiTranslationExportScope.SELECTED_ONLY,
            listOf(untranslated),
            listOf(overlapping, selectedTranslated),
        )
        val combined = AiTranslationExportSelection.resolve(
            AiTranslationExportScope.MISSING_AND_SELECTED,
            listOf(untranslated, overlapping, effectivelyTranslated),
            listOf(overlapping, selectedTranslated),
        )

        assertEquals(listOf("needs_ko", "overlap"), missingOnly.map { it.tag })
        assertEquals(listOf("overlap", "selected_translated"), selectedOnly.map { it.tag })
        assertEquals(listOf("needs_ko", "overlap", "selected_translated"), combined.map { it.tag })
        assertEquals(1, combined.count { it.tag == "overlap" })
        assertFalse(combined.any { it.tag == "base_translated" })
    }

    @Test
    fun `clipboard export contains only selected rows and preserves existing fields`() {
        val text = TagTranslationExchange.exportClipboard(
            listOf(TagTranslationCandidate("alice_(series)", "character", 12, "앨리스", "character", "앨리스짱")),
            listOf("character", "copyright"),
        )

        assertTrue(text.contains("\"tag\":\"alice_(series)\""))
        assertTrue(text.contains("\"source_category\":\"character\""))
        assertTrue(text.contains("\"ko\":\"앨리스\""))
        assertTrue(text.contains("앨리스짱"))
        assertFalse(text.contains("unselected_tag"))
    }

    @Test
    fun `clipboard guidance distinguishes character and copyright proper names`() {
        val text = TagTranslationExchange.exportClipboard(emptyList(), listOf("character", "copyright"))
        assertTrue(text.contains("may be Character/Copyright proper names"))
        assertTrue(text.contains("official Korean localization"))
        assertTrue(text.contains("Namuwiki may be used as a cross-check"))
        assertTrue(text.contains("sources conflict"))
    }

    @Test
    fun `bundle guidance requires research and Korean proper name cross check`() {
        val bytes = TagTranslationExchange.exportBundle(emptyList(), listOf("character", "copyright"))
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        val instructions = entries.getValue("translation_instructions.md")
        assertTrue(instructions.contains("must not be classified or translated from spelling alone"))
        assertTrue(instructions.contains("official Korean localization"))
        assertTrue(instructions.contains("Namuwiki may be used to verify common Korean usage"))
        assertTrue(instructions.contains("return `review`"))
    }

    @Test
    fun `parse supports exclusion candidate and rejects incomplete exclusion`() {
        val parsed = TagTranslationExchange.parse(
            """
            {"tag":"white_shair","status":"excluded_candidate","reason_code":"typo","reason_text":"철자 오류로 보임","ko":"","aliases_ko":[],"app_category":""}
            {"tag":"bad","status":"excluded_candidate","reason_code":"guess"}
            """.trimIndent(),
        )

        assertEquals(1, parsed.rows.size)
        assertEquals(TagTranslationStatus.EXCLUDED_CANDIDATE, parsed.rows.single().status)
        assertEquals("typo", parsed.rows.single().exclusionReasonCode)
        assertEquals(1, parsed.invalidLines)
    }

    @Test
    fun `parse accepts partial result and deterministic first duplicate`() {
        val parsed = TagTranslationExchange.parse(
            """
            {"tag":"smile","status":"translated","ko":"미소","aliases_ko":[],"app_category":"expression"}
            {"tag":"smile","status":"translated","ko":"웃음","aliases_ko":[],"app_category":"expression"}
            """.trimIndent(),
        )

        assertEquals("미소", parsed.rows.distinctBy { it.tag }.single().korean)
    }
    @Test
    fun `bundle distinguishes numeric source categories and preserves source metadata`() {
        val bytes = TagTranslationExchange.exportBundle(
            listOf(TagTranslationCandidate("white_shair", "0", 0, null, null)),
            listOf("0", "1", "general", "hair"),
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        val manifest = entries.getValue("categories.json")
        assertFalse(manifest.contains("\"id\":\"0\""))
        assertTrue(manifest.contains("\"id\":\"artist\""))
        val row = entries.getValue("tags_to_process.jsonl")
        assertTrue(row.contains("\"source_category\":\"0\""))
        assertTrue(row.contains("\"post_count\":0"))
        assertTrue(row.contains("white_shair"))
        assertTrue(entries.getValue("translation_instructions.md").contains("leave them absent"))
    }

    @Test
    fun `export includes instructions categories and candidates`() {
        val text = TagTranslationExchange.export(
            listOf(TagTranslationCandidate("school_uniform", "general", 123L, null, null)),
            listOf("clothes", "pose"),
        )
        val lines = text.lineSequence().filter(String::isNotBlank).toList()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("existing_categories"))
        assertTrue(lines[1].contains("school_uniform"))
    }

    @Test
    fun `parse accepts array aliases and reports invalid lines`() {
        val parsed = TagTranslationExchange.parse(
            """
            {"type":"nai_block_prompt_translation_batch","version":1}
            {"tag":"school_uniform","ko":"교복","aliases_ko":["학교 교복"],"app_category":"clothes","needs_review":false}
            not-json
            """.trimIndent(),
        )
        assertEquals(1, parsed.rows.size)
        assertEquals("교복", parsed.rows.single().korean)
        assertEquals(listOf("학교 교복"), parsed.rows.single().aliases)
        assertEquals(1, parsed.invalidLines)
    }

    @Test
    fun `parse accepts comma separated aliases from tolerant AI output`() {
        val parsed = TagTranslationExchange.parse(
            """{"tag":"looking_back","ko":"뒤돌아보기","aliases_ko":"뒤를 봄, 뒤돌아 봄","suggested_category":"pose"}""",
        )
        assertEquals(listOf("뒤를 봄", "뒤돌아 봄"), parsed.rows.single().aliases)
        assertEquals("pose", parsed.rows.single().suggestedCategory)
    }

    @Test
    fun `parse ignores markdown fences often returned by AI`() {
        val parsed = TagTranslationExchange.parse("""```jsonl
            {"tag":"smile","ko":"미소","aliases_ko":[]}
            ```""".trimIndent())
        assertEquals(1, parsed.rows.size)
        assertEquals(0, parsed.invalidLines)
    }

    @Test
    fun `bundle contains instructions category manifest and jsonl`() {
        val bytes = TagTranslationExchange.exportBundle(
            listOf(TagTranslationCandidate("rim_light", "general", 10, null, null)),
            listOf("lighting"),
        )
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
            }
        }
        assertEquals(listOf("translation_instructions.md", "categories.json", "tags_to_process.jsonl"), names)
    }

    @Test
    fun `developer bundle splits candidates into one thousand row files`() {
        val candidates = (1..1_001).map { index ->
            TagTranslationCandidate("tag_$index", "general", index.toLong(), null, null)
        }
        val bytes = TagTranslationExchange.exportBundle(candidates, listOf("general"), splitBatches = true)
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertTrue("tags_to_process_0001.jsonl" in entries)
        assertTrue("tags_to_process_0002.jsonl" in entries)
        assertEquals(1_001, entries.filterKeys { it.startsWith("tags_to_process_") }
            .values.sumOf { content -> content.lineSequence().count(String::isNotBlank) })
    }
}
