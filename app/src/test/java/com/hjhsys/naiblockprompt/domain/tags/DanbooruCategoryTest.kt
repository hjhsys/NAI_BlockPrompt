package com.hjhsys.naiblockprompt.domain.tags

import org.junit.Assert.*
import org.junit.Test

class DanbooruCategoryTest {
    @Test fun `known IDs normalize but unknown values stay intact`() {
        assertEquals(listOf("general", "artist", "copyright", "character", "meta"), listOf("0", "1", "3", "4", "5").map(DanbooruCategory::normalize))
        assertEquals("99", DanbooruCategory.normalize("99"))
        assertEquals("custom", DanbooruCategory.normalize("custom"))
        assertNull(DanbooruCategory.normalize(null))
    }
}
