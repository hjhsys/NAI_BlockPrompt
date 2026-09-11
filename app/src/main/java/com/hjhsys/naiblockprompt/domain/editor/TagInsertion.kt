package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.autocomplete.PromptTokenEditing

object TagInsertion {
    fun insert(content: String, cursor: Int, tags: List<String>): String {
        if (tags.isEmpty()) return content
        val position = cursor.coerceIn(0, content.length)
        val inserted = tags.joinToString(", ") { it.replace('_', ' ') }
        if (content.isBlank()) return inserted

        return PromptTokenEditing.insertAfter(
            text = content,
            range = PromptTokenEditing.rangeAt(content, position),
            insertion = inserted,
            appendSeparatorAtEnd = false,
        ).text
    }
}
