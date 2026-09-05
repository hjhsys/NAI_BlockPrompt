package com.hjhsys.naiblockprompt.data.tags

import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledTagTranslationAssetTest {
    @Test
    fun `bundled Korean translation asset is complete and valid`() {
        val asset = File("src/main/assets/${BundledTagImporter.TRANSLATION_ASSET}")
        val tags = HashSet<String>(201_273)
        var rows = 0
        var emptyKorean = 0
        var needsReview = 0
        var suggestedCategories = 0
        val allowedCategories = setOf(
            "", "general", "artist", "copyright", "character", "meta",
            "clothes", "pose", "hair", "body", "expression", "accessory",
            "background", "composition", "lighting", "effect", "other",
        )

        GZIPInputStream(asset.inputStream()).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val row = Json.parseToJsonElement(line).jsonObject
                val tag = row.getValue("tag").jsonPrimitive.content
                val korean = row.getValue("ko").jsonPrimitive.content
                val category = row.getValue("app_category").jsonPrimitive.content
                rows++
                assertTrue("duplicate tag: $tag", tags.add(tag))
                if (korean.isBlank()) emptyKorean++
                assertTrue("unsupported app_category: $category", category in allowedCategories)
                if (row.getValue("needs_review").jsonPrimitive.boolean) needsReview++
                if (row.getValue("suggested_category").jsonPrimitive.content.isNotBlank()) suggestedCategories++
            }
        }

        assertEquals(201_273, rows)
        assertEquals(rows, tags.size)
        assertEquals(0, emptyKorean)
        assertEquals(27_783, needsReview)
        assertEquals(6_360, suggestedCategories)
    }
}
