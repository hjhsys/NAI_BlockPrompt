package com.hjhsys.naiblockprompt.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TagInsertionTest {
    @Test
    fun `inserts selected tags at cursor with prompt separators`() {
        assertEquals(
            "girl, blue eyes, long hair, smile",
            TagInsertion.insert("girl, smile", 4, listOf("blue_eyes", "long_hair")),
        )
    }

    @Test
    fun `inserts into an empty prompt without extra separators`() {
        assertEquals("blue eyes, smile", TagInsertion.insert("", 0, listOf("blue_eyes", "smile")))
    }

    @Test
    fun `clamps a stale cursor to the current prompt length`() {
        assertEquals("girl, smile", TagInsertion.insert("girl", 100, listOf("smile")))
    }

    @Test
    fun `cursor in the middle of a word preserves the whole word`() {
        assertEquals("shirt, smile", TagInsertion.insert("shirt", 3, listOf("smile")))
    }

    @Test
    fun `cursor at token end appends after the token`() {
        assertEquals("shirt, smile", TagInsertion.insert("shirt", 5, listOf("smile")))
    }

    @Test
    fun `cursor at token start appends after the token`() {
        assertEquals("shirt, smile", TagInsertion.insert("shirt", 0, listOf("smile")))
    }

    @Test
    fun `cursor after comma preserves the following token`() {
        val content = "shirt, long hair"
        assertEquals("shirt, long hair, smile", TagInsertion.insert(content, 7, listOf("smile")))
    }

    @Test
    fun `prompt first and last positions preserve existing tokens`() {
        val content = "girl, solo"
        assertEquals("girl, smile, solo", TagInsertion.insert(content, 0, listOf("smile")))
        assertEquals("girl, solo, smile", TagInsertion.insert(content, content.length, listOf("smile")))
    }

    @Test
    fun `multiple canonical tags keep selection order`() {
        assertEquals(
            "shirt, smile, blue eyes, long hair, solo",
            TagInsertion.insert(
                content = "shirt",
                cursor = 3,
                tags = listOf("smile", "blue_eyes", "long_hair", "solo"),
            ),
        )
    }

    @Test
    fun `insertion after trailing comma has normalized spacing`() {
        assertEquals("shirt, smile", TagInsertion.insert("shirt,", 6, listOf("smile")))
    }
}
