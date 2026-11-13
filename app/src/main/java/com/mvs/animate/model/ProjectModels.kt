package com.mvs.animate.model

import java.util.UUID

/** AI-authored, editor-independent motion recipe. Times are normalized to the clip (0..1). */
data class MotionKeyframe(
    val t: Float,
    val value: Float,
    val easing: String = "linear",
)

data class MotionTrack(
    /** scale, rotation, opacity, position_x or position_y */
    val property: String,
    val keyframes: List<MotionKeyframe>,
)

data class NativeEffectParameter(
    val name: String,
    /** Alight Motion scene value type: float, int, bool, color, vec2, vec3. */
    val type: String,
    /** Static value in the native XML representation. */
    val value: String,
    /** Optional animated numeric parameter curve. */
    val keyframes: List<MotionKeyframe> = emptyList(),
)

data class NativeEffectRecipe(
    /** Verified Alight Motion effect identifier from the bundled allow-list. */
    val id: String,
    val locallyApplied: Boolean = true,
    val parameters: List<NativeEffectParameter> = emptyList(),
)

data class ClipAnimation(
    val name: String,
    val description: String = "",
    /** Human-readable effect stack labels and MVS-only requests. */
    val effects: List<String> = emptyList(),
    val tracks: List<MotionTrack> = emptyList(),
    /** Native effects with validated IDs and parameter names; exported to scene XML. */
    val nativeEffects: List<NativeEffectRecipe> = emptyList(),
)

enum class ClipKind { IMAGE, VIDEO, AUDIO, TEXT, SHAPE, UNKNOWN }

data class EditClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: ClipKind,
    val uri: String? = null,
    val startMs: Long = 0L,
    val durationMs: Long = 4_000L,
    val sourceInMs: Long = 0L,
    val text: String = "",
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val effects: List<String> = emptyList(),
    val templateLayer: Boolean = false,
    val templateLayerId: String? = null,
    val templateUri: String? = null,
    val animation: ClipAnimation? = null,
)

data class EditProject(
    val title: String = "Untitled edit",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Double = 30.0,
    val background: String = "#FF080A12",
    val clips: List<EditClip> = emptyList(),
    val musicUri: String? = null,
    val musicName: String = "",
    val musicStartMs: Long = 0L,
    val musicEndMs: Long = 0L,
    val sourceSceneXml: String? = null,
    val motionLibrary: List<ClipAnimation> = emptyList(),
)

enum class StageStatus { WAITING, RUNNING, DONE, FAILED }

data class PipelineStage(
    val id: String,
    val title: String,
    val detail: String,
    val status: StageStatus = StageStatus.WAITING,
    val durationMs: Long? = null,
)

data class AudioAnalysis(
    val durationMs: Long,
    val bpm: Int?,
    val averageEnergy: Float,
    val dynamicRange: Float,
    val suggestedStartMs: Long,
    val suggestedEndMs: Long,
    val description: String,
    val beatMarkersMs: List<Long> = emptyList(),
)

data class SourceAsset(
    val title: String,
    val thumbnailUrl: String?,
    val pageUrl: String,
    val license: String,
    val author: String,
)

data class EffectPreset(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val alightMotionEffectId: String? = null,
)

val builtInEffects = listOf(
    EffectPreset("shake_soft", "Soft Shake", "Camera", "Small rhythmic camera movement", "com.alightcreative.effects.shake2"),
    EffectPreset("shake_hard", "Impact Shake", "Camera", "Short high-energy impact shake", "com.alightcreative.effects.shake2"),
    EffectPreset("zoom_punch", "Zoom Punch", "Camera", "Fast scale-in and settle", null),
    EffectPreset("flash_white", "White Flash", "Transition", "One-frame white flash on a beat", null),
    EffectPreset("rgb_echo", "RGB Echo", "Stylized", "Chromatic echo look; app-side preset", null),
    EffectPreset("motion_blur", "Motion Blur", "Stylized", "Directional blur; app-side preset", null),
    EffectPreset("fade_in", "Fade In", "Transition", "Opacity keyframes", null),
    EffectPreset("fade_out", "Fade Out", "Transition", "Opacity keyframes", null),
    EffectPreset("glow", "Glow", "Stylized", "Soft highlight bloom", null),
    EffectPreset("beat_cut", "Beat Cut", "Timing", "Snap clip boundary to detected beat", null),
    EffectPreset("velocity_ramp", "Velocity Ramp", "Timing", "Fast-in / slow-out scale timing curve", null),
    EffectPreset("whip_pan", "Whip Pan", "Camera", "Directional travel with rotation settle", null),
    EffectPreset("handheld", "Handheld", "Camera", "Layered micro-position and rotation movement", null),
    EffectPreset("bounce", "Bounce", "Motion", "Elastic scale overshoot and settle", null),
    EffectPreset("tilt_reveal", "Tilt Reveal", "Motion", "Rotational reveal with opacity rise", null),
    EffectPreset("pulse", "Pulse", "Motion", "Repeated scale pulse around the beat", null),
    EffectPreset("drift", "Parallax Drift", "Camera", "Slow position drift across the frame", null),
    EffectPreset("snap_zoom", "Snap Zoom", "Camera", "Sharp zoom hit with quick recovery", null),
    EffectPreset("flicker", "Flicker", "Transition", "Short opacity pulses; use sparingly", null),
    EffectPreset("spin_in", "Spin In", "Motion", "Rotational entrance with scale-up", null),
    EffectPreset("float", "Float", "Motion", "Gentle vertical floating movement", null),
)
