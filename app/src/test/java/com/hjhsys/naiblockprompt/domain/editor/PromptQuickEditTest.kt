package com.hjhsys.naiblockprompt.domain.editor

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hjhsys.naiblockprompt.domain.model.PromptPolarity

class PromptQuickEditTest {
    private fun selection(text: String, needle: String, inside: Int = 0) =
        PromptQuickEdit.selectionAt(text, text.indexOf(needle) + inside).also(::assertNotNull)!!

    private fun changed(result: QuickEditResult) = (result as QuickEditResult.Changed).text
    private fun changedWithSelection(result: QuickEditResult): Pair<String, QuickEditSelection?> {
        val changed = result as QuickEditResult.Changed
        return changed.text to changed.preferredSelectionOffset?.let { PromptQuickEdit.selectionAt(changed.text, it) }
    }

    @Test fun `weight wrap selects the new group for repeated adjustment and unwrap selects tag`() {
        val (wrapped, wrappedSelection) = changedWithSelection(PromptQuickEdit.adjustWeight("ABC", selection("ABC", "ABC"), BigDecimal("0.1")))
        assertEquals("1.1::ABC ::", wrapped)
        assertEquals("1.1::ABC ::", wrapped.substring(wrappedSelection!!.range.start, wrappedSelection.range.endExclusive))
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, wrappedSelection.type)
        val twice = PromptQuickEdit.adjustWeight(wrapped, wrappedSelection, BigDecimal("0.1")) as QuickEditResult.Changed
        assertEquals("1.2::ABC ::", twice.text)

        val (unwrapped, unwrappedSelection) = changedWithSelection(PromptQuickEdit.adjustWeight(wrapped, wrappedSelection, BigDecimal("-0.1")))
        assertEquals("ABC", unwrapped)
        assertEquals("ABC", unwrapped.substring(unwrappedSelection!!.range.start, unwrappedSelection.range.endExclusive))
    }

    @Test fun `selection hint resolves the changed duplicate not another equal tag`() {
        val text = "ABC, DEF, ABC"
        val first = selection(text, "ABC")
        val (changed, selected) = changedWithSelection(PromptQuickEdit.adjustWeight(text, first, BigDecimal("0.1")))
        assertEquals("1.1::ABC ::, DEF, ABC", changed)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, selected!!.type)
        assertEquals(0, selected.range.start)
    }

    @Test fun `selects ordinary comma chunk`() {
        val text = "ABC, DEF, GHI"
        assertEquals("DEF", text.substring(selection(text, "DEF").range.start, selection(text, "DEF").range.endExclusive))
    }

    @Test fun `distinguishes weight group delimiters and children`() {
        val text = "1.2::ABC, DEF ::, GHI"
        assertEquals(QuickEditUnitType.TAG, selection(text, "ABC").type)
        assertEquals(QuickEditUnitType.TAG, selection(text, "DEF").type)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, selection(text, "1.2").type)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, selection(text, "::").type)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, PromptQuickEdit.selectionAt(text, text.lastIndexOf("::"))!!.type)
    }

    @Test fun `single weight child tap promotes to whole group`() {
        val single = "1.2::ABC ::"
        val selected = selection(single, "ABC")
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, selected.type)
        assertEquals(single, single.substring(selected.range.start, selected.range.endExclusive))

        val multiple = "1.2::ABC, DEF ::"
        assertEquals(QuickEditUnitType.TAG, selection(multiple, "ABC").type)
        assertEquals(QuickEditUnitType.TAG, selection(multiple, "DEF").type)
    }

    @Test fun `wraps ordinary tags with decimal safe weight`() {
        val plus = changed(PromptQuickEdit.adjustWeight("ABC", selection("ABC", "ABC"), BigDecimal("0.1")))
        val minus = changed(PromptQuickEdit.adjustWeight("ABC", selection("ABC", "ABC"), BigDecimal("-0.1")))
        assertEquals("1.1::ABC ::", plus)
        assertEquals("0.9::ABC ::", minus)
    }

    @Test fun `adjusts group and unwraps exactly at one`() {
        var text = "1.2::ABC, DEF ::"
        val increased = PromptQuickEdit.adjustWeight(text, selection(text, "1.2"), BigDecimal("0.1")) as QuickEditResult.Changed
        assertEquals("1.3::ABC, DEF ::", increased.text)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, PromptQuickEdit.selectionAt(increased.text, increased.preferredSelectionOffset!!)!!.type)
        text = "1.1::ABC, DEF ::"
        assertEquals("ABC, DEF", changed(PromptQuickEdit.adjustWeight(text, selection(text, "1.1"), BigDecimal("-0.1"))))
        text = "0.9::ABC ::"
        assertEquals("ABC", changed(PromptQuickEdit.adjustWeight(text, selection(text, "0.9"), BigDecimal("0.1"))))
    }

    @Test fun `inner weight tag never auto splits`() {
        val text = "1.2::ABC, DEF ::"
        assertTrue(PromptQuickEdit.adjustWeight(text, selection(text, "ABC"), BigDecimal("0.1")) is QuickEditResult.Unsupported)
    }

    @Test fun `toggles ordinary and inner comments`() {
        var text = "ABC"
        text = changed(PromptQuickEdit.toggleComment(text, selection(text, "ABC")))
        assertEquals("## ABC ##", text)
        assertEquals("ABC", changed(PromptQuickEdit.toggleComment(text, selection(text, "##"))))
        text = "1.2::ABC, DEF ::"
        assertEquals("1.2::ABC, ## DEF ## ::", changed(PromptQuickEdit.toggleComment(text, selection(text, "DEF"))))
    }

    @Test fun `deletes chunks and empty group safely`() {
        var text = "ABC, DEF, GHI"
        assertEquals("ABC, GHI", changed(PromptQuickEdit.delete(text, selection(text, "DEF"))))
        text = "1.2::ABC, DEF ::"
        assertEquals("1.2::DEF ::", changed(PromptQuickEdit.delete(text, selection(text, "ABC"))))
        text = "1.2::ABC ::, GHI"
        assertEquals("GHI", changed(PromptQuickEdit.delete(text, selection(text, "ABC"))))
    }

    @Test fun `moves tag outside into and between weights`() {
        var text = "1.2::ABC, DEF ::, GHI"
        var source = selection(text, "ABC")
        var target = PromptQuickEdit.dropTargets(text, source).first { it.parentWeightRange == null && it.itemIndex == 0 }
        assertEquals("ABC, 1.2::DEF ::, GHI", changed(PromptQuickEdit.move(text, source, target)))

        text = "ABC, 1.2::DEF ::"
        source = selection(text, "ABC")
        target = PromptQuickEdit.dropTargets(text, source).first { it.parentWeightRange != null && it.itemIndex == 0 }
        assertEquals("1.2::ABC, DEF ::", changed(PromptQuickEdit.move(text, source, target)))

        text = "1.2::ABC, DEF ::, 0.8::GHI ::"
        source = selection(text, "ABC")
        val second = PromptQuickEdit.parse(text).last().range
        target = PromptQuickEdit.dropTargets(text, source).first { it.parentWeightRange == second && it.itemIndex == 0 }
        assertEquals("1.2::DEF ::, 0.8::ABC, GHI ::", changed(PromptQuickEdit.move(text, source, target)))
    }

    @Test fun `moving forward inside one weight uses pre-removal boundary correctly`() {
        val text = "1.2::ABC, DEF, GHI ::"
        val source = selection(text, "ABC")
        val group = PromptQuickEdit.parse(text).single().range
        val betweenDefAndGhi = PromptQuickEdit.dropTargets(text, source).first { it.parentWeightRange == group && it.itemIndex == 2 }
        assertEquals("1.2::DEF, ABC, GHI ::", changed(PromptQuickEdit.move(text, source, betweenDefAndGhi)))
    }

    @Test fun `weight groups stay independent and cannot be nested`() {
        val text = "1.2::ABC ::, 1.2::DEF ::"
        val source = selection(text, "1.2")
        assertTrue(PromptQuickEdit.dropTargets(text, source).all { it.parentWeightRange == null })
        assertEquals(2, PromptQuickEdit.parse(text).count { it.type == QuickEditUnitType.WEIGHT_GROUP })
    }

    @Test fun `comments and randomizers are protected atomic units`() {
        val text = "|| A, B ||, ## C, D ##, E"
        val units = PromptQuickEdit.parse(text)
        assertEquals(listOf(QuickEditUnitType.RANDOMIZER, QuickEditUnitType.COMMENT, QuickEditUnitType.TAG), units.map { it.type })
    }

    @Test fun `newlines split only top level tags including CRLF`() {
        assertEquals(3, PromptQuickEdit.parse("ABC\nDEF\r\nGHI").size)
        assertEquals(4, PromptQuickEdit.parse("ABC, DEF\nGHI, JKL").size)
        val weighted = PromptQuickEdit.parse("1.2::ABC\nDEF ::")
        assertEquals(1, weighted.size)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, weighted.single().type)
        assertEquals(1, weighted.single().children.size)
        assertEquals(QuickEditUnitType.COMMENT, PromptQuickEdit.parse("## ABC\nDEF ##").single().type)
        assertEquals(QuickEditUnitType.RANDOMIZER, PromptQuickEdit.parse("|| ABC\nDEF ||").single().type)
    }

    @Test fun `multiline gap exposes equivalent visual drop offsets for one insertion index`() {
        val text = "ABC,\n\nDEF,\nGHI"
        val source = selection(text, "GHI")
        val betweenFirstAndSecond = PromptQuickEdit.dropTargets(text, source).filter { it.parentWeightRange == null && it.itemIndex == 1 }
        assertTrue(betweenFirstAndSecond.any { it.offset == text.indexOf("ABC") + 3 })
        assertTrue(betweenFirstAndSecond.any { it.offset == text.indexOf("DEF") })
        assertTrue(betweenFirstAndSecond.map { it.offset }.distinct().size >= 3)
        assertEquals(
            changed(PromptQuickEdit.move(text, source, betweenFirstAndSecond.first())),
            changed(PromptQuickEdit.move(text, source, betweenFirstAndSecond.last())),
        )
    }

    @Test fun `unrelated prompt text and selection source remain unchanged`() {
        val text = "keep  spacing, ABC, keep\nline"
        val selected = selection(text, "ABC")
        val result = changed(PromptQuickEdit.adjustWeight(text, selected, BigDecimal("0.1")))
        assertEquals("keep  spacing, 1.1::ABC ::, keep\nline", result)
        assertEquals(text, "keep  spacing, ABC, keep\nline")
    }

    @Test fun `repeated decimal steps never expose float drift`() {
        var text = "ABC"
        text = changed(PromptQuickEdit.adjustWeight(text, selection(text, "ABC"), BigDecimal("0.1")))
        repeat(2) { text = changed(PromptQuickEdit.adjustWeight(text, selection(text, text.substringBefore("::")), BigDecimal("0.1"))) }
        assertEquals("1.3::ABC ::", text)
    }

    @Test fun `weight adjustment skips zero and supports negative groups`() {
        var text = "0.1::ABC ::"
        var result = PromptQuickEdit.adjustWeight(text, selection(text, "0.1"), BigDecimal("-0.1")) as QuickEditResult.Changed
        assertEquals("-0.1::ABC ::", result.text)
        assertEquals(QuickEditUnitType.WEIGHT_GROUP, PromptQuickEdit.selectionAt(result.text, result.preferredSelectionOffset!!)!!.type)

        text = result.text
        result = PromptQuickEdit.adjustWeight(text, selection(text, "-0.1"), BigDecimal("0.1")) as QuickEditResult.Changed
        assertEquals("0.1::ABC ::", result.text)

        text = "-0.1::ABC ::"
        assertEquals("-0.2::ABC ::", changed(PromptQuickEdit.adjustWeight(text, selection(text, "-0.1"), BigDecimal("-0.1"))))
        assertEquals("-0.5::ABC ::", changed(PromptQuickEdit.setWeight(text, selection(text, "-0.1"), BigDecimal("-0.5"))))
        assertTrue(PromptQuickEdit.setWeight(text, selection(text, "-0.1"), BigDecimal.ZERO) is QuickEditResult.Unsupported)
    }

    @Test fun `each quick mutation can be one discrete undo step`() {
        val history = PromptUndoHistory()
        val key = PromptUndoKey(PromptOwner.Base, PromptPolarity.POSITIVE, "block")
        val before = "ABC"
        history.recordBeforeChange(key, PromptEditorSnapshot(before, 2, 2), PromptEditKind.DISCRETE)
        val after = changed(PromptQuickEdit.adjustWeight(before, selection(before, "ABC"), BigDecimal("0.1")))
        assertEquals("1.1::ABC ::", after)
        assertEquals(before, history.undo(key)?.content)
    }
}
