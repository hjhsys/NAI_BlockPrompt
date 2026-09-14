package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.PromptPolarity

data class PromptUndoKey(
    val owner: PromptOwner,
    val polarity: PromptPolarity,
    val blockId: String,
)

data class PromptEditorSnapshot(
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    fun normalized(): PromptEditorSnapshot {
        val start = selectionStart.coerceIn(0, content.length)
        val end = selectionEnd.coerceIn(0, content.length)
        return copy(selectionStart = start, selectionEnd = end)
    }
}

enum class PromptEditKind { TYPING, DISCRETE }

/** In-memory, block-local, content-only undo/redo history. */
class PromptUndoHistory(
    private val maxSteps: Int = 30,
    private val typingCoalesceMillis: Long = 900L,
) {
    private data class BlockHistory(
        val undo: ArrayDeque<PromptEditorSnapshot> = ArrayDeque(),
        val redo: ArrayDeque<PromptEditorSnapshot> = ArrayDeque(),
        var lastTypingAtMillis: Long? = null,
    )

    private val histories = mutableMapOf<PromptUndoKey, BlockHistory>()

    fun recordBeforeChange(
        key: PromptUndoKey,
        snapshot: PromptEditorSnapshot,
        kind: PromptEditKind,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val normalized = snapshot.normalized()
        val history = histories.getOrPut(key) { BlockHistory() }
        val lastTyping = history.lastTypingAtMillis
        val beginsTypingGroup = kind == PromptEditKind.TYPING &&
            (lastTyping == null || nowMillis - lastTyping > typingCoalesceMillis)
        val shouldPush = kind == PromptEditKind.DISCRETE || beginsTypingGroup
        if (shouldPush && history.undo.lastOrNull() != normalized) {
            history.undo.addLast(normalized)
            while (history.undo.size > maxSteps) history.undo.removeFirst()
        }
        history.redo.clear()
        history.lastTypingAtMillis = if (kind == PromptEditKind.TYPING) nowMillis else null
    }

    fun undo(key: PromptUndoKey, current: PromptEditorSnapshot): PromptEditorSnapshot? {
        val history = histories[key] ?: return null
        history.lastTypingAtMillis = null
        val result = history.undo.removeLastOrNull() ?: return null
        history.redo.addLast(current.normalized())
        return result
    }

    fun redo(key: PromptUndoKey, current: PromptEditorSnapshot): PromptEditorSnapshot? {
        val history = histories[key] ?: return null
        history.lastTypingAtMillis = null
        val result = history.redo.removeLastOrNull() ?: return null
        history.undo.addLast(current.normalized())
        return result
    }

    fun canUndo(key: PromptUndoKey): Boolean = histories[key]?.undo?.isNotEmpty() == true
    fun canRedo(key: PromptUndoKey): Boolean = histories[key]?.redo?.isNotEmpty() == true

    fun clear() = histories.clear()
}
