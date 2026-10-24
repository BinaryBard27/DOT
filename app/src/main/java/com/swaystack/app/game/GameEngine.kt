package com.swaystack.app.game

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GameEngine(context: Context, val state: GameState, val sounds: SoundManager, val prefs: SharedPreferences) {
    private val vibrator: Vibrator? = try { if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator } catch (_: Exception) { null }

    fun handleInput() {
        if (state.state == GameStateEnum.GAME_OVER) { state.resetRun(); return }
        if (state.state == GameStateEnum.LEVEL_COMPLETE) {
            if (state.landmark != null && state.landmarkRevealSweep < 1f) return
            state.resetRun(PlayMode.CONSTELLATION_PATH, state.levelIndex + 1, state.levelIndex + 1L); return
        }
        if (state.state == GameStateEnum.HOME) state.resetRun()
        if (state.blockPhase == BlockPhase.WAITING_FOR_TAP) resolveTap()
    }

    fun update(dtMs: Float) {
        val dt = (dtMs.coerceIn(0f, 50f) / 16.666f).coerceAtLeast(.1f)
        if (state.state == GameStateEnum.GAME_OVER) { state.gameOverAlpha = (state.gameOverAlpha + .04f * dt).coerceAtMost(1f); return }
        if (state.state == GameStateEnum.LEVEL_COMPLETE && state.landmark != null) {
            state.landmarkRevealTimer += dtMs
            if (state.landmarkRevealTimer >= 400f) state.landmarkRevealSweep = ((state.landmarkRevealTimer - 400f) / 1200f).coerceIn(0f, 1f)
            return
        }
        if (state.state != GameStateEnum.PLAYING || state.pegs.isEmpty()) return
        state.flashTimer = (state.flashTimer - .05f * dt).coerceAtLeast(0f); state.shakeTimer = (state.shakeTimer - .08f * dt).coerceAtLeast(0f)
        state.clouds.forEach { it.x += it.speed * dt; if (it.x > state.screenWidth + 100) it.x = -100f }
        state.particles.removeAll { it.alpha <= 0 }; state.particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.vy += .08f * dt; it.alpha -= (7 * dt).toInt() }
        if (state.blockPhase == BlockPhase.RESPAWNING) { state.respawnDelayMs -= dtMs; if (state.respawnDelayMs <= 0) state.blockPhase = BlockPhase.WAITING_FOR_TAP; return }
        if (state.blockPhase == BlockPhase.WAITING_FOR_TAP) {
            val t = System.currentTimeMillis() - state.startTimeMillis + state.inputDelayOffsetMs
            if (state.landmark != null) updateLandmarkPulse(t) else {
                val peg = state.currentPeg()
                val requested = state.screenWidth * (.18f + state.slack * .012f).coerceAtMost(.32f)
                val amplitude = MotionBounds.safeAmplitude(peg.x, state.screenWidth, requested)
                state.movingX = MotionBounds.clampX(peg.x + amplitude * sin(t * state.oscillationSpeed * (1f + state.slack * .03f)), state.screenWidth); state.movingY = peg.y
            }
        }
        if (state.landmark == null) {
            val target = state.currentPeg()
            // In Free Play, camera scrolls up smoothly to follow current peg (keeps target peg at ~60% down the screen).
            val desiredCameraY = (state.screenHeight * .60f - target.y).coerceAtLeast(0f)
            state.cameraYOffset += (desiredCameraY - state.cameraYOffset) * (.06f * dt)
            state.cameraXOffset = 0f
            state.pruneFreePlayMemory()
        } else {
            state.cameraXOffset = 0f
            val layout = state.landmark!!
            // If the picture is taller than the screen, camera follows the current peg vertically.
            if (layout.height > state.screenHeight * .75f && state.state == GameStateEnum.PLAYING) {
                val target = state.landmarkTargetPeg()
                val desiredCameraY = (state.screenHeight * .50f - target.y)
                state.cameraYOffset += (desiredCameraY - state.cameraYOffset) * (.06f * dt)
            } else {
                state.cameraYOffset += (0f - state.cameraYOffset) * (.06f * dt)
            }
        }
    }

    private fun resolveTap() {
        if (state.landmark != null) { resolveLandmarkTap(); return }
        val target = state.currentPeg(); val distance = abs(state.movingX - target.x); val spacing = state.screenWidth * .34f
        val deadHit = state.deadPegs.filter { abs(it.y - target.y) < 20f }.minByOrNull { abs(it.x - state.movingX) }
        val perfectWindow = spacing * state.perfectWindowRatio
        val goodWindow = spacing * .48f
        val type = when {
            deadHit != null && abs(deadHit.x - state.movingX) <= perfectWindow -> ImpactType.MISS
            distance <= perfectWindow -> ImpactType.PERFECT
            distance <= goodWindow -> ImpactType.GOOD
            else -> ImpactType.MISS
        }
        val strength = (1f + state.slack * .1f).coerceAtMost(2f)
        dispatch(BlockImpactEvent(type, state.movingX, target.y, strength))
        state.lastImpact = type
        when (type) {
            ImpactType.PERFECT -> { state.segments.add(StringSegment(state.previousPeg(), target, type, 0f)); state.score += 10 * state.multiplier; state.multiplier++; state.currentComboStreak++; state.highestComboStreak = maxOf(state.highestComboStreak, state.currentComboStreak); advanceTarget(); state.flashTimer = 1f; burst(target, 20, 255) }
            ImpactType.GOOD -> { state.segments.add(StringSegment(state.previousPeg(), target, type, state.slack + .35f)); state.score += 5; state.multiplier = 1; state.currentComboStreak = 0; state.slack = (state.slack + .65f).coerceAtMost(12f); advanceTarget(); burst(target, 6, 150) }
            ImpactType.MISS -> { state.segments.add(StringSegment(state.previousPeg(), target, type, state.slack + .7f)); state.lives--; state.multiplier = 1; state.currentComboStreak = 0; state.slack = (state.slack + 1.2f).coerceAtMost(12f); state.shakeTimer = 1f; burst(target, 12, 105); if (state.lives <= 0) { state.state = GameStateEnum.GAME_OVER; state.gameOverAlpha = 0f } }
        }
        if (state.score > state.highScore) { state.highScore = state.score; state.isNewBest = true; prefs.edit().putInt("high_score", state.highScore).apply() }
        state.blockPhase = if (state.state == GameStateEnum.GAME_OVER) BlockPhase.FALLING_AFTER_MISS else BlockPhase.RESPAWNING; state.respawnDelayMs = 90f
    }

    private fun updateLandmarkPulse(t: Long) {
        val from = state.landmarkPreviousPeg(); val to = state.landmarkTargetPeg()
        val dx = to.x - from.x; val dy = to.y - from.y; val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val requested = state.screenWidth * (.18f + state.slack * .012f).coerceAtMost(.32f)
        val amplitude = MotionBounds.safeAmplitude(to.x, state.screenWidth, requested)
        val offset = amplitude * sin(t * state.oscillationSpeed * (1f + state.slack * .03f))
        state.movingX = MotionBounds.clampX(to.x + dx / length * offset, state.screenWidth); state.movingY = MotionBounds.clampY(to.y + dy / length * offset, state.screenHeight)
    }

    private fun resolveLandmarkTap() {
        val target = state.landmarkTargetPeg(); val from = state.landmarkPreviousPeg()
        val dx = target.x - from.x; val dy = target.y - from.y; val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val distance = abs((state.movingX - target.x) * dx / length + (state.movingY - target.y) * dy / length)
        val density = state.screenWidth / 360f
        val perfectWindow = (20f * density * state.difficulty.perfectWindowMultiplier).coerceIn(6f * density, 20f * density); val goodWindow = maxOf(28f * density, 42f * density) * GameConstants.MAX_GOOD_WIDENING
        val type = when { distance <= perfectWindow -> ImpactType.PERFECT; distance <= goodWindow -> ImpactType.GOOD; else -> ImpactType.MISS }
        dispatch(BlockImpactEvent(type, state.movingX, state.movingY, (1f + state.slack * .1f).coerceAtMost(2f)))
        state.lastImpact = type
        when (type) {
            ImpactType.PERFECT, ImpactType.GOOD -> {
                state.segments += StringSegment(from, target, type, if (type == ImpactType.GOOD) state.slack + .35f else 0f, state.landmarkStrokeIndex)
                state.landmarkLitPegs += state.landmarkCurrentStroke()[state.landmarkTargetInStroke]
                if (type == ImpactType.PERFECT) { state.score += 10 * state.multiplier; state.multiplier++; state.currentComboStreak++; state.highestComboStreak = maxOf(state.highestComboStreak, state.currentComboStreak); state.flashTimer = 1f; burst(target, 20, 255) }
                else { state.score += 5; state.multiplier = 1; state.currentComboStreak = 0; state.slack = (state.slack + .65f).coerceAtMost(12f); burst(target, 6, 150) }
                advanceLandmarkTarget()
            }
            ImpactType.MISS -> {
                state.landmarkFrays-- ; state.multiplier = 1; state.currentComboStreak = 0; state.slack = (state.slack + 1.2f).coerceAtMost(12f); state.shakeTimer = 1f; burst(target, 12, 105)
                if (state.landmarkFrays <= 0) restartLandmarkStroke()
            }
        }
        state.blockPhase = if (state.state == GameStateEnum.LEVEL_COMPLETE) BlockPhase.RESPAWNING else BlockPhase.RESPAWNING; state.respawnDelayMs = 90f
    }

    private fun advanceLandmarkTarget() {
        val stroke = state.landmarkCurrentStroke()
        if (state.landmarkTargetInStroke < stroke.lastIndex) { state.landmarkTargetInStroke++; return }
        if (state.landmarkStrokeIndex >= state.landmark!!.strokes.lastIndex) {
            state.dailyDay?.let { com.swaystack.app.DailyProgress.recordCompletion(prefs, it); state.dailyStreak = com.swaystack.app.DailyProgress.currentStreak(prefs) }
            state.state = GameStateEnum.LEVEL_COMPLETE; state.landmarkRevealTimer = 0f; return
        }
        state.landmarkStrokeIndex++; state.landmarkTargetInStroke = if (state.landmarkCurrentStroke().size > 1) 1 else 0; state.landmarkFrays = state.difficulty.frays; state.landmarkLitPegs += state.landmarkCurrentStroke().first(); state.flashTimer = .4f
    }

    private fun restartLandmarkStroke() {
        state.segments.removeAll { it.strokeIndex == state.landmarkStrokeIndex }
        state.landmarkLitPegs.clear()
        state.segments.forEach { state.landmarkLitPegs += state.landmark!!.pegs.indexOf(it.from); state.landmarkLitPegs += state.landmark!!.pegs.indexOf(it.to) }
        state.landmarkLitPegs += state.landmarkCurrentStroke().first(); state.landmarkTargetInStroke = if (state.landmarkCurrentStroke().size > 1) 1 else 0; state.landmarkFrays = state.difficulty.frays; state.slack = 0f
    }
    private fun advanceTarget() {
        if (state.targetPegIndex >= state.completionTarget) {
            if (state.playMode == PlayMode.FREE_PLAY) {
                state.targetPegIndex++
                state.appendFreePlayChunk()
            } else state.state = GameStateEnum.LEVEL_COMPLETE
        } else state.targetPegIndex++
        state.pruneFreePlayMemory()
    }
    private fun burst(peg: Peg, count: Int, alpha: Int) { repeat(count) { val a = Math.random() * Math.PI * 2; val v = 2 + Math.random() * 4; state.particles.add(Particle(peg.x, peg.y, (cos(a) * v).toFloat(), (sin(a) * v).toFloat(), alpha)) } }
    private fun dispatch(event: BlockImpactEvent) { sounds.onBlockImpact(event); if (!state.vibrationEnabled) return; try { if (Build.VERSION.SDK_INT >= 26) { vibrator?.vibrate(VibrationEffect.createOneShot(if (event.type == ImpactType.MISS) 130L else 24L, if (event.type == ImpactType.PERFECT) 220 else 150)) } else { @Suppress("DEPRECATION") vibrator?.vibrate(35L) } } catch (_: Exception) {} }
}
