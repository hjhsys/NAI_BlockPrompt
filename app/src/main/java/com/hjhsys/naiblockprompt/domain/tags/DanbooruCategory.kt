package com.hjhsys.naiblockprompt.domain.tags

object DanbooruCategory {
    fun normalize(value: String?): String? = when (value) {
        "0" -> "general"
        "1" -> "artist"
        "3" -> "copyright"
        "4" -> "character"
        "5" -> "meta"
        else -> value
    }
}
