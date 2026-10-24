package com.swaystack.app

import com.swaystack.app.game.LevelGenerator
import com.swaystack.app.game.MotionBounds
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionBoundsTest {
    @Test fun `free play levels one through fifty keep the full swing inside the margin`() {
        val generator = LevelGenerator(360f, 800f)
        for (level in 1..50) {
            val generated = generator.generate(level, level.toLong())
            generated.pegs.forEach { peg ->
                val amplitude = MotionBounds.safeAmplitude(peg.x, 360f, 360f * .32f)
                assertTrue(peg.x - amplitude >= 8f - .001f)
                assertTrue(peg.x + amplitude <= 352f + .001f)
            }
        }
    }

    @Test fun `clamping preserves the eight dp screen margin`() {
        assertTrue(MotionBounds.clampX(-100f, 360f) >= 8f)
        assertTrue(MotionBounds.clampX(1000f, 360f) <= 352f)
        assertTrue(MotionBounds.clampY(-100f, 800f) >= 8f)
        assertTrue(MotionBounds.clampY(1000f, 800f) <= 792f)
    }
}
