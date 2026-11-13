package com.mvs.animate.data

import org.json.JSONObject
import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.MotionKeyframe
import com.mvs.animate.model.MotionTrack
import com.mvs.animate.model.NativeEffectRecipe

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
        val fenced = Regex("(?is)```(?:json)?\\s*(\\{.*?})\\s*```").findAll(response).lastOrNull()?.groupValues?.getOrNull(1)
        val candidates = listOfNotNull(fenced, extractObject(response))
        for (candidate in candidates) {
            runCatching {
                val root = JSONObject(candidate)
                val cuts = root.optJSONArray("cuts") ?: return@runCatching emptyList<Cut>()
                return (0 until cuts.length()).mapNotNull { index ->
                    val item = cuts.optJSONObject(index) ?: return@mapNotNull null
                    Cut(
                        atMs = item.optLong("atMs", index * 2_000L).coerceAtLeast(0L),
                        durationMs = item.optLong("durationMs", 2_000L).coerceIn(250L, 60_000L),
                        visual = item.optString("visual", "AI suggested visual"),
                        text = item.optString("text", ""),
                        effect = item.optString("effect", ""),
                        animation = parseAnimation(item.optJSONObject("animation")),
                        textAnimation = parseAnimation(item.optJSONObject("textAnimation")),
                    )
                }
            }.getOrNull()?.let { return it }
        }
        return emptyList()
    }


    private val allowedProperties = setOf("scale", "rotation", "opacity", "position_x", "position_y")
    private val allowedEasing = setOf("linear", "ease_in", "ease_out", "ease_in_out", "hold")

    private fun parseAnimation(json: JSONObject?): ClipAnimation? {
        if (json == null) return null
        val rawTracks = json.optJSONArray("tracks") ?: org.json.JSONArray()
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
                val easing = frame.optString("easing", "linear").lowercase().trim().takeIf { it in allowedEasing } ?: "linear"
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

    private fun parseNativeEffects(array: org.json.JSONArray?): List<NativeEffectRecipe> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            val spec = AlightMotionEffectCatalog.find(id) ?: return@mapNotNull null
            val rawParams = item.optJSONArray("parameters") ?: org.json.JSONArray()
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
            // Missing parameters use catalog defaults; this keeps the native effect valid.
            val provided = params.associateBy { it.name }
            val completeParams = spec.parameters.map { parameter ->
                provided[parameter.name] ?: AlightMotionEffectCatalog.normalizeParameter(id, parameter.name, parameter.defaultValue, emptyList())!!
            }
            NativeEffectRecipe(id, item.optBoolean("locallyApplied", true), completeParams)
        }.distinctBy { it.id }.take(5)
    }

    fun parseSearchQueries(response: String): List<String> {
        val candidate = Regex("(?is)```(?:json)?\\s*(\\{.*?})\\s*```").findAll(response).lastOrNull()?.groupValues?.getOrNull(1)
            ?: extractObject(response)
            ?: return emptyList()
        return runCatching {
            val array = JSONObject(candidate).optJSONArray("searchQueries") ?: return emptyList()
            (0 until array.length()).map { array.optString(it).trim() }
                .filter { it.isNotBlank() }.distinct().take(8)
        }.getOrDefault(emptyList())
    }

    private fun extractObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }
}
