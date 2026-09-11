package com.hjhsys.naiblockprompt.domain.prompt

data class TokenRange(val minimum: Int, val maximum: Int) {
    val hasRange get() = minimum != maximum
}

/**
 * Offline T5-oriented estimate. This remains informational and must never gate generation.
 * Inputs use the same confirmed extra-comma cleanup as generation; whitespace is preserved.
 */
object PromptTokenEstimator {
    private val wildcard = Regex("__([A-Za-z0-9_.-]+)__")
    private val randomizer = Regex("\\|\\|([^|]*(?:\\|[^|]+)*)\\|\\|")

    fun estimate(text: String, wildcards: Map<String, List<String>>): TokenRange {
        val variants = bounds(text, wildcards, emptySet(), 0)
        return TokenRange(count(variants.first), count(variants.second))
    }

    private fun bounds(text: String, values: Map<String, List<String>>, stack: Set<String>, depth: Int): Pair<String, String> {
        if (depth >= 12) return text to text
        fun choose(source: String, regex: Regex, candidates: (MatchResult) -> List<String>, nextStack: (MatchResult) -> Set<String>): Pair<String, String> {
            val match = regex.find(source) ?: return source to source
            val options = candidates(match).ifEmpty { return source to source }.map { bounds(it, values, nextStack(match), depth + 1) }
            val min = options.minBy { count(it.first) }.first
            val max = options.maxBy { count(it.second) }.second
            val prefix = source.substring(0, match.range.first)
            val suffix = source.substring(match.range.last + 1)
            val minRest = bounds(prefix + min + suffix, values, stack, depth + 1)
            val maxRest = bounds(prefix + max + suffix, values, stack, depth + 1)
            return minRest.first to maxRest.second
        }
        wildcard.find(text)?.let { match ->
            val name = match.groupValues[1]
            if (name !in stack && values[name].orEmpty().isNotEmpty()) return choose(text, wildcard, { values[name].orEmpty() }, { stack + name })
        }
        randomizer.find(text)?.let { return choose(text, randomizer, { it.groupValues[1].split('|') }, { stack }) }
        return text to text
    }

    private fun estimateT5Like(text: String): Int {
        val cleaned = PromptProcessor.cleanupExtraCommas(text)
        if (cleaned.isBlank()) return 0
        val pieces = Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N},]").findAll(cleaned).map { it.value }
        return pieces.sumOf { piece ->
            when {
                piece.length <= 6 -> 1
                piece.all(Char::isLetterOrDigit) -> (piece.length + 6) / 7
                else -> 1
            }
        } + 1
    }

    private fun count(text: String) = estimateT5Like(PromptProcessor.cleanupExtraCommas(text))
}
