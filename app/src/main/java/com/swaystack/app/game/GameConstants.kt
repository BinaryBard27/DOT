package com.swaystack.app.game

/** Single source of truth for values that affect difficulty or player timing. */
object GameConstants {
    const val LANDMARK_ACTIVE_PEG_RADIUS_DP = 10f
    const val LANDMARK_INACTIVE_PEG_RADIUS_DP = 8f
    const val FREE_ACTIVE_PEG_RADIUS_DP = 23f
    const val FREE_INACTIVE_PEG_RADIUS_DP = 18f
    const val MIN_LANDMARK_PEG_DISTANCE_DP = 28f
    const val PERFECT_MIN = .06f
    const val PERFECT_MAX = .20f
    const val GOOD_RATIO = .48f
    const val INPUT_OFFSET_MIN_MS = -100
    const val INPUT_OFFSET_MAX_MS = 100
    const val MAX_GOOD_WIDENING = 1.10f
    const val MAX_GOOD_SPEEDUP = 1.10f
}

enum class Difficulty(val speedMultiplier: Float, val perfectWindowMultiplier: Float, val frays: Int) {
    EASY(.80f, 1.18f, 3), MEDIUM(1f, 1f, 3), HARD(1.25f, .82f, 2)
}

fun difficultyFor(level: Int): Difficulty = Difficulty.entries[(level - 1).coerceIn(0, 2)]

object Progression {
    fun isUnlocked(completed: Set<String>, landmarkId: String, difficulty: Difficulty, orderedIds: List<String>): Boolean {
        val index = orderedIds.indexOf(landmarkId)
        if (index < 0) return false
        return when (difficulty) {
            Difficulty.EASY -> index == 0 || completed.contains("${orderedIds[index - 1]}:EASY")
            Difficulty.MEDIUM -> completed.contains("$landmarkId:EASY")
            Difficulty.HARD -> completed.contains("$landmarkId:MEDIUM")
        }
    }
}
