package com.mvs.animate.data

import com.mvs.animate.model.NativeEffectParameter
import com.mvs.animate.model.NativeEffectRecipe

/**
 * Nine-effect conservative allow-list of Alight Motion effects whose scene IDs and parameter
 * names/types are documented by public schema research. Values are still user-editable
 * inside Alight Motion; the app deliberately does not ship effect binaries or shaders.
 */
object AlightMotionEffectCatalog {
    data class ParameterSpec(
        val name: String,
        val type: String,
        val defaultValue: String,
        val minimum: Double? = null,
        val maximum: Double? = null,
        val animatable: Boolean = false,
    )
    data class EffectSpec(val id: String, val name: String, val parameters: List<ParameterSpec>)

    private fun p(name: String, type: String, defaultValue: String, min: Double? = null, max: Double? = null, animatable: Boolean = false) =
        ParameterSpec(name, type, defaultValue, min, max, animatable)

    val effects: List<EffectSpec> = listOf(
        EffectSpec("com.alightcreative.effects.shake2", "Auto Shake / Shake 2", listOf(
            p("mag", "float", "50", 0.0, 500.0, true),
            p("freq", "float", "2.0", 0.0, 60.0, true),
            p("evolution", "float", "0", -360.0, 360.0, true),
            p("seed", "float", "0", 0.0, 1.0),
            p("angle", "float", "45.0", -360.0, 360.0, true),
            p("slack", "float", "0.25", 0.0, 1.0),
            p("zshake", "float", "0", -100.0, 100.0, true),
        )),
        EffectSpec("com.alightcreative.effects.gaussianblur", "Gaussian Blur", listOf(
            p("strength", "float", "0.15", 0.0, 2.0, true),
        )),
        EffectSpec("com.alightcreative.effects.rgbsep", "RGB Split", listOf(
            p("strength", "float", "0.15", 0.0, 2.0, true),
            p("angle", "float", "0.0", -360.0, 360.0, true),
            p("centerChannel", "int", "1", 0.0, 2.0),
            p("mode", "int", "2", 0.0, 3.0),
        )),
        EffectSpec("com.alightcreative.effects.darkglow", "Dark Glow", listOf(
            p("strength", "float", "0.25", 0.0, 2.0, true),
            p("threshold", "float", "0.3", 0.0, 1.0, true),
            p("color", "color", "#FFFF5566"),
            p("blend", "float", "0.25", 0.0, 1.0, true),
            p("alpha", "float", "0.75", 0.0, 1.0, true),
        )),
        EffectSpec("com.alightcreative.glow", "Glow", listOf(
            p("radius", "float", "100.0", 0.0, 500.0, true),
            p("alpha", "float", "0.75", 0.0, 1.0, true),
            p("hardness", "float", "0.5", 0.0, 1.0, true),
            p("color", "color", "#FFFFFFAA"),
            p("blendMode", "int", "2", 0.0, 5.0),
        )),
        EffectSpec("com.alightcreative.effects.motionblur4", "Motion Blur", listOf(
            p("tune", "float", "1.0", 0.0, 4.0, true),
            p("usePos", "bool", "true"),
            p("useScale", "bool", "true"),
            p("useAngle", "bool", "true"),
        )),
        EffectSpec("com.alightcreative.effects.textprogress", "Text Progress / Typewriter", listOf(
            p("start", "float", "0.0", 0.0, 1.0, true),
            p("end", "float", "1.0", 0.0, 1.0, true),
            p("cursor", "int", "0", 0.0, 3.0),
            p("blink", "bool", "false"),
        )),
        EffectSpec("com.alightcreative.effects.pulseopacity", "Pulse Opacity", listOf(
            p("freq", "float", "2.0", 0.0, 60.0, true),
            p("strength", "float", "1.0", 0.0, 1.0, true),
            p("phase", "float", "0.0", 0.0, 1.0, true),
            p("type", "int", "0", 0.0, 5.0),
        )),
        EffectSpec("com.alightcreative.effects.swing2", "Swing 2", listOf(
            p("freq", "float", "2.0", 0.0, 60.0, true),
            p("a1", "float", "-30.0", -180.0, 180.0, true),
            p("a2", "float", "30.0", -180.0, 180.0, true),
            p("phase", "float", "0.0", -360.0, 360.0, true),
            p("type", "int", "0", 0.0, 5.0),
        )),
    )

    private val byId = effects.associateBy { it.id }
    fun find(id: String): EffectSpec? = byId[id]

    fun defaultRecipe(id: String): NativeEffectRecipe? {
        val spec = find(id) ?: return null
        return NativeEffectRecipe(id, parameters = spec.parameters.map {
            NativeEffectParameter(it.name, it.type, it.defaultValue)
        })
    }

    fun presetRecipe(presetId: String): NativeEffectRecipe? = when (presetId) {
        "shake_soft" -> defaultRecipe("com.alightcreative.effects.shake2")?.withValues(mapOf("mag" to "12", "freq" to "2.2", "angle" to "0"))
        "shake_hard" -> defaultRecipe("com.alightcreative.effects.shake2")?.withValues(mapOf("mag" to "32", "freq" to "3.5", "angle" to "0"))
        "rgb_echo" -> defaultRecipe("com.alightcreative.effects.rgbsep")?.withValues(mapOf("strength" to "0.28", "angle" to "0"))
        "glow" -> defaultRecipe("com.alightcreative.glow")?.withValues(mapOf("radius" to "80", "alpha" to "0.65", "hardness" to "0.45"))
        "motion_blur" -> defaultRecipe("com.alightcreative.effects.motionblur4")
        else -> null
    }

    private fun NativeEffectRecipe.withValues(values: Map<String, String>): NativeEffectRecipe = copy(
        parameters = parameters.map { param -> param.copy(value = values[param.name] ?: param.value) }
    )

    /** Validate a model-supplied parameter against the known schema. */
    fun normalizeParameter(effectId: String, name: String, rawValue: String, rawKeyframes: List<com.mvs.animate.model.MotionKeyframe>): NativeEffectParameter? {
        val spec = find(effectId)?.parameters?.firstOrNull { it.name == name } ?: return null
        if (rawKeyframes.isNotEmpty() && !spec.animatable) return null
        val value = when (spec.type) {
            "float", "int" -> {
                val numeric = rawValue.toDoubleOrNull()?.takeIf { it.isFinite() } ?: spec.defaultValue.toDoubleOrNull() ?: 0.0
                val bounded = numeric.coerceIn(spec.minimum ?: -10_000.0, spec.maximum ?: 10_000.0)
                if (spec.type == "int") bounded.toInt().toString() else bounded.toString()
            }
            "bool" -> when (rawValue.lowercase()) { "true", "1" -> "true"; "false", "0" -> "false"; else -> spec.defaultValue }
            "color" -> rawValue.uppercase().takeIf { it.matches(Regex("#[0-9A-F]{8}")) } ?: spec.defaultValue
            else -> spec.defaultValue
        }
        val frames = if (spec.animatable) rawKeyframes.sortedBy { it.t }.distinctBy { it.t }.take(24).map { frame ->
            frame.copy(
                t = frame.t.coerceIn(0f, 1f),
                value = frame.value.coerceIn(spec.minimum?.toFloat() ?: -10_000f, spec.maximum?.toFloat() ?: 10_000f),
                easing = frame.easing.takeIf { it in setOf("linear", "ease_in", "ease_out", "ease_in_out", "hold") } ?: "linear",
            )
        } else emptyList()
        if (frames.size == 1) return null
        return NativeEffectParameter(spec.name, spec.type, value, frames)
    }
}
