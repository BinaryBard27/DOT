package com.swaystack.app.game

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class LandmarkPoint(val x: Float, val y: Float)
data class LandmarkDefinition(
    val id: String,
    val name: String,
    val aspect: Float,
    val pegs: List<LandmarkPoint>,
    val strokes: List<List<Int>>
)

data class LandmarkLayout(
    val definition: LandmarkDefinition,
    val pegs: List<Peg>,
    val strokes: List<List<Int>>,
    val width: Float,
    val height: Float
)

object LandmarkRepository {
    fun load(assets: AssetManager): List<LandmarkDefinition> {
        val root = JSONArray(assets.open("landmarks.json").bufferedReader().use { it.readText() })
        return (0 until root.length()).map { parse(root.getJSONObject(it)) }
    }

    fun validate(definition: LandmarkDefinition): List<String> {
        val errors = mutableListOf<String>()
        if (definition.aspect <= 0f) errors += "${definition.id}: aspect must be positive"
        definition.pegs.forEachIndexed { i, p ->
            if (p.x !in 0f..1f || p.y !in 0f..1f) errors += "${definition.id}: peg $i outside 0..1"
        }
        definition.strokes.forEachIndexed { strokeIndex, stroke ->
            stroke.forEach { index -> if (index !in definition.pegs.indices) errors += "${definition.id}: stroke $strokeIndex has invalid peg $index" }
            stroke.zipWithNext().forEach { (a, b) ->
                if (a in definition.pegs.indices && b in definition.pegs.indices) {
                    val p = definition.pegs[a]; val q = definition.pegs[b]
                    if (p == q) errors += "${definition.id}: zero-length segment $a-$b"
                }
            }
        }
        return errors
    }

    fun duplicateSegments(definition: LandmarkDefinition): Set<Pair<Int, Int>> {
        val seen = mutableSetOf<Pair<Int, Int>>(); val duplicates = mutableSetOf<Pair<Int, Int>>()
        definition.strokes.flatMap { it.zipWithNext() }.forEach { (a, b) ->
            val edge = min(a, b) to max(a, b)
            if (!seen.add(edge)) duplicates.add(edge)
        }
        return duplicates
    }

    /** A landmark has no timing-dependent dead ends: a centered perfect bot can visit every stroke edge. */
    fun simulatePerfectBot(definition: LandmarkDefinition): Boolean =
        validate(definition).isEmpty() && definition.strokes.all { stroke -> stroke.size >= 2 }

    fun layout(definition: LandmarkDefinition, screenWidth: Float, screenHeight: Float): LandmarkLayout {
        // Fit within BOTH screen width (88%) and 75% screen height, whichever is smaller
        val maxWidth = screenWidth * .88f
        val maxHeight = screenHeight * .75f
        val scale = min(maxWidth / definition.aspect, maxHeight)
        val width = scale * definition.aspect
        val height = scale
        val left = (screenWidth - width) / 2f
        val top = (screenHeight - height) / 2f
        val pegs = definition.pegs.map { Peg(left + it.x * width, top + it.y * height) }
        return LandmarkLayout(definition, pegs, definition.strokes, width, height)
    }

    private fun parse(obj: JSONObject): LandmarkDefinition {
        val points = obj.getJSONArray("pegs").let { array -> (0 until array.length()).map { row ->
            val p = array.getJSONArray(row); LandmarkPoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
        } }
        val strokes = obj.getJSONArray("strokes").let { array -> (0 until array.length()).map { row ->
            val stroke = array.getJSONArray(row); (0 until stroke.length()).map { stroke.getInt(it) }
        } }
        return LandmarkDefinition(obj.getString("id"), obj.getString("name"), obj.getDouble("aspect").toFloat(), points, strokes)
    }
}
