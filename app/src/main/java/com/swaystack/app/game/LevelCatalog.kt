package com.swaystack.app.game

/** Constellation Path is exactly 13 landmarks with three difficulty tiers each. */
object LevelCatalog {
    const val LANDMARK_LEVELS = 39
    const val INCLUDE_GENERATED_CHALLENGES = false
    const val MIN_PLAYABLE_LEVELS = LANDMARK_LEVELS

    data class LandmarkSlot(val definitionIndex: Int, val difficulty: Difficulty)

    fun landmarkSlot(level: Int, landmarkCount: Int): LandmarkSlot? {
        if (level !in 1..LANDMARK_LEVELS || landmarkCount == 0) return null
        val zeroBased = level - 1
        return LandmarkSlot((zeroBased / Difficulty.entries.size).coerceIn(0, landmarkCount - 1), Difficulty.entries[zeroBased % Difficulty.entries.size])
    }
}
