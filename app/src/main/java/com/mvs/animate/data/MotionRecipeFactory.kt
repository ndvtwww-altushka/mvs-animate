package com.mvs.animate.data

import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.MotionKeyframe
import com.mvs.animate.model.MotionTrack

/** Safe starter recipes. AI can author any curve using the same small, validated motion DSL. */
object MotionRecipeFactory {
    fun fromPreset(id: String): ClipAnimation? {
        fun track(property: String, vararg points: Triple<Float, Float, String>) =
            MotionTrack(property, points.map { MotionKeyframe(it.first, it.second, it.third) })
        return when (id) {
            "velocity_ramp", "zoom_punch", "snap_zoom" -> ClipAnimation(
                name = if (id == "velocity_ramp") "Velocity Ramp" else if (id == "snap_zoom") "Snap Zoom" else "Zoom Punch",
                description = "Scale overshoot with a fast settle",
                effects = listOf("camera_zoom", "velocity"),
                tracks = listOf(track("scale", Triple(0f, .82f, "ease_in"), Triple(.18f, 1.18f, "ease_out"), Triple(.52f, 1f, "ease_in_out"))),
            )
            "whip_pan" -> ClipAnimation("Whip Pan", "Fast directional move and rotation settle", listOf("whip", "camera"), listOf(
                track("position_x", Triple(0f, -.55f, "ease_in"), Triple(.26f, .12f, "ease_out"), Triple(1f, 0f, "ease_out")),
                track("rotation", Triple(0f, -8f, "ease_in"), Triple(.24f, 4f, "ease_out"), Triple(.62f, 0f, "ease_out")),
                track("scale", Triple(0f, 1.08f, "ease_out"), Triple(1f, 1f, "linear")),
            ))
            "handheld", "shake_soft", "shake_hard" -> {
                val intensity = if (id == "shake_hard") 2.8f else 1f
                ClipAnimation("${if (id == "shake_hard") "Impact" else "Soft"} Handheld Shake", "Short irregular camera micro-movement", listOf("handheld", "shake"), listOf(
                    track("position_x", Triple(0f, -.025f * intensity, "linear"), Triple(.2f, .02f * intensity, "linear"), Triple(.43f, -.018f * intensity, "linear"), Triple(.68f, .014f * intensity, "linear"), Triple(1f, 0f, "ease_out")),
                    track("position_y", Triple(0f, .018f * intensity, "linear"), Triple(.27f, -.02f * intensity, "linear"), Triple(.51f, .016f * intensity, "linear"), Triple(.77f, -.01f * intensity, "linear"), Triple(1f, 0f, "ease_out")),
                    track("rotation", Triple(0f, -1.2f * intensity, "linear"), Triple(.24f, 1.4f * intensity, "linear"), Triple(.5f, -.9f * intensity, "linear"), Triple(.76f, .7f * intensity, "linear"), Triple(1f, 0f, "ease_out")),
                ), nativeEffects = listOfNotNull(AlightMotionEffectCatalog.presetRecipe(id)))
            }
            "bounce" -> ClipAnimation("Bounce", "Elastic overshoot and settle", listOf("elastic", "scale"), listOf(
                track("scale", Triple(0f, .72f, "ease_in"), Triple(.32f, 1.16f, "ease_out"), Triple(.55f, .96f, "ease_in_out"), Triple(.78f, 1.04f, "ease_out"), Triple(1f, 1f, "ease_out")),
            ))
            "tilt_reveal" -> ClipAnimation("Tilt Reveal", "Tilt and fade reveal", listOf("tilt", "reveal"), listOf(
                track("rotation", Triple(0f, -12f, "ease_out"), Triple(.7f, 1.5f, "ease_in_out"), Triple(1f, 0f, "ease_out")),
                track("opacity", Triple(0f, 0f, "linear"), Triple(.22f, 1f, "ease_out")),
                track("scale", Triple(0f, 1.12f, "ease_out"), Triple(1f, 1f, "ease_out")),
            ))
            "pulse" -> ClipAnimation("Beat Pulse", "Three restrained pulses", listOf("pulse", "beat"), listOf(
                track("scale", Triple(0f, 1f, "linear"), Triple(.18f, 1.09f, "ease_out"), Triple(.34f, 1f, "ease_in"), Triple(.55f, 1.07f, "ease_out"), Triple(.72f, 1f, "ease_in"), Triple(.86f, 1.05f, "ease_out"), Triple(1f, 1f, "ease_out")),
            ))
            "drift", "float" -> ClipAnimation("Parallax Drift", "Slow camera drift", listOf("drift", "parallax"), listOf(
                track("position_y", Triple(0f, .08f, "ease_in_out"), Triple(1f, -.08f, "ease_in_out")),
                track("scale", Triple(0f, 1.02f, "linear"), Triple(1f, 1.12f, "linear")),
            ))
            "spin_in" -> ClipAnimation("Spin In", "Rotational entrance", listOf("spin", "entrance"), listOf(
                track("rotation", Triple(0f, -90f, "ease_out"), Triple(.72f, 4f, "ease_in_out"), Triple(1f, 0f, "ease_out")),
                track("scale", Triple(0f, .55f, "ease_out"), Triple(.68f, 1.08f, "ease_out"), Triple(1f, 1f, "ease_out")),
                track("opacity", Triple(0f, 0f, "linear"), Triple(.12f, 1f, "ease_out")),
            ))
            "fade_in" -> ClipAnimation("Fade In", tracks = listOf(track("opacity", Triple(0f, 0f, "linear"), Triple(.18f, 1f, "ease_out"))))
            "fade_out" -> ClipAnimation("Fade Out", tracks = listOf(track("opacity", Triple(.82f, 1f, "linear"), Triple(1f, 0f, "ease_in"))))
            "flicker", "flash_white" -> ClipAnimation("${if (id == "flash_white") "Flash Cut" else "Flicker"}", "Rapid opacity pulses; this is a layer flash, not a white overlay", listOf("flicker", "impact"), listOf(
                track("opacity", Triple(0f, 1f, "linear"), Triple(.08f, .22f, "hold"), Triple(.16f, 1f, "hold"), Triple(.24f, .35f, "hold"), Triple(.34f, 1f, "ease_out")),
            ))
            "glow", "rgb_echo", "motion_blur" -> ClipAnimation(
                name = when (id) { "glow" -> "Glow Filter Label"; "rgb_echo" -> "RGB Echo Label"; else -> "Motion Blur Label" },
                description = "Visual filter request; requires a renderer or verified native Alight Motion effect mapping",
                effects = listOf(id), tracks = emptyList(),
                nativeEffects = listOfNotNull(AlightMotionEffectCatalog.presetRecipe(id)),
            )
            "beat_cut" -> ClipAnimation("Beat Cut", "Timing marker only; clip boundary should align to a detected beat", listOf("beat_cut"), emptyList())
            else -> null
        }
    }
}
