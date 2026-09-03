package com.hjhsys.naiblockprompt.ui.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePagerPageTest {
    @Test fun `generate and contextual secondary screen use two page navigation`() {
        assertEquals(0, GenerateNavigationPolicy.SETTINGS_PAGE)
        assertEquals(1, GenerateNavigationPolicy.GENERATE_PAGE)
        assertEquals(2, GenerateNavigationPolicy.SECONDARY_PAGE)
        assertEquals(SecondaryTarget.RESULT, GenerateNavigationPolicy.afterGenerationSuccess())
        assertEquals(SecondaryTarget.HISTORY, GenerateNavigationPolicy.afterHistoryReturn())
        assertFalse(GenerateNavigationPolicy.shouldAutoOpenResult("image-a", "image-a"))
        assertTrue(GenerateNavigationPolicy.shouldAutoOpenResult("image-b", "image-a"))
    }
}
