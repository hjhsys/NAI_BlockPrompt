package com.hjhsys.naiblockprompt.ui.generate

import com.hjhsys.naiblockprompt.domain.model.PromptEditorOverlayPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class AutocompletePopupPolicyTest {
    @Test
    fun `fixed top ignores cursor overlap`() {
        assertEquals(
            8,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 0,
                visibleBottom = 800,
                margin = 8,
                cursorTop = 20,
                cursorBottom = 60,
                placement = PromptEditorOverlayPlacement.TOP,
            ),
        )
    }

    @Test
    fun `fixed bottom ignores cursor overlap`() {
        assertEquals(
            672,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 0,
                visibleBottom = 800,
                margin = 8,
                cursorTop = 700,
                cursorBottom = 740,
                placement = PromptEditorOverlayPlacement.BOTTOM,
            ),
        )
    }

    @Test
    fun `uses inline autocomplete when its natural position fits above keyboard`() {
        assertEquals(
            true,
            shouldShowInlineAutocomplete(
                inlineTopInWindow = 500f,
                estimatedHeightPx = 120f,
                viewportTopInWindow = 100f,
                viewportBottomInWindow = 800f,
                marginPx = 8f,
            ),
        )
    }

    @Test
    fun `uses popup when inline autocomplete would be clipped`() {
        assertEquals(
            false,
            shouldShowInlineAutocomplete(
                inlineTopInWindow = 700f,
                estimatedHeightPx = 120f,
                viewportTopInWindow = 100f,
                viewportBottomInWindow = 800f,
                marginPx = 8f,
            ),
        )
    }

    @Test
    fun `defaults to top of visible area`() {
        assertEquals(
            8,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 0,
                visibleBottom = 800,
                margin = 8,
            ),
        )
    }

    @Test
    fun `defaults to bottom without ime`() {
        assertEquals(
            672,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 0,
                visibleBottom = 800,
                margin = 8,
                preferBottom = true,
            ),
        )
    }

    @Test
    fun `moves bottom-default overlay to top when it covers cursor`() {
        assertEquals(
            8,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 0,
                visibleBottom = 800,
                margin = 8,
                cursorTop = 700,
                cursorBottom = 724,
                preferBottom = true,
            ),
        )
    }

    @Test
    fun `moves overlay to bottom when top placement covers cursor`() {
        assertEquals(
            522,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 0,
                visibleBottom = 650,
                margin = 8,
                cursorTop = 40,
                cursorBottom = 64,
            ),
        )
    }

    @Test
    fun `keeps popup inside visible area when neither side fully fits`() {
        assertEquals(
            48,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 120,
                visibleTop = 40,
                visibleBottom = 200,
                margin = 8,
            ),
        )
    }

    @Test
    fun `moves popup to top when bottom placement covers cursor`() {
        assertEquals(
            8,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 160,
                visibleTop = 0,
                visibleBottom = 500,
                margin = 8,
                cursorTop = 350,
                cursorBottom = 374,
            ),
        )
    }

    @Test
    fun `keeps popup at bottom when cursor is near top`() {
        assertEquals(
            332,
            EditorOverlayPlacementPolicy.verticalPosition(
                popupHeight = 160,
                visibleTop = 0,
                visibleBottom = 500,
                margin = 8,
                cursorTop = 80,
                cursorBottom = 104,
            ),
        )
    }
}
