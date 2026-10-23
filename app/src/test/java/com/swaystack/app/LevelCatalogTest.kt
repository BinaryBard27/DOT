package com.swaystack.app

import com.swaystack.app.game.Difficulty
import com.swaystack.app.game.LevelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCatalogTest {
    @Test fun `constellation catalog has exactly thirty nine landmark tiers`() {
        assertEquals(39, LevelCatalog.MIN_PLAYABLE_LEVELS)
        assertTrue(!LevelCatalog.INCLUDE_GENERATED_CHALLENGES)
        for (level in 1..39) assertTrue("level $level", level <= LevelCatalog.MIN_PLAYABLE_LEVELS)
    }

    @Test fun `first thirty nine levels cover every landmark difficulty`() {
        assertEquals(0, LevelCatalog.landmarkSlot(1, 13)!!.definitionIndex)
        assertEquals(Difficulty.EASY, LevelCatalog.landmarkSlot(1, 13)!!.difficulty)
        assertEquals(Difficulty.MEDIUM, LevelCatalog.landmarkSlot(2, 13)!!.difficulty)
        assertEquals(Difficulty.HARD, LevelCatalog.landmarkSlot(3, 13)!!.difficulty)
        assertEquals(12, LevelCatalog.landmarkSlot(39, 13)!!.definitionIndex)
        assertEquals(Difficulty.HARD, LevelCatalog.landmarkSlot(39, 13)!!.difficulty)
        assertNull(LevelCatalog.landmarkSlot(40, 13))
        assertNotNull(LevelCatalog.landmarkSlot(39, 13))
    }
}
