package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.PromptPolarity
import com.hjhsys.naiblockprompt.domain.model.Session

/** Creates the runtime editor state shown after a whole-session load. */
object LoadedSessionDisplayPolicy {
    fun prepare(loaded: Session): Session = loaded.copy(
        base = loaded.base.copy(selectedPolarity = PromptPolarity.POSITIVE),
        characters = loaded.characters.map { it.copy(selectedPolarity = PromptPolarity.POSITIVE) },
    )
}
