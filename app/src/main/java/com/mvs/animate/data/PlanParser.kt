package com.mvs.animate.data

import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.MotionKeyframe
import com.mvs.animate.model.MotionTrack
import com.mvs.animate.model.NativeEffectRecipe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extracts the structured edit-plan JSON from an OpenCode response.
 * Deliberately avoids regex: Android's java.util.regex rejected the previous
 * Markdown-fence pattern on some devices, aborting the entire AI pipeline.
 */
object PlanParser {
    data class Cut(
        val atMs: Long,
        val durationMs: Long,
        val visual: String,
        val text: String,
        val effect: String,
        val animation: ClipAnimation? = null,
        val textAnimation: ClipAnimation? = null,
    )

    fun parseCuts(response: String): List<Cut> {
        for (root in parseRootCandidates(response)) {
            val cuts = root.optJSONArray("cuts") ?: continue
            val parsed = (0 until cuts.length()).mapNotNull { index ->
                val item = cuts.optJSONObject(index) ?: return@mapNotNull null
                Cut(
                    atMs = item.optLong("atMs", index * 2_000L).coerceAtLeast(0L),
                    durationMs = item.optLong("durationMs", 2_000L).coerceIn(250L, 60_000L),
                    visual = item.optString("visual", "AI suggested visual").take(500),
                    text = item.optString("text", "").take(240),
                    effect = item.optString("effect", "").take(160),
                    animation = parseAnimation(item.optJSONObject("animation")),
                    textAnimation = parseAnimation(item.optJSONObject("textAnimation")),
                )
            }
            if (parsed.isNotEmpty()) return parsed.take(12)
        }
        return emptyList()
    }

    /** Short user-facing proposal title; never dumps the model's raw JSON into the UI. */
    fun summary(response: String, cuts: List<Cut>): String {
        val root = parseRootCandidates(response).firstOrNull { it.has("cuts") }
        val title = root?.optString("title")?.trim()?.takeIf { it.isNotBlank() }
        val overview = root?.optString("overview")?.trim()?.takeIf { it.isNotBlank() }
        val headline = title ?: overview ?: "AI edit plan"
        if (cuts.isEmpty()) return "$headline\nNo valid cuts were found in the model response."
        val visualLines = cuts.take(5).mapIndexed { index, cut ->
            "${index + 1}. ${cut.visual.take(110)}" + if (cut.text.isNotBlank()) " · ${cut.text}" else ""
        }
        return buildString {
            append(headline)
            append("\n")
            append("${cuts.size} cut(s) · ready to apply to the timeline")
            visualLines.forEach { append("\n"); append(it) }
        }
    }

    fun parseSearchQueries(response: String): List<String> {
        for (root in parseRootCandidates(response)) {
            val array = root.optJSONArray("searchQueries") ?: continue
            val queries = (0 until array.length()).map { array.optString(it).trim() }
                .filter { it.isNotBlank() }.distinct().take(8)
            if (queries.isNotEmpty()) return queries
        }
        return emptyList()
    }

    private fun parseRootCandidates(response: String): List<JSONObject> {
        if (response.isBlank()) return emptyList()
        val candidates = linkedSetOf<String>()

        // Prefer fenced JSON blocks (the model sometimes adds prose before/after them).
        var cursor = 0
        while (cursor < response.length) {
            val fenceStart = response.indexOf("```", cursor)
            if (fenceStart < 0) break
            val bodyStart = response.indexOf('\n', fenceStart + 3)
            if (bodyStart < 0) break
            val fenceEnd = response.indexOf("```", bodyStart + 1)
            if (fenceEnd < 0) break
            val body = response.substring(bodyStart + 1, fenceEnd)
            collectBalancedObjects(body, candidates)
            cursor = fenceEnd + 3
        }

        // Also accept plain JSON and responses with no Markdown fences.
        collectBalancedObjects(response, candidates)

        return candidates.mapNotNull { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }.filter { it.has("cuts") || it.has("searchQueries") || it.has("plan") || it.has("data") }
            .map { root ->
                root.optJSONObject("plan") ?: root.optJSONObject("data") ?: root
            }
    }

    /** Balanced-brace scanner that respects JSON strings and escaped quotes. */
    private fun collectBalancedObjects(text: String, output: MutableSet<String>) {
        if (text.length > MAX_RESPONSE_CHARS) return
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (index in text.indices) {
            val c = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        output += text.substring(start, index + 1)
                        start = -1
                        if (output.size >= MAX_CANDIDATES) return
                    }
                }
            }
        }
    }

    private val allowedProperties = setOf("scale", "rotation", "opacity", "position_x", "position_y")
    private val allowedEasing = setOf("linear", "ease_in", "ease_out", "ease_in_out", "hold")

    private fun parseAnimation(json: JSONObject?): ClipAnimation? {
        if (json == null) return null
        val rawTracks = json.optJSONArray("tracks") ?: JSONArray()
        val tracks = (0 until rawTracks.length()).mapNotNull trackLoop@ { index ->
            val track = rawTracks.optJSONObject(index) ?: return@trackLoop null
            val property = track.optString("property").lowercase().trim()
            if (property !in allowedProperties) return@trackLoop null
            val rawFrames = track.optJSONArray("keyframes") ?: return@trackLoop null
            val frames = (0 until rawFrames.length()).mapNotNull frameLoop@ { frameIndex ->
                val frame = rawFrames.optJSONObject(frameIndex) ?: return@frameLoop null
                val t = frame.optDouble("t", Double.NaN).toFloat()
                val v = frame.optDouble("v", Double.NaN).toFloat()
                if (!t.isFinite() || !v.isFinite()) return@frameLoop null
                val boundedValue = when (property) {
                    "scale" -> v.coerceIn(0.05f, 8f)
                    "rotation" -> v.coerceIn(-720f, 720f)
                    "opacity" -> v.coerceIn(0f, 1f)
                    "position_x", "position_y" -> v.coerceIn(-1f, 1f)
                    else -> v
                }
                val easing = frame.optString("easing", "linear").lowercase().trim()
                    .takeIf { it in allowedEasing } ?: "linear"
                MotionKeyframe(t.coerceIn(0f, 1f), boundedValue, easing)
            }.sortedBy { it.t }.distinctBy { it.t }.take(24)
            if (frames.size < 2) null else MotionTrack(property, frames)
        }.distinctBy { it.property }.take(5)
        val rawEffects = json.optJSONArray("effects")
        val effects = if (rawEffects == null) emptyList() else (0 until rawEffects.length())
            .map { rawEffects.optString(it).trim().take(48) }
            .filter { it.isNotBlank() }.distinct().take(8)
        val nativeEffects = parseNativeEffects(json.optJSONArray("nativeEffects"))
        if (tracks.isEmpty() && effects.isEmpty() && nativeEffects.isEmpty()) return null
        return ClipAnimation(
            name = json.optString("name", "AI Motion Recipe").take(80),
            description = json.optString("description", "AI-generated keyframe animation").take(240),
            effects = effects,
            tracks = tracks,
            nativeEffects = nativeEffects,
        )
    }

    private fun parseNativeEffects(array: JSONArray?): List<NativeEffectRecipe> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            val spec = AlightMotionEffectCatalog.find(id) ?: return@mapNotNull null
            val rawParams = item.optJSONArray("parameters") ?: JSONArray()
            val params = (0 until rawParams.length()).mapNotNull paramLoop@ { paramIndex ->
                val raw = rawParams.optJSONObject(paramIndex) ?: return@paramLoop null
                val name = raw.optString("name").trim()
                val rawFrames = raw.optJSONArray("keyframes")
                val frames = if (rawFrames == null) emptyList() else (0 until rawFrames.length()).mapNotNull frameLoop@ { frameIndex ->
                    val frame = rawFrames.optJSONObject(frameIndex) ?: return@frameLoop null
                    val t = frame.optDouble("t", Double.NaN).toFloat()
                    val value = frame.optDouble("v", Double.NaN).toFloat()
                    if (!t.isFinite() || !value.isFinite()) return@frameLoop null
                    MotionKeyframe(
                        t.coerceIn(0f, 1f), value,
                        frame.optString("easing", "linear").takeIf { it in allowedEasing } ?: "linear",
                    )
                }
                AlightMotionEffectCatalog.normalizeParameter(id, name, raw.optString("value", ""), frames)
            }.distinctBy { it.name }.take(16)
            val provided = params.associateBy { it.name }
            val completeParams = spec.parameters.map { parameter ->
                provided[parameter.name] ?: AlightMotionEffectCatalog.normalizeParameter(
                    id, parameter.name, parameter.defaultValue, emptyList()
                )!!
            }
            NativeEffectRecipe(id, item.optBoolean("locallyApplied", true), completeParams)
        }.distinctBy { it.id }.take(5)
    }

    private const val MAX_RESPONSE_CHARS = 1_000_000
    private const val MAX_CANDIDATES = 32
}
