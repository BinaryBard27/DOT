package com.swaystack.app.game

import kotlin.math.abs
import kotlin.math.sin
import java.util.Random

enum class PlayMode { FREE_PLAY, CONSTELLATION_PATH, DAILY_CHALLENGE }
enum class PathShape { STRAIGHT, SINE_WAVE, ZIGZAG, ARC, STAIRCASE, SCATTER }

data class BranchPoint(val pegIndex: Int, val alternativeOffsets: List<Float>)

data class GeneratedLevel(
    val levelIndex: Int,
    val seed: Long,
    val pegs: List<Peg>,
    val deadPegs: List<Peg>,
    val branchPoints: List<BranchPoint>,
    val perfectWindowRatio: Float,
    val oscillationSpeed: Float,
    val pathShape: PathShape
)

data class SolvabilityResult(val completable: Boolean, val reason: String? = null)

/** Deterministic, data-only level construction. Rendering and gameplay never hand-place level pegs. */
class LevelGenerator(private val width: Float, private val height: Float) {
    companion object {
        const val DAILY_SEED_SALT = 0x54415554L // "TAUT"
        private const val MAX_RETRIES = 24
    }

    fun generate(levelIndex: Int, seed: Long = levelIndex.toLong()): GeneratedLevel {
        require(levelIndex >= 1) { "levelIndex must be positive" }
        var candidateSeed = seed
        repeat(MAX_RETRIES) {
            val candidate = generateCandidate(levelIndex, candidateSeed)
            if (validate(candidate).completable) return candidate
            candidateSeed += -7046029254386353131L
        }
        error("Unable to generate a completable level after $MAX_RETRIES attempts")
    }

    fun generateDaily(dayNumber: Long): GeneratedLevel = generate(1, dayNumber xor DAILY_SEED_SALT)

    /** Runs the deterministic center-of-window bot used as the ship gate. */
    fun simulatePerfectBot(level: GeneratedLevel): SolvabilityResult = validate(level)

    fun validate(level: GeneratedLevel): SolvabilityResult {
        if (level.pegs.size !in 5..40) return SolvabilityResult(false, "peg count outside 5..40")
        if (level.perfectWindowRatio !in 0.06f..0.20f) return SolvabilityResult(false, "perfect window outside floor/ceiling")
        if (level.pegs.any { it.dead || !it.x.isFinite() || !it.y.isFinite() }) return SolvabilityResult(false, "main path contains a dead or invalid peg")
        // Vertical layout: each successive peg must be higher (smaller Y = up on screen).
        if (level.pegs.zipWithNext().any { (a, b) -> b.y >= a.y }) return SolvabilityResult(false, "main path is not traversable upward")
        val deadExpected = if (level.levelIndex < 15) 0 else level.pegs.size / 10
        if (level.deadPegs.size != deadExpected) return SolvabilityResult(false, "dead peg count does not match stage")
        validateShape(level)?.let { return SolvabilityResult(false, it) }
        // The perfect bot swings horizontally and is always centred on the peg's X; it can
        // always reach any peg placed near screen centre, so no per-peg spacing check is needed.
        return SolvabilityResult(true)
    }

    private fun generateCandidate(level: Int, seed: Long): GeneratedLevel {
        val random = Random(seed)
        val count = (5 + ((level - 1).coerceIn(0, 29) * 35 / 29)).coerceIn(5, 40)
        val pathShape = stagedShape(level)
        val ratio = when {
            level <= 30 -> 0.20f - (level - 1) * (0.12f / 29f)
            level <= 60 -> 0.08f - (level - 30) * (0.02f / 30f)
            else -> 0.06f
        }.coerceIn(0.06f, 0.20f)
        val spacingMultiplier = 1f + (level - 1).coerceAtMost(50) * .008f
        // Vertical layout: peg spacing is in Y (upward). Base it on screen height.
        val baseSpacing = (height * .13f).coerceAtLeast(90f) * spacingMultiplier
        val baseX = width / 2f
        // The previous curve used .018 per level after level 16. A .027 multiplier
        // is 50% steeper while retaining the same accessible early-game speed.
        val speed = .0021f * (1f + ((level - 16).coerceAtLeast(0) * .027f))
        val pegs = ArrayList<Peg>(count)
        var y = height * .80f   // first peg near the bottom
        val arcDirection = if (random.nextBoolean()) 1f else -1f
        val staircaseDirection = if (random.nextBoolean()) 1f else -1f
        repeat(count) { i ->
            if (i > 0) y -= baseSpacing * (.90f + random.nextFloat() * .20f) // move upward
            val progress = if (count == 1) 0f else i.toFloat() / (count - 1)
            // Path shape expressed as a horizontal (X) offset from screen centre.
            val xOffset = when (pathShape) {
                PathShape.STRAIGHT  -> 0f
                PathShape.SINE_WAVE -> (sin(i * .9) * width * .12f).toFloat()
                PathShape.ZIGZAG    -> if (i % 2 == 0) -width * .12f else width * .12f
                // A single quadratic bow: zero at both ends, one broad apex.
                PathShape.ARC       -> arcDirection * 4f * progress * (1f - progress) * width * .22f
                // Unlike zigzag, every step moves in the same horizontal direction.
                PathShape.STAIRCASE -> staircaseDirection * (progress * 2f - 1f) * width * .20f
                // Each peg samples its own X; no prior peg or curve is consulted.
                PathShape.SCATTER   -> (random.nextFloat() * 2f - 1f) * width * .25f
            }
            pegs += Peg((baseX + xOffset).coerceIn(width * .12f, width * .88f), y)
        }
        val deadCount = if (level < 15) 0 else pegs.size / 10
        // Dead pegs sit at the same Y as their reference peg but shifted horizontally,
        // so the horizontal swing can pass through them and trick the player.
        val dead = (0 until deadCount).map { n ->
            val target = pegs[(n * 7 + 2) % pegs.size]
            val side = if (n % 2 == 0) 1f else -1f
            Peg((target.x + width * .16f * side).coerceIn(width * .06f, width * .94f), target.y, dead = true)
        }
        val branchIndices = if (level < 35) emptyList() else {
            val candidates = pegs.indices.filter { it > 1 && it < pegs.lastIndex && random.nextFloat() < .08f }
            if (candidates.isNotEmpty()) candidates else listOf(pegs.size / 2)
        }
        val branches = branchIndices.map { BranchPoint(it, listOf(-width * .08f, width * .08f)) }
        if (level == 10 || level == 30 || level == 60) {
            println("[Taut] difficulty level=$level perfectWindowRatio=$ratio oscillationSpeed=$speed")
        }
        return GeneratedLevel(level, seed, pegs, dead, branches, ratio, speed, pathShape)
    }

    private fun stagedShape(level: Int): PathShape {
        val shapes = when {
            level < 11 -> arrayOf(PathShape.STRAIGHT, PathShape.SINE_WAVE, PathShape.ZIGZAG)
            level < 35 -> arrayOf(PathShape.STRAIGHT, PathShape.SINE_WAVE, PathShape.ZIGZAG, PathShape.ARC, PathShape.STAIRCASE)
            else -> PathShape.values()
        }
        return shapes[(level - 1) % shapes.size]
    }

    private fun validateShape(level: GeneratedLevel): String? {
        // In vertical layout path shapes express as X offsets; validate the X axis.
        val xs = level.pegs.map { it.x }
        val deltas = xs.zipWithNext { a, b -> b - a }
        val epsilonPx = 2f   // pixel tolerance for floating-point comparison
        return when (level.pathShape) {
            PathShape.STRAIGHT  -> if (xs.zipWithNext().any { abs(it.second - it.first) > epsilonPx }) "straight path X is not centred" else null
            PathShape.SINE_WAVE -> if (xs.distinct().size < 3) "sine path lacks X wave variation" else null
            PathShape.ZIGZAG    -> if (deltas.zipWithNext().any { it.first * it.second >= 0f }) "zigzag path X does not alternate" else null
            PathShape.ARC       -> {
                val interior = if (xs.size > 2) xs.subList(1, xs.lastIndex) else emptyList()
                val ends = (xs.first() + xs.last()) / 2f
                val oneSided = interior.isNotEmpty() &&
                    (interior.all { it > ends + epsilonPx } || interior.all { it < ends - epsilonPx })
                if (abs(xs.first() - xs.last()) > epsilonPx * 5f || !oneSided) "arc path X is not a single bow" else null
            }
            PathShape.STAIRCASE -> if (deltas.any { abs(it) < epsilonPx } ||
                deltas.zipWithNext().any { it.first * it.second <= 0f }) "staircase path X is not monotonic" else null
            PathShape.SCATTER   -> if (xs.distinct().size < (xs.size / 2).coerceAtLeast(3) ||
                deltas.none { it > 0f } || deltas.none { it < 0f }) "scatter path X is too uniform" else null
        }
    }
}
