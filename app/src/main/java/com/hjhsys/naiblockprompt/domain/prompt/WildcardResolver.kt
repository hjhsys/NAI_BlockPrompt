package com.hjhsys.naiblockprompt.domain.prompt

import kotlin.random.Random

object WildcardResolver {
    private val token = Regex("__([A-Za-z0-9_.-]+)__")

    fun resolve(text: String, values: Map<String, List<String>>, seed: Long, maxDepth: Int = 12): String {
        fun expand(source: String, depth: Int, stack: Set<String>): String {
            if (depth >= maxDepth) return source
            var occurrence = 0
            return token.replace(source) { match ->
                val name = match.groupValues[1]
                val candidates = values[name].orEmpty().filter(String::isNotBlank)
                if (candidates.isEmpty() || name in stack) match.value
                else {
                    val mixedSeed = seed xor name.hashCode().toLong().shl(32) xor occurrence++.toLong()
                    expand(candidates[Random(mixedSeed).nextInt(candidates.size)], depth + 1, stack + name)
                }
            }
        }
        return expand(text, 0, emptySet())
    }
}
