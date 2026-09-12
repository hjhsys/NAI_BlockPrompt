package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import java.math.BigDecimal

enum class QuickEditUnitType { TAG, WEIGHT_GROUP, COMMENT, RANDOMIZER }

data class QuickEditRange(val start: Int, val endExclusive: Int) {
    init { require(start <= endExclusive) }
    fun contains(offset: Int) = offset in start until endExclusive
}

data class QuickEditUnit(
    val type: QuickEditUnitType,
    val range: QuickEditRange,
    val contentRange: QuickEditRange = range,
    val parentWeightRange: QuickEditRange? = null,
    val weightValue: BigDecimal? = null,
    val children: List<QuickEditUnit> = emptyList(),
)

data class QuickEditSelection(
    val type: QuickEditUnitType,
    val range: QuickEditRange,
    val parentWeightRange: QuickEditRange? = null,
    val weightValue: BigDecimal? = null,
)

data class QuickEditDropTarget(
    val parentWeightRange: QuickEditRange?,
    val itemIndex: Int,
    val offset: Int,
)

sealed interface QuickEditResult {
    /** preferredSelectionOffset is resolved against a freshly parsed [text]. */
    data class Changed(val text: String, val preferredSelectionOffset: Int? = null) : QuickEditResult
    data object Unsupported : QuickEditResult
}

/** Framework-free parser/editor. All ranges use original-string offsets. */
object PromptQuickEdit {
    fun parse(text: String): List<QuickEditUnit> {
        if (PromptProcessor.validateWeights(text).hasUnclosedWeight ||
            PromptProcessor.validateRandomizers(text).hasUnclosedRandomizer
        ) return emptyList()
        val weights = PromptProcessor.weightSpans(text)
        val comments = PromptProcessor.commentSpans(text).map { QuickEditRange(it.start, it.endExclusive) }
        val randomizers = PromptProcessor.randomizerSpans(text).map { QuickEditRange(it.start, it.endExclusive) }
        return splitContainer(text, 0, text.length, weights.map { QuickEditRange(it.start, it.endExclusive) }, comments, randomizers, splitNewlines = true)
            .mapNotNull { raw -> classify(text, raw, weights, comments, randomizers) }
    }

    fun selectionAt(text: String, offset: Int): QuickEditSelection? {
        val at = offset.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        for (unit in parse(text)) {
            if (!unit.range.contains(at)) continue
            if (unit.type == QuickEditUnitType.WEIGHT_GROUP && unit.contentRange.contains(at)) {
                unit.children.firstOrNull { it.range.contains(at) }?.let { child ->
                    // A one-tag weight has no meaningful child/group distinction. Promote a
                    // tap on that tag to the whole group so weight actions stay immediately
                    // available. Multi-tag groups retain individual child selection.
                    if (unit.children.size == 1 && child.type == QuickEditUnitType.TAG) return unit.selection()
                    return child.selection()
                }
            }
            return unit.selection()
        }
        return null
    }

    fun adjustWeight(text: String, selection: QuickEditSelection, delta: BigDecimal): QuickEditResult {
        if (selection.type == QuickEditUnitType.TAG && selection.parentWeightRange != null) return QuickEditResult.Unsupported
        return when (selection.type) {
            QuickEditUnitType.TAG -> {
                val prefix = "${format(BigDecimal.ONE + delta)}::"
                // Select the new group (weight prefix), not its child, so repeated +/- remains available.
                replace(text, selection.range, "$prefix${text.slice(selection.range)} ::", selection.range.start)
            }
            QuickEditUnitType.WEIGHT_GROUP -> {
                val current = selection.weightValue ?: return QuickEditResult.Unsupported
                var next = current + delta
                // NovelAI has no useful zero-weight wrapper. Cross zero in the requested
                // direction so 0.1 - 0.1 becomes -0.1 (and vice versa), never 0::...::.
                if (next.compareTo(BigDecimal.ZERO) == 0) next += delta
                setWeight(text, selection, next)
            }
            else -> QuickEditResult.Unsupported
        }
    }

    fun setWeight(text: String, selection: QuickEditSelection, value: BigDecimal): QuickEditResult {
        if (value.compareTo(BigDecimal.ZERO) == 0) return QuickEditResult.Unsupported
        if (selection.type == QuickEditUnitType.TAG && selection.parentWeightRange == null) {
            return if (value.compareTo(BigDecimal.ONE) == 0) QuickEditResult.Changed(text, selection.range.start)
            else {
                val prefix = "${format(value)}::"
                replace(text, selection.range, "$prefix${text.slice(selection.range)} ::", selection.range.start)
            }
        }
        if (selection.type != QuickEditUnitType.WEIGHT_GROUP) return QuickEditResult.Unsupported
        val unit = parse(text).firstOrNull { it.type == QuickEditUnitType.WEIGHT_GROUP && it.range == selection.range }
            ?: return QuickEditResult.Unsupported
        val body = text.slice(unit.contentRange).trim()
        return if (value.compareTo(BigDecimal.ONE) == 0) replace(text, unit.range, body, unit.range.start)
        else replace(text, unit.range, "${format(value)}::$body ::", unit.range.start)
    }

    fun toggleComment(text: String, selection: QuickEditSelection): QuickEditResult = when (selection.type) {
        QuickEditUnitType.COMMENT -> {
            val raw = text.slice(selection.range)
            replace(text, selection.range, raw.removePrefix("##").removeSuffix("##").trim())
        }
        else -> replace(text, selection.range, "## ${text.slice(selection.range)} ##")
    }

    fun delete(text: String, selection: QuickEditSelection): QuickEditResult {
        val parent = selection.parentWeightRange
        if (parent != null) {
            val group = parse(text).firstOrNull { it.range == parent } ?: return QuickEditResult.Unsupported
            if (group.children.size == 1) return deleteRangeWithSeparator(text, group.range, parse(text).map { it.range })
            val bodyItems = group.children.map { text.slice(it.range) }.toMutableList()
            val index = group.children.indexOfFirst { it.range == selection.range }
            if (index < 0) return QuickEditResult.Unsupported
            bodyItems.removeAt(index)
            val replacement = "${format(group.weightValue!!)}::${bodyItems.joinToString(", ")} ::"
            return replace(text, group.range, replacement)
        }
        return deleteRangeWithSeparator(text, selection.range, parse(text).map { it.range })
    }

    fun dropTargets(text: String, selection: QuickEditSelection): List<QuickEditDropTarget> {
        val top = parse(text)
        val targets = mutableListOf<QuickEditDropTarget>()
        for (i in 0..top.size) {
            when {
                i == 0 -> targets += QuickEditDropTarget(null, i, top.firstOrNull()?.range?.start ?: 0)
                i == top.size -> targets += QuickEditDropTarget(null, i, top.lastOrNull()?.range?.endExclusive ?: 0)
                else -> {
                    // A separator containing line breaks has several visually equivalent cursor
                    // positions. Keep one semantic insertion index, but expose every position in
                    // the gap so dragging after the previous line, onto a blank line, or before
                    // the next item feels identical.
                    val previousEnd = top[i - 1].range.endExclusive
                    val nextStart = top[i].range.start
                    for (offset in previousEnd..nextStart) targets += QuickEditDropTarget(null, i, offset)
                }
            }
        }
        if (selection.type != QuickEditUnitType.WEIGHT_GROUP) {
            top.filter { it.type == QuickEditUnitType.WEIGHT_GROUP }.forEach { group ->
                for (i in 0..group.children.size) {
                    val offset = when { i == 0 -> group.contentRange.start; i == group.children.size -> group.contentRange.endExclusive; else -> group.children[i].range.start }
                    targets += QuickEditDropTarget(group.range, i, offset)
                }
            }
        }
        return targets.filterNot { target ->
            target.parentWeightRange == selection.parentWeightRange && containerIndex(top, selection) in setOf(target.itemIndex, target.itemIndex - 1)
        }.distinctBy { Triple(it.parentWeightRange, it.itemIndex, it.offset) }
    }

    fun move(text: String, selection: QuickEditSelection, target: QuickEditDropTarget): QuickEditResult {
        val top = parse(text)
        val sourceUnit = top.flatMap { listOf(it) + it.children }.firstOrNull { it.range == selection.range } ?: return QuickEditResult.Unsupported
        if (sourceUnit.type == QuickEditUnitType.WEIGHT_GROUP && target.parentWeightRange != null) return QuickEditResult.Unsupported

        data class Node(val type: QuickEditUnitType, val weight: BigDecimal?, val text: String, val originalRange: QuickEditRange, val children: MutableList<Node> = mutableListOf(), var dirty: Boolean = false)
        fun node(u: QuickEditUnit) = Node(u.type, u.weightValue, text.slice(u.range), u.range, u.children.map { Node(it.type, null, text.slice(it.range), it.range) }.toMutableList())
        val nodes = top.map(::node).toMutableList()
        val separators = top.zipWithNext { a, b -> text.substring(a.range.endExclusive, b.range.start) }
        var moved: Node? = null
        var sourceChildIndex = -1
        if (selection.parentWeightRange == null) {
            val i = top.indexOfFirst { it.range == selection.range }; if (i >= 0) moved = nodes.removeAt(i)
        } else {
            val gi = top.indexOfFirst { it.range == selection.parentWeightRange }
            val ci = if (gi >= 0) top[gi].children.indexOfFirst { it.range == selection.range } else -1
            if (gi >= 0 && ci >= 0) {
                sourceChildIndex = ci
                moved = nodes[gi].children.removeAt(ci)
                nodes[gi].dirty = true
                if (nodes[gi].children.isEmpty()) nodes.removeAt(gi)
            }
        }
        val item = moved ?: return QuickEditResult.Unsupported
        if (target.parentWeightRange == null) {
            val targetOriginal = top.getOrNull(target.itemIndex)
            var index = if (targetOriginal == null) nodes.size else nodes.indexOfFirst { it.text == text.slice(targetOriginal.range) }.takeIf { it >= 0 } ?: nodes.size
            nodes.add(index.coerceIn(0, nodes.size), item.copy(type = if (item.type == QuickEditUnitType.TAG) QuickEditUnitType.TAG else item.type))
        } else {
            val originalGroup = top.firstOrNull { it.range == target.parentWeightRange } ?: return QuickEditResult.Unsupported
            val groupIndex = nodes.indexOfFirst { it.originalRange == originalGroup.range }
            if (groupIndex < 0) return QuickEditResult.Unsupported
            val adjustedTarget = if (selection.parentWeightRange == target.parentWeightRange && sourceChildIndex >= 0 && sourceChildIndex < target.itemIndex) {
                target.itemIndex - 1
            } else target.itemIndex
            nodes[groupIndex].children.add(adjustedTarget.coerceIn(0, nodes[groupIndex].children.size), item.copy(type = QuickEditUnitType.TAG, children = mutableListOf()))
            nodes[groupIndex].dirty = true
        }
        fun render(n: Node): String = if (n.type == QuickEditUnitType.WEIGHT_GROUP && n.dirty) "${format(n.weight!!)}::${n.children.joinToString(", ") { it.text.trim() }} ::" else n.text
        val output = buildString {
            nodes.forEachIndexed { index, node ->
                if (index > 0) append(separators.getOrNull(index - 1) ?: ", ")
                append(render(node))
            }
        }
        return QuickEditResult.Changed(output)
    }

    fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun classify(text: String, raw: QuickEditRange, weights: List<com.hjhsys.naiblockprompt.domain.prompt.WeightSpan>, comments: List<QuickEditRange>, randomizers: List<QuickEditRange>): QuickEditUnit? {
        val range = trimRange(text, raw)
        if (range.start == range.endExclusive) return null
        comments.firstOrNull { it == range }?.let { return QuickEditUnit(QuickEditUnitType.COMMENT, range) }
        randomizers.firstOrNull { it == range }?.let { return QuickEditUnit(QuickEditUnitType.RANDOMIZER, range) }
        val weight = weights.firstOrNull { it.start == range.start && it.endExclusive == range.endExclusive }
        if (weight != null) {
            val open = text.indexOf("::", range.start)
            val close = range.endExclusive - 2
            val content = trimRange(text, QuickEditRange(open + 2, close))
            val protected = (comments + randomizers).filter { it.start >= content.start && it.endExclusive <= content.endExclusive }
            val children = splitContainer(text, content.start, content.endExclusive, emptyList(), protected.filter { text.startsWith("##", it.start) }, protected.filter { text.startsWith("||", it.start) }, splitNewlines = false)
                .mapNotNull { child ->
                    val cr = trimRange(text, child); if (cr.start == cr.endExclusive) null
                    else {
                        val type = when { text.startsWith("##", cr.start) -> QuickEditUnitType.COMMENT; text.startsWith("||", cr.start) -> QuickEditUnitType.RANDOMIZER; else -> QuickEditUnitType.TAG }
                        QuickEditUnit(type, cr, cr, range)
                    }
                }
            return QuickEditUnit(QuickEditUnitType.WEIGHT_GROUP, range, content, weightValue = weight.weight.toBigDecimal(), children = children)
        }
        return QuickEditUnit(QuickEditUnitType.TAG, range)
    }

    private fun splitContainer(text: String, start: Int, end: Int, weights: List<QuickEditRange>, comments: List<QuickEditRange>, randomizers: List<QuickEditRange>, splitNewlines: Boolean): List<QuickEditRange> {
        val protected = (weights + comments + randomizers).sortedBy { it.start }
        val out = mutableListOf<QuickEditRange>(); var itemStart = start; var i = start
        while (i < end) {
            val span = protected.firstOrNull { it.start == i && it.endExclusive <= end }
            if (span != null) { i = span.endExclusive; continue }
            if (text[i] == ',' || splitNewlines && (text[i] == '\n' || text[i] == '\r')) {
                out += QuickEditRange(itemStart, i)
                if (text[i] == '\r' && i + 1 < end && text[i + 1] == '\n') i++
                itemStart = i + 1
            }
            i++
        }
        out += QuickEditRange(itemStart, end)
        return out
    }

    private fun trimRange(text: String, range: QuickEditRange): QuickEditRange {
        var s = range.start; var e = range.endExclusive
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return QuickEditRange(s, e)
    }
    private fun replace(text: String, range: QuickEditRange, value: String, preferredSelectionOffset: Int? = null) =
        QuickEditResult.Changed(text.replaceRange(range.start, range.endExclusive, value), preferredSelectionOffset)
    private fun String.slice(range: QuickEditRange) = substring(range.start, range.endExclusive)
    private fun QuickEditUnit.selection() = QuickEditSelection(type, range, parentWeightRange, weightValue)
    private fun containerIndex(top: List<QuickEditUnit>, s: QuickEditSelection): Int = if (s.parentWeightRange == null) top.indexOfFirst { it.range == s.range } else top.firstOrNull { it.range == s.parentWeightRange }?.children?.indexOfFirst { it.range == s.range } ?: -1
    private fun deleteRangeWithSeparator(text: String, range: QuickEditRange, siblings: List<QuickEditRange>): QuickEditResult {
        val index = siblings.indexOf(range)
        val removal = when {
            index < 0 -> range
            index < siblings.lastIndex -> QuickEditRange(range.start, siblings[index + 1].start)
            index > 0 -> QuickEditRange(siblings[index - 1].endExclusive, range.endExclusive)
            else -> range
        }
        return replace(text, removal, "")
    }
}
