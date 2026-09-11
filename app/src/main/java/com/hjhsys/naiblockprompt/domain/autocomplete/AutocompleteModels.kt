package com.hjhsys.naiblockprompt.domain.autocomplete

enum class SuggestionSource { LOCAL, NOVEL_AI, DANBOORU, WILDCARD }

data class TagSuggestion(
    val tag: String,
    val source: SuggestionSource,
    val danbooruPostCount: Long? = null,
    val naiCount: Double? = null,
    val naiConfidence: Double? = null,
    val category: String? = null,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
)

data class PromptFragment(val text: String, val start: Int, val endExclusive: Int)
data class PromptReplacement(val text: String, val cursor: Int)
data class PromptTokenRange(val start: Int, val endExclusive: Int)

object PromptTokenEditing {
    private val separators = charArrayOf(',', '\n', '\r')

    fun rangeAt(text: String, cursor: Int): PromptTokenRange {
        val safeCursor = cursor.coerceIn(0, text.length)
        val previousSeparator = if (safeCursor == 0) -1 else text.lastIndexOfAny(separators, safeCursor - 1)
        val nextSeparator = text.indexOfAny(separators, safeCursor).takeIf { it >= 0 } ?: text.length
        return PromptTokenRange(previousSeparator + 1, nextSeparator)
    }

    fun replaceAtCursor(text: String, cursor: Int, replacement: String): PromptReplacement =
        replace(text, rangeAt(text, cursor), replacement)

    fun replace(
        text: String,
        range: PromptTokenRange,
        replacement: String,
        preservedTokenPrefix: String = "",
        appendSeparatorAtEnd: Boolean = true,
    ): PromptReplacement {
        val safeStart = range.start.coerceIn(0, text.length)
        val safeEnd = range.endExclusive.coerceIn(safeStart, text.length)
        val before = text.substring(0, safeStart)
        val leadingSpace = if (before.endsWith(',')) " " else ""
        val token = preservedTokenPrefix.trim() + replacement
        val rawSuffix = text.substring(safeEnd)
        val suffix = when {
            rawSuffix.startsWith(',') -> ", " + rawSuffix.substring(1).trimStart(' ', '\t')
            rawSuffix.isEmpty() && appendSeparatorAtEnd -> ", "
            else -> rawSuffix
        }
        val prefix = before + leadingSpace
        val separatorLength = if (rawSuffix.isEmpty()) suffix.length else 0
        return PromptReplacement(
            text = prefix + token + suffix,
            cursor = prefix.length + token.length + separatorLength,
        )
    }

    /** Preserves the current comma-delimited token and inserts a new token after it. */
    fun insertAfter(
        text: String,
        range: PromptTokenRange,
        insertion: String,
        appendSeparatorAtEnd: Boolean = true,
    ): PromptReplacement {
        val safeStart = range.start.coerceIn(0, text.length)
        val safeEnd = range.endExclusive.coerceIn(safeStart, text.length)
        val before = text.substring(0, safeStart)
        val currentToken = text.substring(safeStart, safeEnd).trim()
        if (currentToken.isEmpty()) {
            return replace(
                text = text,
                range = range,
                replacement = insertion,
                appendSeparatorAtEnd = appendSeparatorAtEnd,
            )
        }

        val leadingSpace = if (before.endsWith(',')) " " else ""
        val insertedPrefix = before + leadingSpace + currentToken + ", " + insertion
        val rawSuffix = text.substring(safeEnd)
        val suffix = when {
            rawSuffix.startsWith(',') -> ", " + rawSuffix.substring(1).trimStart(' ', '\t')
            rawSuffix.isEmpty() && appendSeparatorAtEnd -> ", "
            else -> rawSuffix
        }
        val separatorLength = if (rawSuffix.isEmpty()) suffix.length else 0
        return PromptReplacement(
            text = insertedPrefix + suffix,
            cursor = insertedPrefix.length + separatorLength,
        )
    }
}

object PromptAutocomplete {
    const val LOCAL_MIN_QUERY_LENGTH = 2
    const val REMOTE_MIN_QUERY_LENGTH = 3

    fun currentFragment(text: String, cursor: Int): PromptFragment? {
        val safeCursor = cursor.coerceIn(0, text.length)
        val boundary = text.lastIndexOfAny(charArrayOf(',', '\n', '\r'), safeCursor - 1)
        var start = boundary + 1
        val weightDelimiter = text.lastIndexOf("::", safeCursor - 1)
        if (weightDelimiter >= start) start = weightDelimiter + 2
        while (start < safeCursor && text[start].isWhitespace()) start++
        val fragment = text.substring(start, safeCursor)
        return fragment.takeIf {
            it.length >= LOCAL_MIN_QUERY_LENGTH &&
                (it != "__") &&
                it.none { char -> char == '#' }
        }
            ?.let { PromptFragment(it, start, safeCursor) }
    }

    fun shouldQueryRemote(fragment: PromptFragment): Boolean =
        fragment.text.length >= REMOTE_MIN_QUERY_LENGTH && !fragment.text.startsWith("__")

    fun replace(text: String, fragment: PromptFragment, tag: String): PromptReplacement {
        val inserted = if (tag.startsWith("__") && tag.endsWith("__")) tag else tag.replace('_', ' ')
        val tokenRange = PromptTokenEditing.rangeAt(text, fragment.endExclusive)
        val safeFragmentStart = fragment.start.coerceIn(tokenRange.start, tokenRange.endExclusive)
        val safeFragmentEnd = fragment.endExclusive.coerceIn(safeFragmentStart, tokenRange.endExclusive)
        val currentToken = text.substring(tokenRange.start, tokenRange.endExclusive).trim()
        val trailingText = text.substring(safeFragmentEnd, tokenRange.endExclusive)
        val normalizedFragment = text.substring(safeFragmentStart, safeFragmentEnd).trim().lowercase()
        val normalizedInsertion = inserted.trim().lowercase()
        val completesFragment = normalizedFragment.isNotEmpty() &&
            normalizedInsertion.startsWith(normalizedFragment)
        val containsOtherContext = trailingText.any(Char::isWhitespace) ||
            (currentToken.any(Char::isWhitespace) && !completesFragment)

        if (containsOtherContext) {
            return PromptTokenEditing.insertAfter(text, tokenRange, inserted)
        }

        val preservedPrefix = text.substring(tokenRange.start, safeFragmentStart)
        return PromptTokenEditing.replace(text, tokenRange, inserted, preservedPrefix)
    }
}

object AutocompleteDeduplicator {
    fun excludeLocal(local: List<TagSuggestion>, remote: List<TagSuggestion>): List<TagSuggestion> {
        val localKeys = local.mapTo(mutableSetOf()) { canonicalKey(it.tag) }
        return remote.distinctBy { canonicalKey(it.tag) }.filterNot { canonicalKey(it.tag) in localKeys }
    }

    private fun canonicalKey(tag: String) = tag.trim().replace(' ', '_').lowercase()
}
