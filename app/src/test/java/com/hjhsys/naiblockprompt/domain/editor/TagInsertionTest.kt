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
}
