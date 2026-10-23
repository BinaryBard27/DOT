package com.swaystack.app.game

enum class GameStateEnum { HOME, PLAYING, GAME_OVER, LEVEL_COMPLETE }
enum class BlockPhase { WAITING_FOR_TAP, PRE_DROP, DROPPING, FALLING_AFTER_MISS, RESPAWNING }
enum class ImpactType { PERFECT, GOOD, MISS }

data class Peg(val x: Float, val y: Float, val dead: Boolean = false)
data class StringSegment(val from: Peg, val to: Peg, val quality: ImpactType, val slack: Float, val strokeIndex: Int = -1)
data class BlockImpactEvent(val type: ImpactType, val positionX: Float, val positionY: Float, val strength: Float)
class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var alpha: Int)
class Cloud(var x: Float, var y: Float, val size: Float, val speed: Float)
class FloatingText(var x: Float, var y: Float, val text: String, var life: Float, val maxLife: Float)

class GameState(var screenWidth: Float, var screenHeight: Float, assets: android.content.res.AssetManager? = null) {
    var state = GameStateEnum.HOME
    var score = 0; var lives = 3; var multiplier = 1
    var currentComboStreak = 0; var highestComboStreak = 0; var highScore = 0; var isNewBest = false
    val pegs = mutableListOf<Peg>(); val deadPegs = mutableListOf<Peg>(); val segments = mutableListOf<StringSegment>()
    val particles = mutableListOf<Particle>(); val floatingTexts = mutableListOf<FloatingText>(); val clouds = mutableListOf<Cloud>()
    var targetPegIndex = 0; var movingX = 0f; var movingY = 0f
    var cameraXOffset = 0f; var cameraYOffset = 0f; var slack = 0f
    var flashTimer = 0f; var shakeTimer = 0f; var gameOverAlpha = 0f
    var lastImpact: ImpactType? = null; var showHint = true; var soundEnabled = true; var vibrationEnabled = true
    var startTimeMillis = System.currentTimeMillis(); var blockPhase = BlockPhase.WAITING_FOR_TAP
    var respawnDelayMs = 0f; var oscillationSpeed = 0.0021f
    var playMode = PlayMode.FREE_PLAY; var levelIndex = 1; var completionTarget = 0
    var perfectWindowRatio = .20f; var generatorSeed = 1L
    var pathShape = PathShape.STRAIGHT
    var landmark: LandmarkLayout? = null
    var landmarkStrokeIndex = 0
    var landmarkTargetInStroke = 0
    var landmarkFrays = 3
    var landmarkRevealTimer = 0f
    var landmarkRevealSweep = 0f
    val landmarkLitPegs = mutableSetOf<Int>()
    var debugShowAllPegs = false
    var landmarkAssets: android.content.res.AssetManager? = assets
    private var generator: LevelGenerator? = null

    fun configureForScreen() {
        if (clouds.isEmpty()) repeat(10) { clouds.add(Cloud((Math.random() * screenWidth).toFloat(), (Math.random() * screenHeight * .55f).toFloat(), (35 + Math.random() * 45).toFloat(), (.12 + Math.random() * .18).toFloat())) }
    }
    fun resetRun() {
        resetRun(playMode, levelIndex, generatorSeed)
    }
    fun resetRun(mode: PlayMode, requestedLevel: Int = 1, seed: Long = requestedLevel.toLong()) {
        playMode = mode; levelIndex = requestedLevel.coerceAtLeast(1); generatorSeed = seed
        state = GameStateEnum.PLAYING; score = 0; lives = 3; multiplier = 1; currentComboStreak = 0; highestComboStreak = 0
        targetPegIndex = 0; slack = 0f; cameraXOffset = 0f; cameraYOffset = 0f; segments.clear(); particles.clear(); floatingTexts.clear(); pegs.clear(); deadPegs.clear()
        landmark = null; landmarkStrokeIndex = 0; landmarkTargetInStroke = 0; landmarkFrays = 3; landmarkRevealTimer = 0f; landmarkRevealSweep = 0f; landmarkLitPegs.clear()
        generator = LevelGenerator(screenWidth, screenHeight)
        if (mode == PlayMode.CONSTELLATION_PATH && levelIndex in 1..3 && landmarkAssets != null) {
            val definitions = LandmarkRepository.load(landmarkAssets!!)
            val definition = definitions[levelIndex - 1]
            require(LandmarkRepository.validate(definition).isEmpty()) { LandmarkRepository.validate(definition).joinToString() }
            landmark = LandmarkRepository.layout(definition, screenWidth, screenHeight)
            pegs.addAll(landmark!!.pegs)
            landmarkLitPegs += landmark!!.strokes.first().first()
            landmarkTargetInStroke = if (landmark!!.strokes.first().size > 1) 1 else 0
            completionTarget = landmark!!.strokes.sumOf { (it.size - 1).coerceAtLeast(0) }
            oscillationSpeed = .0021f * (1f + ((levelIndex - 16).coerceAtLeast(0) * .027f))
            perfectWindowRatio = (0.20f - (levelIndex - 1) * (0.12f / 29f)).coerceIn(.06f, .20f)
            movingX = pegs.first().x; movingY = pegs.first().y
            startTimeMillis = System.currentTimeMillis(); blockPhase = BlockPhase.WAITING_FOR_TAP; lastImpact = null; gameOverAlpha = 0f
            return
        }
        val generated = if (mode == PlayMode.DAILY_CHALLENGE) generator!!.generateDaily(seed) else generator!!.generate(levelIndex, seed)
        pegs.addAll(generated.pegs); deadPegs.addAll(generated.deadPegs); completionTarget = pegs.lastIndex; oscillationSpeed = generated.oscillationSpeed; perfectWindowRatio = generated.perfectWindowRatio; pathShape = generated.pathShape
        movingY = pegs.first().y; movingX = pegs.first().x; startTimeMillis = System.currentTimeMillis(); blockPhase = BlockPhase.WAITING_FOR_TAP; lastImpact = null; gameOverAlpha = 0f
    }
    fun appendFreePlayChunk(): Int {
        val next = (generator ?: LevelGenerator(screenWidth, screenHeight)).generate(levelIndex + 1, generatorSeed + pegs.size)
        val lastY = pegs.lastOrNull()?.y ?: (screenHeight * 0.8f)
        val baseSpacing = (screenHeight * .13f).coerceAtLeast(90f)
        val shift = lastY - next.pegs.first().y - baseSpacing
        pegs.addAll(next.pegs.map { it.copy(y = it.y + shift) }); deadPegs.addAll(next.deadPegs.map { it.copy(y = it.y + shift) })
        levelIndex++; generatorSeed += pegs.size; completionTarget = pegs.lastIndex; perfectWindowRatio = next.perfectWindowRatio; oscillationSpeed = next.oscillationSpeed; pathShape = next.pathShape
        pruneFreePlayMemory()
        return next.pegs.size
    }
    fun pruneFreePlayMemory() {
        if (playMode != PlayMode.FREE_PLAY || pegs.isEmpty()) return
        val currentY = pegs.getOrNull(targetPegIndex.coerceIn(0, pegs.lastIndex))?.y ?: return
        val effectiveCameraY = if (cameraYOffset != 0f) cameraYOffset else (screenHeight * 0.60f - currentY).coerceAtLeast(0f)
        val cutoffY = screenHeight * 4f - effectiveCameraY

        var pruneCount = 0
        while (pruneCount < targetPegIndex && pegs[pruneCount].y > cutoffY) {
            pruneCount++
        }
        if (pruneCount > 0) {
            pegs.subList(0, pruneCount).clear()
            targetPegIndex -= pruneCount
            completionTarget -= pruneCount
        }
        deadPegs.removeAll { it.y > cutoffY }
        segments.removeAll { it.to.y > cutoffY }
    }
    fun currentPeg(): Peg = pegs[targetPegIndex.coerceIn(0, pegs.lastIndex)]
    fun previousPeg(): Peg = if (targetPegIndex == 0) currentPeg() else pegs[targetPegIndex - 1]
    fun landmarkCurrentStroke(): List<Int> = landmark?.strokes?.getOrNull(landmarkStrokeIndex) ?: emptyList()
    fun landmarkPreviousPeg(): Peg = pegs[landmarkCurrentStroke()[landmarkTargetInStroke.coerceAtLeast(0)]]
    fun landmarkTargetPeg(): Peg = pegs[landmarkCurrentStroke()[landmarkTargetInStroke.coerceAtMost(landmarkCurrentStroke().lastIndex)]]
}
