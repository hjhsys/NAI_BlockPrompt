package com.hjhsys.naiblockprompt.ui.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePagerPageTest {
    @Test fun `sticky prompt toolbar only appears for active toolbar below viewport`() {
        assertFalse(shouldShowStickyPromptToolbar(false, 900f, 800f, 4f))
        assertFalse(shouldShowStickyPromptToolbar(true, 700f, 800f, 4f))
        assertTrue(shouldShowStickyPromptToolbar(true, 796f, 800f, 4f))
        assertTrue(shouldShowStickyPromptToolbar(true, 900f, 800f, 4f))
        assertFalse(shouldShowStickyPromptToolbar(true, Float.NaN, 800f, 4f))
    }
    @Test fun `ime reduces visible prompt viewport and reveals sticky controls`() {
        val effectiveBottom = effectivePromptViewportBottom(100f, 900f, imeBottomPx = 400, windowHeightPx = 900)
        assertEquals(500f, effectiveBottom)
        assertTrue(shouldShowStickyPromptToolbar(true, 700f, effectiveBottom, 4f))
        assertEquals(100f, effectivePromptViewportBottom(100f, 900f, imeBottomPx = 1200, windowHeightPx = 900))
        assertEquals(900f, effectivePromptViewportBottom(100f, 900f, imeBottomPx = 0, windowHeightPx = 900))
    }
    @Test fun `ime does not get subtracted twice when window is already resized`() {
        assertEquals(
            500f,
            effectivePromptViewportBottom(100f, 500f, imeBottomPx = 400, windowHeightPx = 900),
        )
    }
    @Test fun `overlay top never rises above title bar or system inset`() {
        assertEquals(120f, effectivePromptViewportTop(80f, 120f, systemTopInsetPx = 24))
        assertEquals(80f, effectivePromptViewportTop(80f, Float.NaN, systemTopInsetPx = 24))
        assertEquals(24f, effectivePromptViewportTop(Float.NEGATIVE_INFINITY, Float.NaN, systemTopInsetPx = 24))
        assertEquals(24f, effectivePromptViewportTop(-100f, -20f, systemTopInsetPx = 24))
    }
    @Test fun `sticky autocomplete only appears while its editor intersects viewport`() {
        assertTrue(shouldShowStickyAutocomplete(100f, 500f, 50f, 800f))
        assertTrue(shouldShowStickyAutocomplete(-100f, 100f, 50f, 800f))
        assertTrue(shouldShowStickyAutocomplete(700f, 900f, 50f, 800f))
        assertFalse(shouldShowStickyAutocomplete(-200f, 50f, 50f, 800f))
        assertFalse(shouldShowStickyAutocomplete(800f, 900f, 50f, 800f))
        assertFalse(shouldShowStickyAutocomplete(Float.NaN, 500f, 50f, 800f))
    }
    @Test fun `generation workspace uses four stable pages`() {
        assertEquals(0, GenerateNavigationPolicy.SETTINGS_PAGE)
        assertEquals(1, GenerateNavigationPolicy.GENERATE_PAGE)
        assertEquals(2, GenerateNavigationPolicy.RESULT_PAGE)
        assertEquals(3, GenerateNavigationPolicy.HISTORY_PAGE)
        assertEquals(4, GenerateNavigationPolicy.PAGE_COUNT)
        assertEquals(GenerateNavigationPolicy.RESULT_PAGE, GenerateNavigationPolicy.afterGenerationSuccess())
        assertFalse(GenerateNavigationPolicy.shouldDismissQuickEdit(GenerateNavigationPolicy.GENERATE_PAGE))
        assertTrue(GenerateNavigationPolicy.shouldDismissQuickEdit(GenerateNavigationPolicy.SETTINGS_PAGE))
        assertTrue(GenerateNavigationPolicy.shouldDismissQuickEdit(GenerateNavigationPolicy.RESULT_PAGE))
        assertTrue(GenerateNavigationPolicy.shouldDismissQuickEdit(GenerateNavigationPolicy.HISTORY_PAGE))
        assertFalse(GenerateNavigationPolicy.shouldAutoOpenResult("image-a", "image-a"))
        assertTrue(GenerateNavigationPolicy.shouldAutoOpenResult("image-b", "image-a"))
        assertFalse(GenerateNavigationPolicy.shouldAutoOpenResult("restored", null, autoOpenResult = false))
    }

}
