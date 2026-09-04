package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.CharacterPosition
import kotlin.math.floor

object CharacterPositioning {
    fun supportsContinuousCoordinates(modelId: String?) = modelId?.startsWith("nai-diffusion-5-") == true

    fun normalize(modelId: String?, x: Float, y: Float): CharacterPosition = if (supportsContinuousCoordinates(modelId)) {
        CharacterPosition(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    } else {
        CharacterPosition(snapToFiveByFive(x), snapToFiveByFive(y))
    }

    private fun snapToFiveByFive(value: Float): Float =
        ((floor(value.coerceIn(0f, 0.999999f) * 5f) + 0.5f) / 5f)
}
