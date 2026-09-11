package com.hjhsys.naiblockprompt.ui.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePagerPageTest {
    @Test fun `generation workspace uses four stable pages`() {
        assertEquals(0, GenerateNavigationPolicy.SETTINGS_PAGE)
        assertEquals(1, GenerateNavigationPolicy.GENERATE_PAGE)
        assertEquals(2, GenerateNavigationPolicy.RESULT_PAGE)
        assertEquals(3, GenerateNavigationPolicy.HISTORY_PAGE)
        assertEquals(4, GenerateNavigationPolicy.PAGE_COUNT)
        assertEquals(GenerateNavigationPolicy.RESULT_PAGE, GenerateNavigationPolicy.afterGenerationSuccess())
        assertFalse(GenerateNavigationPolicy.shouldAutoOpenResult("image-a", "image-a"))
        assertTrue(GenerateNavigationPolicy.shouldAutoOpenResult("image-b", "image-a"))
        assertFalse(GenerateNavigationPolicy.shouldAutoOpenResult("restored", null, autoOpenResult = false))
    }

}
