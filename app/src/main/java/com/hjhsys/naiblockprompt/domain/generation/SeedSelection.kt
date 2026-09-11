package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.GenerationSettings
import com.hjhsys.naiblockprompt.domain.model.SeedMode

object SeedSelection {
    fun isValid(seed: Long?): Boolean = seed != null && seed in 0..4_294_967_295L

    fun applyFixedSeed(settings: GenerationSettings, seed: Long): GenerationSettings =
        if (isValid(seed)) settings.copy(seedMode = SeedMode.FIXED, seed = seed) else settings

    fun toggleMode(
        settings: GenerationSettings,
        selectedSeed: Long?,
        lastUsedSeed: Long? = null,
        randomSeed: () -> Long = { kotlin.random.Random.nextLong(0, 4_294_967_296L) },
    ): GenerationSettings = changeMode(
        settings = settings,
        mode = if (settings.seedMode == SeedMode.RANDOM) SeedMode.FIXED else SeedMode.RANDOM,
        selectedSeed = selectedSeed,
        lastUsedSeed = lastUsedSeed,
        randomSeed = randomSeed,
    )

    fun changeMode(settings: GenerationSettings, mode: SeedMode, selectedSeed: Long?, lastUsedSeed: Long? = null, randomSeed: () -> Long = { kotlin.random.Random.nextLong(0, 4_294_967_296L) }): GenerationSettings {
        if (settings.seedMode == mode) return settings
        if (mode == SeedMode.RANDOM) return settings.copy(seedMode = mode, seed = null)
        val seed = listOf(selectedSeed, lastUsedSeed, settings.seed).firstOrNull(::isValid)
            ?: randomSeed().takeIf(::isValid)
            ?: 0L
        return settings.copy(seedMode = mode, seed = seed)
    }
}
