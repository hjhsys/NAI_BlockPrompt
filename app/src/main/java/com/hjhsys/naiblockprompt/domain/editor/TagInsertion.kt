package com.hjhsys.naiblockprompt.domain.editor

object TagInsertion {
    fun insert(content: String, cursor: Int, tags: List<String>): String {
        if (tags.isEmpty()) return content
        val position = cursor.coerceIn(0, content.length)
        val inserted = tags.joinToString(", ") { it.replace('_', ' ') }
        val prefix = content.substring(0, position)
        val suffix = content.substring(position)
        val before = if (
            prefix.isNotBlank() && !prefix.endsWith(",") && !prefix.last().isWhitespace()
        ) ", " else ""
        val after = if (
            suffix.isNotBlank() && !suffix.startsWith(",") && !suffix.first().isWhitespace()
        ) ", " else ""
        return prefix + before + inserted + after + suffix
    }
}
