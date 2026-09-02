package com.hjhsys.naiblockprompt.domain.autocomplete

import org.junit.Assert.*
import org.junit.Test

class PromptAutocompleteTest {
    @Test fun `requires three character fragment at cursor`() {
        assertNull(PromptAutocomplete.currentFragment("girl, re", 8))
        assertEquals(PromptFragment("red", 6, 9), PromptAutocomplete.currentFragment("girl, red hair", 9))
    }

    @Test fun `fragment starts after comma and leading whitespace`() {
        assertEquals(PromptFragment("blue hai", 6, 14), PromptAutocomplete.currentFragment("girl, blue hair", 14))
    }

    @Test fun `selection replaces only active fragment and keeps suffix`() {
        val source = "girl, blue hai, smile"
        val fragment = PromptAutocomplete.currentFragment(source, 14)!!
        val replacement = PromptAutocomplete.replace(source, fragment, "blue_hair")
        assertEquals("girl, blue hair, smile", replacement.text)
        assertEquals(15, replacement.cursor)
    }

    @Test fun `replacement appends prompt separator at end`() {
        val replacement = PromptAutocomplete.replace("red hai", PromptFragment("red hai", 0, 7), "red_hair")
        assertEquals("red hair, ", replacement.text)
        assertEquals(replacement.text.length, replacement.cursor)
    }

    @Test fun `weighted syntax prefix is not part of queried fragment`() {
        val text = "1.2::red hai"
        assertEquals(PromptFragment("red hai", 5, text.length), PromptAutocomplete.currentFragment(text, text.length))
    }
}
