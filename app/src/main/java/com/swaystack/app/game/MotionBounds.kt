package com.swaystack.app.game

import kotlin.math.min

object MotionBounds {
    const val SCREEN_MARGIN_DP = 8f

    fun safeAmplitude(anchorX: Float, screenWidth: Float, requested: Float, margin: Float = SCREEN_MARGIN_DP): Float =
        min(requested, min(anchorX - margin, screenWidth - margin - anchorX).coerceAtLeast(0f))

    fun clampX(x: Float, screenWidth: Float, margin: Float = SCREEN_MARGIN_DP): Float =
        x.coerceIn(margin, screenWidth - margin)

    fun clampY(y: Float, screenHeight: Float, margin: Float = SCREEN_MARGIN_DP): Float =
        y.coerceIn(margin, screenHeight - margin)
}
