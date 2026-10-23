package com.swaystack.app.game

import android.graphics.*
import kotlin.math.sin

/** Procedural grayscale renderer. Weight, fill, and opacity carry result quality. */
class GameRenderer {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private val bgPaint = Paint().apply { color = Color.rgb(250, 250, 250) }
    private val stringPath = Path()
    private val panelRect = RectF()

    fun draw(c: Canvas, s: GameState, _dtMs: Float) {
        c.drawRect(0f, 0f, s.screenWidth, s.screenHeight, bgPaint)
        if (s.state == GameStateEnum.HOME) { drawHome(c, s); return }
        c.save(); c.translate(s.cameraXOffset + if (s.shakeTimer > 0) sin(s.shakeTimer * 60) * 8 else 0f, s.cameraYOffset)
        s.segments.forEach { drawString(c, it, if (s.landmark != null && s.state == GameStateEnum.LEVEL_COMPLETE) 1.35f else 1f) }
        if (s.landmark != null) drawLandmark(c, s)
        else {
        s.deadPegs.filter { (it.y + s.cameraYOffset) > -100f && (it.y + s.cameraYOffset) < s.screenHeight + 100f }.forEach { drawDeadPeg(c, it) }
        s.pegs.drop(s.targetPegIndex.coerceAtMost(s.pegs.size)).take(12).forEach { drawPeg(c, it, false) }
        drawPeg(c, s.previousPeg(), true)
        if (s.flashTimer > 0f) drawPerfectFlash(c, s.previousPeg(), s.flashTimer)
        if (s.state == GameStateEnum.PLAYING) drawPulse(c, s.movingX, s.movingY)
        }
        s.particles.forEach { fill.color = Color.BLACK; fill.alpha = it.alpha; c.drawCircle(it.x, it.y, 3.5f, fill) }
        c.restore()
        text.color = Color.BLACK; text.textSize = 22f; c.drawText("TAUT", 24f, 42f, text)
        text.textSize = 42f; c.drawText("${s.score}", 24f, 88f, text)
        text.textSize = 18f; text.color = Color.DKGRAY; c.drawText("x${s.multiplier}  COMBO ${s.currentComboStreak}", 24f, 120f, text)
        val frays = if (s.landmark != null) s.landmarkFrays else s.lives
        for (i in 0 until 3) { fill.color = if (i < frays) Color.BLACK else Color.rgb(220, 220, 220); c.drawCircle(s.screenWidth - 36f - i * 34f, 36f, 11f, fill) }
        if (s.showHint && s.score == 0 && s.state == GameStateEnum.PLAYING) { text.textSize = 20f; text.textAlign = Paint.Align.CENTER; text.color = Color.DKGRAY; c.drawText("TAP WHEN THE PULSE MEETS THE PEG", s.screenWidth / 2, s.screenHeight * .82f, text); text.textAlign = Paint.Align.LEFT }
        if (s.state == GameStateEnum.LEVEL_COMPLETE && s.landmark != null) {
            text.textAlign = Paint.Align.CENTER; text.color = Color.BLACK; text.textSize = 28f
            c.drawText(s.landmark!!.definition.name, s.screenWidth / 2f, s.landmark!!.pegs.maxOf { it.y } + 58f, text)
            line.color = Color.BLACK; line.strokeWidth = 2f; panelRect.set(s.screenWidth * .30f, s.screenHeight * .84f, s.screenWidth * .70f, s.screenHeight * .92f); c.drawRoundRect(panelRect, 14f, 14f, line)
            text.textSize = 22f; c.drawText("NEXT", s.screenWidth / 2f, s.screenHeight * .895f, text); text.textAlign = Paint.Align.LEFT
        }
        if (s.state == GameStateEnum.GAME_OVER || (s.state == GameStateEnum.LEVEL_COMPLETE && s.landmark == null)) {
            fill.color = Color.WHITE; fill.alpha = 245; c.drawRect(0f, 0f, s.screenWidth, s.screenHeight, fill)
            line.color = Color.BLACK; line.alpha = 255; line.strokeWidth = 2f; panelRect.set(s.screenWidth * .08f, s.screenHeight * .34f, s.screenWidth * .92f, s.screenHeight * .66f); c.drawRoundRect(panelRect, 18f, 18f, line)
            text.textAlign = Paint.Align.CENTER; text.color = Color.BLACK; text.textSize = 34f; c.drawText(if (s.state == GameStateEnum.LEVEL_COMPLETE && s.landmark == null) "CONSTELLATION ${s.levelIndex} COMPLETE" else if (s.landmark == null) "RUN ENDED" else s.landmark!!.definition.name, s.screenWidth / 2, s.screenHeight / 2 - 24, text); text.textSize = 22f; c.drawText(if (s.state == GameStateEnum.LEVEL_COMPLETE) "Tap for the next level" else "Tap to tune again", s.screenWidth / 2, s.screenHeight / 2 + 28, text); text.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawHome(c: Canvas, s: GameState) {
        text.textAlign = Paint.Align.CENTER; text.color = Color.BLACK; text.textSize = 56f
        c.drawText("TAUT", s.screenWidth / 2f, s.screenHeight * .25f, text)
        text.textSize = 18f; text.color = Color.DKGRAY
        c.drawText("TAP TO TUNE THE STRING", s.screenWidth / 2f, s.screenHeight * .31f, text)
        fill.color = Color.WHITE; fill.alpha = 255
        line.color = Color.BLACK; line.strokeWidth = 2f
        val free = RectF(s.screenWidth * .12f, s.screenHeight * .43f, s.screenWidth * .88f, s.screenHeight * .52f)
        val path = RectF(s.screenWidth * .12f, s.screenHeight * .56f, s.screenWidth * .88f, s.screenHeight * .65f)
        c.drawRoundRect(free, 14f, 14f, fill); c.drawRoundRect(free, 14f, 14f, line)
        c.drawRoundRect(path, 14f, 14f, fill); c.drawRoundRect(path, 14f, 14f, line)
        text.color = Color.BLACK; text.textSize = 24f
        c.drawText("FREE PLAY", s.screenWidth / 2f, s.screenHeight * .485f, text)
        c.drawText("CONSTELLATION PATH", s.screenWidth / 2f, s.screenHeight * .605f, text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun drawString(c: Canvas, seg: StringSegment, weight: Float = 1f) {
        line.color = when (seg.quality) { ImpactType.PERFECT -> Color.BLACK; ImpactType.GOOD -> Color.rgb(125, 125, 125); ImpactType.MISS -> Color.rgb(205, 205, 205) }
        line.strokeWidth = when (seg.quality) { ImpactType.PERFECT -> 16f; ImpactType.GOOD -> 9f; ImpactType.MISS -> 6f } * weight
        line.pathEffect = if (seg.quality == ImpactType.MISS) DashPathEffect(floatArrayOf(8f, 10f), 0f) else null
        stringPath.reset(); stringPath.moveTo(seg.from.x, seg.from.y); val mid = (seg.from.x + seg.to.x) / 2; stringPath.quadTo(mid, (seg.from.y + seg.to.y) / 2 + seg.slack * 7f, seg.to.x, seg.to.y); c.drawPath(stringPath, line); line.pathEffect = null
    }
    private fun drawLandmark(c: Canvas, s: GameState) {
        val layout = s.landmark ?: return
        if (s.debugShowAllPegs) layout.pegs.forEachIndexed { i, p -> drawDebugPeg(c, p, i) }
        else s.landmarkLitPegs.forEach { index -> if (index in layout.pegs.indices) drawPeg(c, layout.pegs[index], true) }
        if (s.state == GameStateEnum.PLAYING) {
            val targetIndex = s.landmarkCurrentStroke().getOrNull(s.landmarkTargetInStroke)
            if (targetIndex != null) drawPeg(c, layout.pegs[targetIndex], false)
            drawPulse(c, s.movingX, s.movingY)
        }
        if (s.state == GameStateEnum.LEVEL_COMPLETE) {
            val alpha = (150 + 105 * s.landmarkRevealSweep).toInt().coerceIn(0, 255)
            layout.pegs.forEach { fill.color = Color.BLACK; fill.alpha = alpha; c.drawCircle(it.x, it.y, 23f, fill) }
            if (s.landmarkRevealSweep > 0f) drawRevealSweep(c, s, alpha)
        }
    }
    private fun drawRevealSweep(c: Canvas, s: GameState, alpha: Int) {
        val edges = s.landmark!!.strokes.flatMap { it.zipWithNext() }
        val count = (edges.size * s.landmarkRevealSweep).toInt().coerceAtMost(edges.size)
        line.color = Color.WHITE; line.alpha = alpha; line.strokeWidth = 5f
        edges.take(count).forEach { (a, b) -> c.drawLine(s.landmark!!.pegs[a].x, s.landmark!!.pegs[a].y, s.landmark!!.pegs[b].x, s.landmark!!.pegs[b].y, line) }
        line.alpha = 255
    }
    private fun drawDebugPeg(c: Canvas, p: Peg, index: Int) { line.color = Color.BLACK; line.strokeWidth = 3f; c.drawCircle(p.x, p.y, 18f, line); text.color = Color.BLACK; text.textSize = 14f; text.textAlign = Paint.Align.CENTER; c.drawText(index.toString(), p.x, p.y + 5f, text); text.textAlign = Paint.Align.LEFT }
    private fun drawPeg(c: Canvas, p: Peg, locked: Boolean) { if (locked) { fill.color = Color.BLACK; fill.alpha = 255; c.drawCircle(p.x, p.y, 23f, fill) } else { line.color = Color.rgb(190, 190, 190); line.strokeWidth = 3f; c.drawCircle(p.x, p.y, 18f, line) } }
    private fun drawDeadPeg(c: Canvas, p: Peg) { line.color = Color.rgb(205, 205, 205); line.strokeWidth = 3f; line.pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f); c.drawCircle(p.x, p.y, 17f, line); line.pathEffect = null }
    private fun drawPulse(c: Canvas, x: Float, y: Float) { fill.color = Color.BLACK; fill.alpha = 255; c.drawCircle(x, y, 25f, fill) }
    private fun drawPerfectFlash(c: Canvas, peg: Peg, timer: Float) { line.color = Color.WHITE; line.strokeWidth = 4f; line.alpha = (timer * 255).toInt().coerceIn(0, 255); c.drawCircle(peg.x, peg.y, 23f + (1f - timer) * 18f, line); line.alpha = 255 }
}
