package com.swaystack.app

import com.swaystack.app.game.GeneratedLevel
import com.swaystack.app.game.LevelGenerator
import com.swaystack.app.game.Peg
import com.swaystack.app.game.PathShape
import com.swaystack.app.game.GameState
import com.swaystack.app.game.PlayMode
import com.swaystack.app.game.StringSegment
import com.swaystack.app.game.ImpactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelGeneratorTest {
    private val generator = LevelGenerator(1080f, 1920f)

    @Test fun `levels one through thirty use staged generated data`() {
        val levels = (1..30).map { generator.generate(it, 1000L + it) }
        assertEquals(5, levels.first().pegs.size)
        assertEquals(40, levels[29].pegs.size)
        // In vertical layout, STRAIGHT path has constant X, while shaped paths vary in X.
        assertTrue(levels[0].pegs.zipWithNext().all { it.first.x == it.second.x })
        assertTrue(levels[1].pegs.zipWithNext().any { it.first.x != it.second.x })
        assertTrue(levels[2].pegs.zipWithNext().any { it.first.x != it.second.x })
        // All paths progress upward (Y decreases)
        assertTrue(levels.all { lvl -> lvl.pegs.zipWithNext().all { it.first.y > it.second.y } })
        assertEquals(0, levels[13].deadPegs.size)
        assertEquals(levels[14].pegs.size / 10, levels[14].deadPegs.size)
        assertTrue(levels[0].perfectWindowRatio > levels[29].perfectWindowRatio)
        assertTrue(levels.all { generator.validate(it).completable })
    }

    @Test fun `same seed produces identical daily layout`() {
        val a = generator.generateDaily(20450)
        val b = generator.generateDaily(20450)
        assertEquals(a.pegs, b.pegs)
        assertEquals(a.deadPegs, b.deadPegs)
    }

    @Test fun `validator rejects deliberately broken seed output`() {
        val valid = generator.generate(20, 7)
        val broken = valid.copy(pegs = valid.pegs.drop(1) + Peg(valid.pegs[0].x, valid.pegs[0].y, dead = true))
        val result = generator.validate(broken)
        assertFalse(result.completable)
        assertTrue(result.reason!!.contains("dead"))
    }

    @Test fun `path shape variety is independent from early difficulty`() {
        val levels = (1..10).map { generator.generate(it, 1000L + it) }
        assertEquals(PathShape.STRAIGHT, levels[0].pathShape)
        assertEquals(PathShape.SINE_WAVE, levels[1].pathShape)
        assertEquals(PathShape.ZIGZAG, levels[2].pathShape)
        assertEquals(PathShape.STRAIGHT, levels[3].pathShape)
        assertTrue(levels.take(5).all { it.oscillationSpeed == .0021f })
        assertTrue(levels[0].perfectWindowRatio > levels[5].perfectWindowRatio)
    }

    @Test fun `new shapes are staged and every shape validates independently`() {
        val early = (1..10).map { generator.generate(it, 2000L + it) }
        assertTrue(early.all { it.pathShape in setOf(PathShape.STRAIGHT, PathShape.SINE_WAVE, PathShape.ZIGZAG) })

        val middle = (11..34).map { generator.generate(it, 3000L + it) }
        assertTrue(middle.any { it.pathShape == PathShape.ARC })
        assertTrue(middle.any { it.pathShape == PathShape.STAIRCASE })
        assertTrue(middle.none { it.pathShape == PathShape.SCATTER })

        val late = (35..40).map { generator.generate(it, 4000L + it) }
        assertEquals(PathShape.values().toSet(), late.map { it.pathShape }.toSet())
        PathShape.values().forEach { shape ->
            val level = late.first { it.pathShape == shape }
            assertTrue("$shape must pass its own perfect-bot validation", generator.simulatePerfectBot(level).completable)
        }
    }

    @Test fun `tightened difficulty snapshots are logged and exact`() {
        val at10 = generator.generate(10, 10L)
        val at30 = generator.generate(30, 30L)
        val at60 = generator.generate(60, 60L)

        println("difficulty snapshot level=10 perfectWindowRatio=${at10.perfectWindowRatio} oscillationSpeed=${at10.oscillationSpeed}")
        println("difficulty snapshot level=30 perfectWindowRatio=${at30.perfectWindowRatio} oscillationSpeed=${at30.oscillationSpeed}")
        println("difficulty snapshot level=60 perfectWindowRatio=${at60.perfectWindowRatio} oscillationSpeed=${at60.oscillationSpeed}")
        assertEquals(0.20f - 9f * (0.12f / 29f), at10.perfectWindowRatio, 0.000001f)
        assertEquals(0.08f, at30.perfectWindowRatio, 0.000001f)
        assertEquals(0.06f, at60.perfectWindowRatio, 0.000001f)
        assertEquals(0.0021f, at10.oscillationSpeed, 0.000001f)
        assertEquals(0.0021f * (1f + 14f * 0.027f), at30.oscillationSpeed, 0.000001f)
        assertEquals(0.0021f * (1f + 44f * 0.027f), at60.oscillationSpeed, 0.000001f)
    }

    @Test fun `dead pegs and branches start at tightened levels`() {
        assertEquals(0, generator.generate(14, 14L).deadPegs.size)
        val level15 = generator.generate(15, 15L)
        assertEquals(level15.pegs.size / 10, level15.deadPegs.size)
        assertTrue(generator.generate(34, 34L).branchPoints.isEmpty())
        assertTrue(generator.generate(35, 35L).branchPoints.isNotEmpty())
    }

    @Test fun `free play memory caps stored pegs and segments under fixed limit after generating 500 pegs`() {
        val state = GameState(360f, 800f)
        state.resetRun(PlayMode.FREE_PLAY)
        var totalGenerated = state.pegs.size
        while (totalGenerated < 500) {
            val from = state.previousPeg()
            val to = state.currentPeg()
            state.segments.add(StringSegment(from, to, ImpactType.PERFECT, 0f))
            if (state.targetPegIndex >= state.completionTarget) {
                state.targetPegIndex++
                totalGenerated += state.appendFreePlayChunk()
            } else {
                state.targetPegIndex++
            }
            state.pruneFreePlayMemory()
        }
        println("[FreePlayMemory] Total generated: $totalGenerated pegs | Stored pegs: ${state.pegs.size} | Stored segments: ${state.segments.size} | Stored dead pegs: ${state.deadPegs.size}")
        assertTrue("Stored pegs (${state.pegs.size}) must stay under fixed limit (100)", state.pegs.size < 100)
        assertTrue("Stored segments (${state.segments.size}) must stay under fixed limit (50)", state.segments.size < 50)
        assertTrue("Stored dead pegs (${state.deadPegs.size}) must stay under fixed limit (20)", state.deadPegs.size < 20)
    }
}
