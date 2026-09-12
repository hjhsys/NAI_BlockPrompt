package com.hjhsys.naiblockprompt.ui.generate

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptEditorSafetyTest {
    @Test
    fun `external text replacement clamps both selection ends and clears composition`() {
        val current = TextFieldValue(
            text = "1.2::shirt, long hair ::",
            selection = TextRange(22, 8),
            composition = TextRange(5, 24),
        )

        val synchronized = synchronizePromptEditorValue(current, "1.2::shirt")

        assertEquals("1.2::shirt", synchronized.text)
        assertEquals(TextRange(10, 8), synchronized.selection)
        assertNull(synchronized.composition)
    }

    @Test
    fun `syntax transformation preserves text and identity offsets while weight syntax is incomplete`() {
        val transformation = PromptVisualTransformation(Color.Red, Color.Blue, Color.Gray, Color.Green, Color.Yellow)
        val cases = listOf(
            "plain prompt",
            "1.2::shirt ::",
            "1.2::shirt, long hair ::",
            "1.2:shirt ::",
            "1.2::shirt :",
            "1.2::shirt",
            "1.2::shirt ## comment ## ::",
            "1.2::shirt ||red|blue|| ::",
            "::",
            ":",
            "",
        )

        cases.forEach { source ->
            val transformed = transformation.filter(AnnotatedString(source))
            assertEquals(source, transformed.text.text)
            (0..source.length).forEach { offset ->
                assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
                assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
            }
        }
    }

    @Test
    fun `weight visual style covers numeric prefix and delimiters without changing text or offsets`() {
        val source = "tag, 0.8::soft style ::, ||red|blue||, 1.2::strong lighting ::, end"
        val transformation = PromptVisualTransformation(Color.Red, Color.Blue, Color.Gray, Color.Green, Color.Yellow)

        val transformed = transformation.filter(AnnotatedString(source))
        val expectedWeights = listOf("0.8::soft style ::", "1.2::strong lighting ::")
        expectedWeights.forEach { weightText ->
            val start = source.indexOf(weightText)
            val range = transformed.text.spanStyles.single { style ->
                style.start == start && style.end == start + weightText.length && style.item.background != Color.Unspecified
            }
            assertEquals(start, range.start)
            assertEquals(start + weightText.length, range.end)
        }
        assertEquals(source, transformed.text.text)
        (0..source.length).forEach { offset ->
            assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
        }
    }

    @Test
    fun `weight visual style excludes delimiters inside comments`() {
        val source = "## 1.5::comment :: ##"
        val transformation = PromptVisualTransformation(Color.Red, Color.Blue, Color.Gray, Color.Green, Color.Yellow)

        val transformed = transformation.filter(AnnotatedString(source))

        assertEquals(source, transformed.text.text)
        assertEquals(0, transformed.text.spanStyles.count { it.item.background != Color.Unspecified })
    }

    @Test
    fun `active selection suppresses autocomplete until cursor collapses`() {
        val selected = TextFieldValue("1.2::shirt, long hair ::", selection = TextRange(5, 21))
        val cursor = selected.copy(selection = TextRange(10))

        assertNull(autocompleteFragmentForEditor(selected))
        assertEquals(PromptFragment("shirt", 5, 10), autocompleteFragmentForEditor(cursor))
    }

    @Test
    fun `stale autocomplete fragment is rejected after cursor or text changes`() {
        val initial = TextFieldValue("shirt, shirt", selection = TextRange(5))
        val fragment = autocompleteFragmentForEditor(initial)!!

        assertEquals(true, isCurrentAutocompleteFragment(initial, fragment))
        assertEquals(false, isCurrentAutocompleteFragment(initial.copy(selection = TextRange(12)), fragment))
        assertEquals(false, isCurrentAutocompleteFragment(TextFieldValue("short", TextRange(5)), fragment))
    }

    @Test
    fun `same text synchronization clamps stale selection and composition`() {
        val invalid = TextFieldValue(
            text = "shirt",
            selection = TextRange(20, 2),
            composition = TextRange(1, 20),
        )

        val synchronized = synchronizePromptEditorValue(invalid, "shirt")

        assertEquals(TextRange(5, 2), synchronized.selection)
        assertEquals(TextRange(1, 5), synchronized.composition)
    }
}
