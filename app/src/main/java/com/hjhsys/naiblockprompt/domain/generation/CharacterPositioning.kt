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

    fun moveByPixels(
        modelId: String?,
        start: CharacterPosition,
        deltaX: Float,
        deltaY: Float,
        width: Float,
        height: Float,
    ): CharacterPosition {
        if (width <= 0f || height <= 0f) return start
        return normalize(
            modelId,
            start.normalizedX + deltaX / width,
            start.normalizedY + deltaY / height,
        )
    }

    private fun snapToFiveByFive(value: Float): Float =
        ((floor(value.coerceIn(0f, 0.999999f) * 5f) + 0.5f) / 5f)
}
