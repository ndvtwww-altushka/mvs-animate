package com.mvs.animate.data

import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.ClipKind
import com.mvs.animate.model.EditClip
import com.mvs.animate.model.EditProject
import com.mvs.animate.model.MotionKeyframe
import com.mvs.animate.model.MotionTrack
import com.mvs.animate.model.NativeEffectRecipe
import org.json.JSONArray
import org.json.JSONObject

/** Native, backwards-compatible MVS Animate JSON project format. */
object ProjectCodec {
    fun encode(project: EditProject): String {
        val clips = JSONArray()
        project.clips.forEach { clip ->
            clips.put(JSONObject()
                .put("id", clip.id).put("name", clip.name).put("kind", clip.kind.name)
                .put("uri", clip.uri).put("startMs", clip.startMs).put("durationMs", clip.durationMs)
                .put("sourceInMs", clip.sourceInMs).put("text", clip.text).put("scale", clip.scale)
                .put("rotation", clip.rotation).put("opacity", clip.opacity)
                .put("effects", JSONArray(clip.effects))
                .put("templateLayer", clip.templateLayer)
                .put("templateLayerId", clip.templateLayerId)
                .put("templateUri", clip.templateUri)
                .put("animation", encodeAnimation(clip.animation)))
        }
        val motionLibrary = JSONArray().apply { project.motionLibrary.forEach { put(encodeAnimation(it)) } }
        return JSONObject()
            .put("format", "mvs-animate-project")
            .put("version", 2)
            .put("title", project.title)
            .put("width", project.width).put("height", project.height).put("fps", project.fps)
            .put("background", project.background).put("clips", clips)
            .put("musicUri", project.musicUri).put("musicName", project.musicName)
            .put("musicStartMs", project.musicStartMs).put("musicEndMs", project.musicEndMs)
            .put("sourceSceneXml", project.sourceSceneXml)
            .put("motionLibrary", motionLibrary)
            .toString(2)
    }

    fun decode(json: String): EditProject {
        val root = JSONObject(json)
        require(root.optString("format") == "mvs-animate-project") { "Not an MVS Animate project" }
        val clipsJson = root.optJSONArray("clips") ?: JSONArray()
        val clips = (0 until clipsJson.length()).mapNotNull { i ->
            val item = clipsJson.optJSONObject(i) ?: return@mapNotNull null
            val effects = item.optJSONArray("effects") ?: JSONArray()
            EditClip(
                id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                name = item.optString("name", "Clip ${i + 1}"),
                kind = runCatching { ClipKind.valueOf(item.optString("kind", "UNKNOWN")) }.getOrDefault(ClipKind.UNKNOWN),
                uri = item.optString("uri").takeIf { it != "null" && it.isNotBlank() },
                startMs = item.optLong("startMs", 0L),
                durationMs = item.optLong("durationMs", 4_000L).coerceAtLeast(1L),
                sourceInMs = item.optLong("sourceInMs", 0L),
                text = item.optString("text", ""),
                scale = item.optDouble("scale", 1.0).toFloat(),
                rotation = item.optDouble("rotation", 0.0).toFloat(),
                opacity = item.optDouble("opacity", 1.0).toFloat(),
                effects = (0 until effects.length()).map { effects.optString(it) },
                templateLayer = item.optBoolean("templateLayer", false),
                templateLayerId = item.optString("templateLayerId").takeIf { it != "null" && it.isNotBlank() },
                templateUri = item.optString("templateUri").takeIf { it != "null" && it.isNotBlank() },
                animation = decodeAnimation(item.optJSONObject("animation")),
            )
        }
        val rawLibrary = root.optJSONArray("motionLibrary") ?: JSONArray()
        val library = (0 until rawLibrary.length()).mapNotNull { index -> decodeAnimation(rawLibrary.optJSONObject(index)) }
            .distinctBy { it.name.lowercase() }.take(100)
        return EditProject(
            title = root.optString("title", "Imported edit"),
            width = root.optInt("width", 1080).coerceIn(1, 8192),
            height = root.optInt("height", 1920).coerceIn(1, 8192),
            fps = root.optDouble("fps", 30.0).coerceIn(1.0, 240.0),
            background = root.optString("background", "#FF080A12"),
            clips = clips,
            musicUri = root.optString("musicUri").takeIf { it != "null" && it.isNotBlank() },
            musicName = root.optString("musicName", ""),
            musicStartMs = root.optLong("musicStartMs", 0L),
            musicEndMs = root.optLong("musicEndMs", 0L),
            sourceSceneXml = root.optString("sourceSceneXml").takeIf { it != "null" && it.isNotBlank() },
            motionLibrary = library,
        )
    }

    private fun encodeAnimation(animation: ClipAnimation?): JSONObject? = animation?.let {
        JSONObject()
            .put("name", it.name)
            .put("description", it.description)
            .put("effects", JSONArray(it.effects))
            .put("tracks", JSONArray().apply {
                it.tracks.forEach { track ->
                    put(JSONObject().put("property", track.property).put("keyframes", JSONArray().apply {
                        track.keyframes.forEach { frame ->
                            put(JSONObject().put("t", frame.t.toDouble()).put("v", frame.value.toDouble()).put("easing", frame.easing))
                        }
                    }))
                }
            })
            .put("nativeEffects", JSONArray().apply {
                it.nativeEffects.forEach { effect ->
                    put(JSONObject()
                        .put("id", effect.id)
                        .put("locallyApplied", effect.locallyApplied)
                        .put("parameters", JSONArray().apply {
                            effect.parameters.forEach { parameter ->
                                put(JSONObject()
                                    .put("name", parameter.name)
                                    .put("type", parameter.type)
                                    .put("value", parameter.value)
                                    .put("keyframes", JSONArray().apply {
                                        parameter.keyframes.forEach { frame ->
                                            put(JSONObject().put("t", frame.t.toDouble()).put("v", frame.value.toDouble()).put("easing", frame.easing))
                                        }
                                    }))
                            }
                        }))
                }
            })
    }

    private fun decodeAnimation(animation: JSONObject?): ClipAnimation? {
        if (animation == null) return null
        val rawTracks = animation.optJSONArray("tracks") ?: JSONArray()
        val tracks = (0 until rawTracks.length()).mapNotNull trackLoop@ { trackIndex ->
            val track = rawTracks.optJSONObject(trackIndex) ?: return@trackLoop null
            val property = track.optString("property")
            if (property !in setOf("scale", "rotation", "opacity", "position_x", "position_y")) return@trackLoop null
            val rawFrames = track.optJSONArray("keyframes") ?: JSONArray()
            val frames = (0 until rawFrames.length()).mapNotNull frameLoop@ { frameIndex ->
                val frame = rawFrames.optJSONObject(frameIndex) ?: return@frameLoop null
                val t = frame.optDouble("t", Double.NaN).toFloat()
                val value = frame.optDouble("v", Double.NaN).toFloat()
                if (!t.isFinite() || !value.isFinite()) return@frameLoop null
                val bounded = when (property) {
                    "scale" -> value.coerceIn(.05f, 8f)
                    "rotation" -> value.coerceIn(-720f, 720f)
                    "opacity" -> value.coerceIn(0f, 1f)
                    "position_x", "position_y" -> value.coerceIn(-1f, 1f)
                    else -> value
                }
                val easing = frame.optString("easing", "linear")
                    .takeIf { it in setOf("linear", "ease_in", "ease_out", "ease_in_out", "hold") } ?: "linear"
                MotionKeyframe(t.coerceIn(0f, 1f), bounded, easing)
            }.sortedBy { it.t }.distinctBy { it.t }.take(24)
            if (frames.size < 2) null else MotionTrack(property, frames)
        }.distinctBy { it.property }.take(5)
        val rawEffects = animation.optJSONArray("effects") ?: JSONArray()
        val effects = (0 until rawEffects.length()).map { rawEffects.optString(it).trim().take(48) }
            .filter { it.isNotBlank() }.distinct().take(8)
        val nativeEffects = decodeNativeEffects(animation.optJSONArray("nativeEffects"))
        if (tracks.isEmpty() && effects.isEmpty() && nativeEffects.isEmpty()) return null
        return ClipAnimation(
            name = animation.optString("name", "Imported motion").take(80),
            description = animation.optString("description", "").take(240),
            effects = effects,
            tracks = tracks,
            nativeEffects = nativeEffects,
        )
    }

    private fun decodeNativeEffects(raw: JSONArray?): List<NativeEffectRecipe> {
        if (raw == null) return emptyList()
        return (0 until raw.length()).mapNotNull effectLoop@ { effectIndex ->
            val item = raw.optJSONObject(effectIndex) ?: return@effectLoop null
            val id = item.optString("id")
            val spec = AlightMotionEffectCatalog.find(id) ?: return@effectLoop null
            val rawParams = item.optJSONArray("parameters") ?: JSONArray()
            val provided = (0 until rawParams.length()).mapNotNull paramLoop@ { paramIndex ->
                val parameter = rawParams.optJSONObject(paramIndex) ?: return@paramLoop null
                val name = parameter.optString("name")
                val rawFrames = parameter.optJSONArray("keyframes") ?: JSONArray()
                val frames = (0 until rawFrames.length()).mapNotNull frameLoop@ { frameIndex ->
                    val frame = rawFrames.optJSONObject(frameIndex) ?: return@frameLoop null
                    val t = frame.optDouble("t", Double.NaN).toFloat()
                    val v = frame.optDouble("v", Double.NaN).toFloat()
                    if (!t.isFinite() || !v.isFinite()) return@frameLoop null
                    MotionKeyframe(t.coerceIn(0f, 1f), v, frame.optString("easing", "linear"))
                }
                AlightMotionEffectCatalog.normalizeParameter(id, name, parameter.optString("value", ""), frames)
            }.associateBy { it.name }
            val parameters = spec.parameters.map { parameter ->
                provided[parameter.name] ?: AlightMotionEffectCatalog.normalizeParameter(id, parameter.name, parameter.defaultValue, emptyList())!!
            }
            NativeEffectRecipe(id, item.optBoolean("locallyApplied", true), parameters)
        }.distinctBy { it.id }.take(5)
    }
}
