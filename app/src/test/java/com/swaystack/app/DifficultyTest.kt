package com.swaystack.app

import com.swaystack.app.game.Difficulty
import com.swaystack.app.game.Progression
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {
    @Test fun `difficulty ordering is strict`() {
        assertTrue(Difficulty.EASY.speedMultiplier < Difficulty.MEDIUM.speedMultiplier)
        assertTrue(Difficulty.MEDIUM.speedMultiplier < Difficulty.HARD.speedMultiplier)
        assertTrue(Difficulty.EASY.perfectWindowMultiplier > Difficulty.MEDIUM.perfectWindowMultiplier)
        assertTrue(Difficulty.MEDIUM.perfectWindowMultiplier > Difficulty.HARD.perfectWindowMultiplier)
    }

    @Test fun `landmark unlocks follow easy medium hard chain`() {
        val ids = listOf("a", "b")
        assertTrue(Progression.isUnlocked(emptySet(), "a", Difficulty.EASY, ids))
        assertTrue(!Progression.isUnlocked(emptySet(), "a", Difficulty.MEDIUM, ids))
        assertTrue(Progression.isUnlocked(setOf("a:EASY"), "a", Difficulty.MEDIUM, ids))
        assertTrue(!Progression.isUnlocked(setOf("a:EASY"), "a", Difficulty.HARD, ids))
        assertTrue(Progression.isUnlocked(setOf("a:MEDIUM"), "a", Difficulty.HARD, ids))
        assertTrue(Progression.isUnlocked(setOf("a:EASY"), "b", Difficulty.EASY, ids))
    }
}
