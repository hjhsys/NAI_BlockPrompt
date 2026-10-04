package com.hjhsys.naiblockprompt.data.library

import com.hjhsys.naiblockprompt.data.local.entity.HistoryEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRetentionPolicyTest {
    @Test fun `record list contains the newest entries regardless of favorite state`() {
        val entries = listOf(entry("favorite", 4, true), entry("new", 3), entry("middle", 2), entry("old", 1))
        assertEquals(listOf("favorite", "new"), HistoryRetentionPolicy.records(entries, 2).map { it.id })
    }

    @Test fun `old favorite survives trimming but leaves the record list`() {
        val entries = listOf(entry("new", 4), entry("middle", 3), entry("old-favorite", 2, true), entry("old", 1))
        assertEquals(listOf("old"), HistoryRetentionPolicy.entriesToTrim(entries, 2).map { it.id })
        assertTrue(HistoryRetentionPolicy.entriesToTrim(entries, 1).none { it.favorite })
        assertEquals(listOf("new", "middle"), HistoryRetentionPolicy.records(entries, 2).map { it.id })
    }

    @Test fun `unfavorited entry returns to normal retention`() {
        val entries = listOf(entry("new", 2), entry("former-favorite", 1))
        assertEquals(listOf("former-favorite"), HistoryRetentionPolicy.entriesToTrim(entries, 1).map { it.id })
    }

    private fun entry(id: String, createdAt: Long, favorite: Boolean = false) = HistoryEntryEntity(
        id, createdAt, "image", "thumb", "model", 1, "{}", favorite,
    )
}
