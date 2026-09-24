package com.hjhsys.naiblockprompt.domain.autocomplete

import org.junit.Assert.*
import org.junit.Test

class PromptAutocompleteTest {
    @Test fun `local autocomplete accepts two characters while remote still requires three`() {
        assertEquals(PromptFragment("re", 6, 8), PromptAutocomplete.currentFragment("girl, re", 8))
        assertEquals(PromptFragment("red", 6, 9), PromptAutocomplete.currentFragment("girl, red hair", 9))
        assertFalse(PromptAutocomplete.shouldQueryRemote(PromptFragment("re", 6, 8)))
        assertTrue(PromptAutocomplete.shouldQueryRemote(PromptFragment("red", 6, 9)))
        assertFalse(PromptAutocomplete.shouldQueryRemote(PromptFragment("__h", 0, 3)))
    }

    @Test fun `fragment starts after comma and leading whitespace`() {
        assertEquals(PromptFragment("blue hai", 6, 14), PromptAutocomplete.currentFragment("girl, blue hair", 14))
    }

    @Test fun `selection replaces the entire token when cursor is in the middle`() {
        val source = "girl, blue haircut, smile"
        val fragment = PromptAutocomplete.currentFragment(source, 14)!!
        val replacement = PromptAutocomplete.replace(source, fragment, "blue_hair")
        assertEquals("girl, blue hair, smile", replacement.text)
        assertEquals(17, replacement.cursor)
    }

    @Test fun `single incomplete tag is completed in place`() {
        val replacement = PromptAutocomplete.replace(
            "red hai",
            PromptFragment("red hai", 0, 7),
            "red_hair",
        )
        assertEquals("red hair, ", replacement.text)
    }

    @Test fun `complete tag is normalized in place`() {
        val text = "red hair"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, text.length)!!,
            "red_hair",
        )
        assertEquals("red hair, ", replacement.text)
    }

    @Test fun `selection replaces the complete multi word token`() {
        val text = "shirt lift"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, "shirt".length)!!,
            "shirt",
        )
        assertEquals("shirt, ", replacement.text)
        assertEquals(replacement.text.length, replacement.cursor)
    }

    @Test fun `single tag suffix is replaced without leaving a fragment`() {
        val text = "shirt"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, 3)!!,
            "shirt",
        )
        assertEquals("shirt, ", replacement.text)
    }

    @Test fun `different canonical suggestion replaces the whole typed token`() {
        val text = "shirt lift"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, text.length)!!,
            "shirt",
        )
        assertEquals("shirt, ", replacement.text)
    }

    @Test fun `replacement before another token keeps one comma boundary`() {
        val text = "shirt lift, smile"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, "shirt".length)!!,
            "shirt",
        )
        assertEquals("shirt, smile", replacement.text)
        assertEquals("shirt, ".length, replacement.cursor)
    }

    @Test fun `token replacement works at token start middle and end`() {
        assertEquals("shirt, ", PromptTokenEditing.replaceAtCursor("skirt", 0, "shirt").text)
        assertEquals("shirt, ", PromptTokenEditing.replaceAtCursor("skirt", 2, "shirt").text)
        assertEquals("shirt, ", PromptTokenEditing.replaceAtCursor("skirt", 5, "shirt").text)
    }

    @Test fun `token immediately after comma is replaced without fragments`() {
        val replacement = PromptTokenEditing.replaceAtCursor("girl, skirt", 6, "shirt")
        assertEquals("girl, shirt, ", replacement.text)
        assertEquals(replacement.text.length, replacement.cursor)
    }

    @Test fun `replacement normalizes whitespace around the active token`() {
        val replacement = PromptTokenEditing.replaceAtCursor("girl,   ski rt   ,   smile", 12, "shirt")
        assertEquals("girl, shirt, smile", replacement.text)
        assertEquals("girl, shirt, ".length, replacement.cursor)
    }

    @Test fun `first and last prompt tokens are replaced without duplicate commas`() {
        assertEquals("solo, smile", PromptTokenEditing.replaceAtCursor("girl, smile", 2, "solo").text)
        assertEquals("girl, solo, ", PromptTokenEditing.replaceAtCursor("girl, smile", 9, "solo").text)
    }

    @Test fun `fragment immediately after comma does not create duplicate comma or spaces`() {
        val text = "girl, shi"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, text.length)!!,
            "shirt",
        )
        assertEquals("girl, shirt, ", replacement.text)
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

    @Test fun `canonical suggestion replaces misspelled whole tag`() {
        val text = "op shirt"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, text.length)!!,
            "open_shirt",
        )

        assertEquals("open shirt, ", replacement.text)
    }

    @Test fun `semantically different suggestion replaces whole tag instead of appending`() {
        val text = "thick penis"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, text.length)!!,
            "girthy_penis",
        )

        assertEquals("girthy penis, ", replacement.text)
    }

    @Test fun `weight opener is preserved while autocomplete replaces its tag`() {
        val text = "1.2::op shirt"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, text.length)!!,
            "open_shirt",
        )

        assertEquals("1.2::open shirt, ", replacement.text)
    }

    @Test fun `autocomplete before another line adds a trailing comma`() {
        val text = "op shirt\nblue eyes"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, "op shirt".length)!!,
            "open_shirt",
        )

        assertEquals("open shirt,\nblue eyes", replacement.text)
    }

    @Test fun `autocomplete does not add a comma when next line already starts with one`() {
        val text = "op shirt\n, blue eyes"
        val replacement = PromptAutocomplete.replace(
            text,
            PromptAutocomplete.currentFragment(text, "op shirt".length)!!,
            "open_shirt",
        )

        assertEquals("open shirt\n, blue eyes", replacement.text)
    }

    @Test fun `cursor between weight delimiter characters does not create an invalid fragment range`() {
        val text = "tag, " + "x".repeat(180) + "::weighted tag"
        val cursorBetweenColons = text.indexOf("::") + 1

        assertNull(PromptAutocomplete.currentFragment(text, cursorBetweenColons))
        assertEquals(
            PromptFragment("weighted tag", cursorBetweenColons + 1, text.length),
            PromptAutocomplete.currentFragment(text, text.length),
        )
    }

    @Test fun `autocomplete fragment remains safe for every cursor offset around weight syntax`() {
        val text = "first, 1.2::second tag ::, third"

        (0..text.length).forEach { cursor -> PromptAutocomplete.currentFragment(text, cursor) }
    }
}
