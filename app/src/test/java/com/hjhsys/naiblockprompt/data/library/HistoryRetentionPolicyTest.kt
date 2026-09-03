package com.hjhsys.naiblockprompt.data.library

import com.hjhsys.naiblockprompt.data.local.entity.HistoryEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRetentionPolicyTest {
    @Test fun `favorite entries do not consume normal history limit`() {
        val entries = listOf(entry("favorite", 4, true), entry("new", 3), entry("middle", 2), entry("old", 1))
        assertEquals(listOf("middle", "old"), HistoryRetentionPolicy.entriesToTrim(entries, 1).map { it.id })
        assertTrue(HistoryRetentionPolicy.entriesToTrim(entries, 1).none { it.favorite })
    }

    @Test fun `unfavorited entry returns to normal retention`() {
        val entries = listOf(entry("new", 2), entry("former-favorite", 1))
        assertEquals(listOf("former-favorite"), HistoryRetentionPolicy.entriesToTrim(entries, 1).map { it.id })
    }

    private fun entry(id: String, createdAt: Long, favorite: Boolean = false) = HistoryEntryEntity(
        id, createdAt, "image", "thumb", "model", 1, "{}", favorite,
    )
}
