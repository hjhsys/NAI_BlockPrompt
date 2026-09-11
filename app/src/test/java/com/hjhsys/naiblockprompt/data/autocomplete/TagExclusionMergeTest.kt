package com.hjhsys.naiblockprompt.data.autocomplete

import com.hjhsys.naiblockprompt.data.local.entity.TagExclusionEntity
import com.hjhsys.naiblockprompt.domain.model.TagExclusionOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagExclusionMergeTest {
    @Test
    fun `user confirmation preserves AI provenance and original reason`() {
        val existing = TagExclusionEntity("bad_tag", "AI", "typo", "Likely misspelling", false, 10, 10)

        val merged = mergeTagExclusion(existing, "bad_tag", TagExclusionOrigin.USER, "user-hidden", null, true, 20)

        assertEquals("AI", merged.origin)
        assertEquals("typo", merged.reasonCode)
        assertEquals("Likely misspelling", merged.reasonText)
        assertTrue(merged.userConfirmed)
        assertEquals(10L, merged.createdAt)
        assertEquals(20L, merged.updatedAt)
    }

    @Test
    fun `later AI candidate does not overwrite existing user exclusion provenance`() {
        val existing = TagExclusionEntity("hidden", "USER", "user-hidden", null, true, 10, 10)

        val merged = mergeTagExclusion(existing, "hidden", TagExclusionOrigin.AI, "noise", "AI reason", false, 20)

        assertEquals("USER", merged.origin)
        assertEquals("user-hidden", merged.reasonCode)
        assertEquals(null, merged.reasonText)
        assertTrue(merged.userConfirmed)
    }
}
