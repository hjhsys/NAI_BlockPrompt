package com.hjhsys.naiblockprompt.domain.prompt

import com.hjhsys.naiblockprompt.domain.model.PromptBlock

data class WeightValidation(
    val delimiterCount: Int,
    val hasUnclosedWeight: Boolean = delimiterCount % 2 != 0,
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

object PromptProcessor {
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

    fun validateWeights(input: String): WeightValidation {
        val count = delimiterPositionsOutsideComments(input).size
        return WeightValidation(count)
    }

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
            WeightSpan(opening, closing + 2, weight)
        }
    }

    fun formatSingleLine(input: String): String = splitEditableItems(input)
        .map { it.trim().replace(Regex("[\\t ]+"), " ").replace(Regex(",+"), ",") }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

    fun formatMultiline(input: String): String = splitEditableItems(input)
        .map { it.trim().replace(Regex("[\\t ]+"), " ") }
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
        .joinToString(" ") { if (it.endsWith(',')) it else "$it," }

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
                !inComment && !inWeight && (input[index] == ',' || input[index] == '\n' || input[index] == '\r') -> {
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
}
