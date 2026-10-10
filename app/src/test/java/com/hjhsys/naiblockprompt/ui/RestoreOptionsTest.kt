package com.hjhsys.naiblockprompt.ui

import com.hjhsys.naiblockprompt.domain.editor.CharacterImportMode
import com.hjhsys.naiblockprompt.domain.model.PromptImportPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreOptionsTest {
    @Test
    fun `shared import preferences round trip through history options`() {
        val preferences = PromptImportPreferences(
            basePositive = false,
            baseNegative = true,
            characters = false,
            settings = true,
            seed = false,
            inputImage = true,
            wildcardOriginal = false,
            appendCharacters = true,
        )

        val options = RestoreOptions.from(preferences)

        assertEquals(CharacterImportMode.APPEND, options.characterMode)
        assertEquals(preferences, options.toImportPreferences())
    }
}
