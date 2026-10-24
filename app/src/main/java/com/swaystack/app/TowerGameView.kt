package com.swaystack.app

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.swaystack.app.game.GameEngine
import com.swaystack.app.game.GameRenderer
import com.swaystack.app.game.GameState
import com.swaystack.app.game.GameStateEnum
import com.swaystack.app.game.SoundManager
import com.swaystack.app.game.PlayMode

class TowerGameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private var thread: Thread? = null
    @Volatile private var isPlaying = false
    private val surfaceHolder: SurfaceHolder = holder

    private val gameState = GameState(0f, 0f, context.assets)
    private val soundManager = SoundManager()
    private val gameRenderer = GameRenderer()

    private val prefs = context.getSharedPreferences("TautPrefs", Context.MODE_PRIVATE)
    private val settings = SettingsStore(context)
    private val gameEngine = GameEngine(context, gameState, soundManager, prefs)

    init {
        surfaceHolder.addCallback(this)
        isFocusable = true
        gameState.highScore = prefs.getInt("high_score", 0)
        gameState.inputDelayOffsetMs = settings.inputDelayOffsetMs
        gameState.vibrationEnabled = settings.vibrationEnabled
        gameState.soundEnabled = settings.soundEnabled
        soundManager.enabled = settings.soundEnabled
        gameState.dailyStreak = DailyProgress.currentStreak(prefs)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameState.screenWidth = width.toFloat()
        gameState.screenHeight = height.toFloat()
        gameState.landmarkAssets = context.assets
        gameState.configureForScreen()
        gameState.resetRun()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        gameState.screenWidth = width.toFloat()
        gameState.screenHeight = height.toFloat()
        gameState.landmarkAssets = context.assets
        gameState.configureForScreen()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    fun pause() {
        isPlaying = false
        try { thread?.join(500) } catch (e: InterruptedException) { e.printStackTrace() }
        thread = null
        soundManager.release()
    }

    fun resume() {
        if (!isPlaying) {
            isPlaying = true
            thread = Thread(this).apply {
                name = "TautGameLoop"
                start()
            }
        }
    }

    /** Entry points used by the menu layer as Constellation Path is introduced. */
    fun startConstellationLevel(level: Int) {
        gameState.resetRun(PlayMode.CONSTELLATION_PATH, level)
    }

    fun startFreePlay() { gameState.resetRun(PlayMode.FREE_PLAY, 1, 1L) }

    fun startDailyChallenge(dayNumber: Long) {
        gameState.resetDailyChallenge(dayNumber)
    }

    /** Debug-only inspection hook: reveal every landmark peg and its data index. */
    fun setLandmarkDebugVisible(visible: Boolean) { gameState.debugShowAllPegs = visible }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only handle the primary pointer; ignore all secondary touch contacts so
        // accidental multi-touch during a run doesn't double-fire inputs.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (gameState.state == GameStateEnum.HOME) {
                if (event.y in height * .43f..height * .52f) startFreePlay()
                else if (event.y in height * .56f..height * .65f) startConstellationLevel(1)
                else if (event.y in height * .68f..height * .76f) startDailyChallenge(DailyChallenge.localDayIndex())
                return true
            }
            // Guard: don't route taps into gameplay during non-interactive states.
            gameEngine.handleInput()
        }
        return true
    }

    /**
     * Game loop: measures actual elapsed time per frame and sleeps only the remaining
     * budget so the update rate approximates 60 fps without a fixed 16 ms sleep that
     * ignores how long update+draw actually took.
     */
    override fun run() {
        var lastFrameTime = System.currentTimeMillis()
        while (isPlaying) {
            val frameStart = System.currentTimeMillis()
            val dtMs = (frameStart - lastFrameTime).toFloat()
            lastFrameTime = frameStart

            try {
                gameEngine.update(dtMs)
                drawFrame()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Sleep only the remaining budget so total ≈ 16 ms per frame.
            val elapsed = System.currentTimeMillis() - frameStart
            val remaining = 16L - elapsed
            if (remaining > 0) {
                try { Thread.sleep(remaining) } catch (_: InterruptedException) {}
            }
        }
    }

    private fun drawFrame() {
        if (!surfaceHolder.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                gameRenderer.draw(canvas, gameState, 0f)
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
