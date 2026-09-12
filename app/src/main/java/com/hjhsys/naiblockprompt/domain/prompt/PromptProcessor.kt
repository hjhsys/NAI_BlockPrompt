package com.hjhsys.naiblockprompt.domain.prompt

import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import com.hjhsys.naiblockprompt.domain.model.TextRenderingState

data class WeightValidation(
    val delimiterCount: Int,
    val hasUnclosedWeight: Boolean = delimiterCount % 2 != 0,
)

data class RandomizerValidation(
    val delimiterCount: Int,
    val hasUnclosedRandomizer: Boolean = delimiterCount % 2 != 0,
)

data class WeightSpan(
    val start: Int,
    val endExclusive: Int,
    val weight: Float,
)

data class CommentSpan(
    val start: Int,
    val endExclusive: Int,
)

data class RandomizerSpan(
    val start: Int,
    val endExclusive: Int,
)

data class PromptSplit(
    val left: String,
    val right: String,
)

object PromptProcessor {
    /**
     * Splits only when [cursor] is immediately after a top-level comma or inside the
     * whitespace following it. Delimiters and tag text are never guessed or moved.
     */
    fun splitAtTopLevelComma(input: String, cursor: Int): PromptSplit? {
        if (cursor !in 0..input.length) return null
        var index = 0
        var inComment = false
        var inWeight = false
        var inRandomizer = false
        while (index < input.length) {
            when {
                input.startsWith("##", index) -> {
                    inComment = !inComment
                    index += 2
                }
                !inComment && input.startsWith("::", index) -> {
                    inWeight = !inWeight
                    index += 2
                }
                !inComment && !inWeight && input.startsWith("||", index) -> {
                    inRandomizer = !inRandomizer
                    index += 2
                }
                !inComment && !inWeight && !inRandomizer && input[index] == ',' -> {
                    val commaEnd = index + 1
                    var rightStart = commaEnd
                    while (rightStart < input.length && input[rightStart].isWhitespace()) rightStart++
                    if (cursor in commaEnd..rightStart) {
                        val left = input.substring(0, commaEnd).trimEnd()
                        val right = input.substring(rightStart).trimEnd()
                        if (left.trim().trim(',').isEmpty() || right.trim().trim(',').isEmpty()) return null
                        return PromptSplit(left, right)
                    }
                    index++
                }
                else -> index++
            }
        }
        return null
    }

    /** Uses the same separator shape as two adjacent blocks while retaining editor text. */
    fun mergeBlockContents(upper: String, current: String): String {
        val first = upper.trim()
        val second = current.trim()
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        return "${if (first.endsWith(',')) first else "$first,"}\n\n$second"
    }

    fun commentSpans(input: String): List<CommentSpan> {
        val spans = mutableListOf<CommentSpan>()
        var opening: Int? = null
        var index = 0
        while (index < input.length - 1) {
            if (input.startsWith("##", index)) {
                val start = opening
                if (start == null) {
                    opening = index
                } else {
                    spans += CommentSpan(start, index + 2)
                    opening = null
                }
                index += 2
            } else {
                index++
            }
        }
        opening?.let { spans += CommentSpan(it, input.length) }
        return spans
    }

    fun stripComments(input: String): String {
        val output = StringBuilder(input.length)
        var index = 0
        var inComment = false
        while (index < input.length) {
            if (input.startsWith("##", index)) {
                inComment = !inComment
                index += 2
            } else {
                if (!inComment) output.append(input[index])
                index++
            }
        }
        return output.toString()
    }

    fun randomizerSpans(input: String): List<RandomizerSpan> {
        val positions = delimiterPositions(input, "||")
        return positions.chunked(2).map { pair ->
            RandomizerSpan(pair[0], if (pair.size == 2) pair[1] + 2 else input.length)
        }
    }

    fun validateWeights(input: String): WeightValidation {
        val count = delimiterPositionsOutsideComments(input).size
        return WeightValidation(count)
    }

    fun validateRandomizers(input: String): RandomizerValidation =
        RandomizerValidation(delimiterPositions(input, "||").size)

    fun normalizeWeightClosings(input: String): String {
        val positions = delimiterPositionsOutsideComments(input)
        if (positions.size < 2) return input
        val closingPositions = positions.drop(1).filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val output = StringBuilder(input.length + closingPositions.size)
        var index = 0
        while (index < input.length) {
            if (index in closingPositions && output.lastOrNull()?.isDigit() == true) output.append(' ')
            if (input.startsWith("::", index)) {
                output.append("::")
                index += 2
            } else {
                output.append(input[index])
                index++
            }
        }
        return output.toString()
    }

    fun weightSpans(input: String): List<WeightSpan> {
        val positions = delimiterPositionsOutsideComments(input)
        return positions.chunked(2).mapNotNull { pair ->
            if (pair.size != 2) return@mapNotNull null
            val opening = pair[0]
            val closing = pair[1]
            val weightText = input.substring(0, opening)
                .takeLastWhile { it.isDigit() || it == '.' || it == '-' }
            val weight = weightText.toFloatOrNull() ?: 1f
            WeightSpan(opening - weightText.length, closing + 2, weight)
        }
    }

    fun formatSingleLine(input: String): String = splitEditableItems(input)
        .map { normalizeOutsideProtectedRegions(it, collapseCommas = true) }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

    fun formatMultiline(input: String): String = splitEditableItems(input)
        .map { normalizeOutsideProtectedRegions(it, collapseCommas = false) }
        .filter { it.isNotEmpty() }
        .joinToString(",\n")

    fun joinEnabledBlocks(
        blocks: List<PromptBlock>,
        normalizeWeightClosings: Boolean,
    ): String = blocks
        .asSequence()
        .filter { it.enabled }
        .sortedBy { it.order }
        .map { stripComments(it.content).trim() }
        .filter { it.isNotEmpty() }
        .map { if (normalizeWeightClosings) normalizeWeightClosings(it) else it }
        .joinToString("\n\n") { if (it.endsWith(',')) it else "$it," }

    /** Appends the dedicated literal Text Rendering clause after all regular blocks. */
    fun appendTextRendering(prompt: String, textRendering: TextRenderingState): String {
        if (!textRendering.enabled || textRendering.content.isBlank()) return prompt
        val clauses = listOfNotNull(
            textRendering.description.trim().takeIf(String::isNotEmpty),
            "Text: ${textRendering.content.trim()}",
        ).joinToString("\n")
        return prompt.trimEnd().let { if (it.isEmpty()) clauses else "$it\n$clauses" }
    }

    /**
     * Canonical cleanup shared by generation and informational token estimates.
     * NovelAI documents removal of empty comma-separated prompt elements. Whitespace is
     * intentionally left untouched because V4+ prompting is whitespace-sensitive.
     */
    fun cleanupExtraCommas(input: String): String = input
        .replace(Regex(",(?:\\s*,)+"), ",")
        .replace(Regex("^\\s*,+\\s*"), "")

    private fun delimiterPositionsOutsideComments(input: String): List<Int> {
        val positions = mutableListOf<Int>()
        var index = 0
        var inComment = false
        while (index < input.length - 1) {
            when {
                input.startsWith("##", index) -> {
                    inComment = !inComment
                    index += 2
                }
                !inComment && input.startsWith("::", index) -> {
                    positions += index
                    index += 2
                }
                else -> index++
            }
        }
        return positions
    }

    private fun splitEditableItems(input: String): List<String> {
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        var inComment = false
        var inWeight = false
        var inRandomizer = false
        while (index < input.length) {
            when {
                input.startsWith("##", index) -> {
                    inComment = !inComment
                    current.append("##")
                    index += 2
                }
                !inComment && input.startsWith("::", index) -> {
                    inWeight = !inWeight
                    current.append("::")
                    index += 2
                }
                !inComment && !inWeight && input.startsWith("||", index) -> {
                    inRandomizer = !inRandomizer
                    current.append("||")
                    index += 2
                }
                !inComment && !inWeight && !inRandomizer && (input[index] == ',' || input[index] == '\n' || input[index] == '\r') -> {
                    if (current.isNotBlank()) items += current.toString()
                    current.clear()
                    index++
                    while (index < input.length && (input[index] == ',' || input[index] == '\n' || input[index] == '\r')) index++
                }
                else -> current.append(input[index++])
            }
        }
        if (current.isNotBlank()) items += current.toString()
        return items
    }


    private fun delimiterPositions(input: String, delimiter: String): List<Int> {
        val positions = mutableListOf<Int>()
        var index = 0
        while (index <= input.length - delimiter.length) {
            if (input.startsWith(delimiter, index)) {
                positions += index
                index += delimiter.length
            } else index++
        }
        return positions
    }

    private fun normalizeOutsideProtectedRegions(input: String, collapseCommas: Boolean): String {
        val protected = mutableListOf<String>()
        val masked = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val delimiter = listOf("##", "::", "||").firstOrNull { input.startsWith(it, index) }
            if (delimiter == null) {
                masked.append(input[index++])
                continue
            }
            val closing = input.indexOf(delimiter, index + delimiter.length)
            val end = if (closing < 0) input.length else closing + delimiter.length
            val tokenIndex = protected.size
            protected += input.substring(index, end)
            masked.append('\u0000').append(tokenIndex).append('\u0000')
            index = end
        }
        var normalized = masked.toString().trim().replace(Regex("[\\t ]+"), " ")
        if (collapseCommas) normalized = normalized.replace(Regex(",+"), ",")
        protected.forEachIndexed { tokenIndex, value ->
            normalized = normalized.replace("\u0000$tokenIndex\u0000", value)
        }
        return normalized
    }
}
