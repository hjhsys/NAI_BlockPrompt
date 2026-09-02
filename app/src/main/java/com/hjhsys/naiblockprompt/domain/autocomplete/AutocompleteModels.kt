package com.hjhsys.naiblockprompt.domain.autocomplete

enum class SuggestionSource { NOVEL_AI, DANBOORU }

data class TagSuggestion(
    val tag: String,
    val source: SuggestionSource,
    val postCount: Long? = null,
    val confidence: Double? = null,
    val category: String? = null,
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
