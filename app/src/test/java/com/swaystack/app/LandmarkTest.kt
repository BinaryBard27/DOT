package com.swaystack.app

import com.swaystack.app.game.Peg
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LandmarkTest {
    @Test fun `three landmark structures are valid and solvable`() {
        val text = File("src/main/assets/landmarks.json").readText()
        val root = org.json.JSONArray(text)
        assertTrue(root.length() == 3)
        for (i in 0 until root.length()) {
            val obj = root.getJSONObject(i)
            val points = obj.getJSONArray("pegs")
            val strokes = obj.getJSONArray("strokes")
            for (p in 0 until points.length()) {
                val point = points.getJSONArray(p)
                assertTrue(point.getDouble(0) in 0.0..1.0)
                assertTrue(point.getDouble(1) in 0.0..1.0)
            }
            for (s in 0 until strokes.length()) {
                val stroke = strokes.getJSONArray(s)
                for (p in 0 until stroke.length()) assertTrue(stroke.getInt(p) in 0 until points.length())
                for (p in 0 until stroke.length() - 1) assertTrue(stroke.getInt(p) != stroke.getInt(p + 1))
            }
        }
    }

    @Test fun `landmark layout fits both supported portrait sizes`() {
        val json = org.json.JSONArray(File("src/main/assets/landmarks.json").readText())
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val aspect = obj.getDouble("aspect").toFloat()
            for ((w, h) in listOf(360f to 640f, 411f to 914f)) {
                val maxWidth = w * .88f
                val maxHeight = h * .75f
                val scale = minOf(maxWidth / aspect, maxHeight)
                val width = scale * aspect
                val height = scale
                val top = (h - height) / 2f
                val left = (w - width) / 2f

                // Fully inside screen bounds
                assertTrue("Width $width should fit in screen width $w", width <= maxWidth + 0.01f)
                assertTrue("Height $height should fit in 75% screen height $maxHeight", height <= maxHeight + 0.01f)
                assertTrue("Top $top must be >= 0", top >= 0f)
                assertTrue("Bottom ${top + height} must be <= screen height $h", top + height <= h + 0.01f)
                assertTrue("Left $left must be >= 0", left >= 0f)
                assertTrue("Right ${left + width} must be <= screen width $w", left + width <= w + 0.01f)
            }
        }
    }

    @Test fun `any two pegs in a landmark at 360x800 layout must be at least min distance apart`() {
        val json = org.json.JSONArray(File("src/main/assets/landmarks.json").readText())
        val screenWidth = 360f
        val screenHeight = 800f
        val pegRadius = 23f // Locked peg radius in GameRenderer (unlocked peg radius is 18f)
        val minDistance = 2 * pegRadius + 8f // 54 dp

        val failures = mutableListOf<String>()

        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val aspect = obj.getDouble("aspect").toFloat()

            val maxWidth = screenWidth * .88f
            val maxHeight = screenHeight * .75f
            val scale = minOf(maxWidth / aspect, maxHeight)
            val width = scale * aspect
            val height = scale
            val left = (screenWidth - width) / 2f
            val top = (screenHeight - height) / 2f

            val pointsArray = obj.getJSONArray("pegs")
            val pegs = (0 until pointsArray.length()).map { idx ->
                val pt = pointsArray.getJSONArray(idx)
                Peg(left + pt.getDouble(0).toFloat() * width, top + pt.getDouble(1).toFloat() * height)
            }

            for (a in pegs.indices) {
                for (b in a + 1 until pegs.size) {
                    val dx = pegs[a].x - pegs[b].x
                    val dy = pegs[a].y - pegs[b].y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist < minDistance) {
                        failures += "$name ($id): Peg $a and Peg $b distance = ${String.format(java.util.Locale.US, "%.2f", dist)} dp (minimum required: $minDistance dp)"
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            println("=== LANDMARK PEG DISTANCE FAILURES (threshold: $minDistance dp) ===")
            failures.forEach { println(" - $it") }
        }

        assertTrue(
            "Found ${failures.size} landmark peg pair(s) closer than $minDistance dp:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }
}
