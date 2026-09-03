package com.hjhsys.naiblockprompt.domain.autocomplete

enum class SuggestionSource { LOCAL, NOVEL_AI, DANBOORU }

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

object PromptAutocomplete {
    fun currentFragment(text: String, cursor: Int): PromptFragment? {
        val safeCursor = cursor.coerceIn(0, text.length)
        val boundary = text.lastIndexOfAny(charArrayOf(',', '\n', '\r'), safeCursor - 1)
        var start = boundary + 1
        val weightDelimiter = text.lastIndexOf("::", safeCursor - 1)
        if (weightDelimiter >= start) start = weightDelimiter + 2
        while (start < safeCursor && text[start].isWhitespace()) start++
        val fragment = text.substring(start, safeCursor)
        return fragment.takeIf { it.length >= 3 && it.none { char -> char == '#' } }
            ?.let { PromptFragment(it, start, safeCursor) }
    }

    fun replace(text: String, fragment: PromptFragment, tag: String): PromptReplacement {
        val suffix = text.substring(fragment.endExclusive)
        val separator = if (suffix.startsWith(",") || suffix.startsWith("\n") || suffix.startsWith("\r")) "" else ", "
        val inserted = tag.replace('_', ' ')
        val prefix = text.substring(0, fragment.start)
        return PromptReplacement(prefix + inserted + separator + suffix, prefix.length + inserted.length + separator.length)
    }
}

object AutocompleteDeduplicator {
    fun excludeLocal(local: List<TagSuggestion>, remote: List<TagSuggestion>): List<TagSuggestion> {
        val localKeys = local.mapTo(mutableSetOf()) { canonicalKey(it.tag) }
        return remote.distinctBy { canonicalKey(it.tag) }.filterNot { canonicalKey(it.tag) in localKeys }
    }

    private fun canonicalKey(tag: String) = tag.trim().replace(' ', '_').lowercase()
}
