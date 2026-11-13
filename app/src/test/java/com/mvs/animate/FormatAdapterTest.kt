package com.mvs.animate

import com.mvs.animate.data.PlanParser
import com.mvs.animate.data.ProjectCodec
import com.mvs.animate.format.SubtitleImporter
import com.mvs.animate.model.ClipKind
import com.mvs.animate.model.EditClip
import com.mvs.animate.model.EditProject
import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.MotionKeyframe
import com.mvs.animate.model.MotionTrack
import com.mvs.animate.model.NativeEffectRecipe
import com.mvs.animate.model.NativeEffectParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAdapterTest {
    @Test fun projectManifestRoundTripsCoreFields() {
        val source = EditProject(
            title = "Neon edit", width = 1080, height = 1920, fps = 60.0,
            clips = listOf(EditClip(
                name = "shot.png", kind = ClipKind.IMAGE, uri = "content://test/1", startMs = 1250, durationMs = 1800,
                animation = ClipAnimation("AI Velocity", "Zoom hit", listOf("velocity", "impact"), listOf(
                    MotionTrack("scale", listOf(MotionKeyframe(0f, .8f, "ease_in"), MotionKeyframe(.3f, 1.2f, "ease_out"), MotionKeyframe(1f, 1f))),
                    MotionTrack("rotation", listOf(MotionKeyframe(0f, -4f), MotionKeyframe(1f, 0f))),
                ), nativeEffects = listOf(NativeEffectRecipe(
                    "com.alightcreative.effects.rgbsep", parameters = listOf(
                        NativeEffectParameter("strength", "float", "0.28", listOf(MotionKeyframe(0f, 0f), MotionKeyframe(1f, .28f))),
                        NativeEffectParameter("angle", "float", "0.0"),
                        NativeEffectParameter("centerChannel", "int", "1"),
                        NativeEffectParameter("mode", "int", "2"),
                    )
                ))),
            )),
            musicName = "track.mp3", musicStartMs = 3000, musicEndMs = 15000,
            motionLibrary = listOf(ClipAnimation("Saved Whip", "Reusable curve", listOf("whip"), listOf(
                MotionTrack("rotation", listOf(MotionKeyframe(0f, -6f), MotionKeyframe(1f, 0f))),
            ))),
        )
        val restored = ProjectCodec.decode(ProjectCodec.encode(source))
        assertEquals(source.title, restored.title)
        assertEquals(source.width, restored.width)
        assertEquals(1, restored.clips.size)
        assertEquals(source.clips.first().startMs, restored.clips.first().startMs)
        assertEquals(source.musicName, restored.musicName)
        assertEquals("AI Velocity", restored.clips.first().animation?.name)
        assertEquals(2, restored.clips.first().animation?.tracks?.size)
        assertEquals(3, restored.clips.first().animation?.tracks?.first()?.keyframes?.size)
        assertEquals("com.alightcreative.effects.rgbsep", restored.clips.first().animation?.nativeEffects?.first()?.id)
        assertEquals(2, restored.clips.first().animation?.nativeEffects?.first()?.parameters?.first()?.keyframes?.size)
        assertEquals("Saved Whip", restored.motionLibrary.first().name)
    }

    @Test fun planParserExtractsStructuredCutsFromMarkdown() {
        val response = """Director overview
```json
{"title":"Impact","cuts":[{"atMs":1200,"durationMs":900,"visual":"close up","text":"GO","effect":"shake_soft","animation":{"name":"Bass-hit whip","description":"Fast camera move","effects":["whip","impact"],"tracks":[{"property":"scale","keyframes":[{"t":0,"v":0.8,"easing":"ease_in"},{"t":0.3,"v":1.2,"easing":"ease_out"},{"t":1,"v":1}]},{"property":"rotation","keyframes":[{"t":0,"v":-5},{"t":1,"v":0}]}],"nativeEffects":[{"id":"com.alightcreative.effects.shake2","locallyApplied":true,"parameters":[{"name":"mag","value":"20","keyframes":[{"t":0,"v":0},{"t":0.2,"v":20},{"t":1,"v":0}]}]}]},"textAnimation":{"name":"Caption Pop","description":"Caption scale punch","effects":["text_pop"],"nativeEffects":[{"id":"com.alightcreative.effects.textprogress","parameters":[{"name":"start","value":"0.0","keyframes":[{"t":0,"v":0},{"t":1,"v":0}]},{"name":"end","value":"1.0","keyframes":[{"t":0,"v":0},{"t":0.8,"v":1}]},{"name":"cursor","value":"0"},{"name":"blink","value":"false"}]}],"tracks":[{"property":"scale","keyframes":[{"t":0,"v":0.7},{"t":0.3,"v":1.1},{"t":1,"v":1}]}]}}],"searchQueries":["night city"]}
```"""
        val cuts = PlanParser.parseCuts(response)
        assertEquals(1, cuts.size)
        assertEquals(1200L, cuts.first().atMs)
        assertEquals("shake_soft", cuts.first().effect)
        assertEquals("Bass-hit whip", cuts.first().animation?.name)
        assertEquals(2, cuts.first().animation?.tracks?.size)
        assertEquals(3, cuts.first().animation?.tracks?.first()?.keyframes?.size)
        assertEquals("ease_out", cuts.first().animation?.tracks?.first()?.keyframes?.get(1)?.easing)
        assertEquals("com.alightcreative.effects.shake2", cuts.first().animation?.nativeEffects?.first()?.id)
        assertEquals(3, cuts.first().animation?.nativeEffects?.first()?.parameters?.first()?.keyframes?.size)
        assertEquals("Caption Pop", cuts.first().textAnimation?.name)
        assertEquals("com.alightcreative.effects.textprogress", cuts.first().textAnimation?.nativeEffects?.first()?.id)
    }

    @Test fun planParserHandlesMarkdownProseAndBracesInsideStringsWithoutRegex() {
        val response = """Here is the edit direction: use a {hard} cut and preserve quoted text.
```json
{"title":"Velocity {impact}","overview":"Fast montage","cuts":[{"atMs":0,"durationMs":1200,"visual":"A blade trail with {glow} and \"quotes\"","text":"GO","effect":"zoom_punch","animation":{"name":"Compound","tracks":[{"property":"scale","keyframes":[{"t":0,"v":0.8},{"t":0.5,"v":1.3},{"t":1,"v":1.0}]},{"property":"rotation","keyframes":[{"t":0,"v":-4},{"t":1,"v":0}]}]}}],"searchQueries":["anime blade impact"]}
```
Additional explanation after the JSON.
"""
        val cuts = PlanParser.parseCuts(response)
        assertEquals(1, cuts.size)
        assertEquals("A blade trail with {glow} and \"quotes\"", cuts.first().visual)
        assertEquals(2, cuts.first().animation?.tracks?.size)
        assertEquals(listOf("anime blade impact"), PlanParser.parseSearchQueries(response))
        val summary = PlanParser.summary(response, cuts)
        assertTrue(summary.contains("Velocity {impact}"))
        assertTrue(summary.contains("1 cut(s)"))
        assertTrue(!summary.contains("keyframes"))
    }

    @Test fun planParserAcceptsUnfencedJson() {
        val response = """{"title":"Simple edit","cuts":[{"atMs":100,"durationMs":800,"visual":"Close-up","text":"RISE","effect":"fade_in"}]}"""
        val cuts = PlanParser.parseCuts(response)
        assertEquals(1, cuts.size)
        assertEquals(100L, cuts.single().atMs)
    }

    @Test fun subtitleImporterCreatesTimedTextLayers() {
        val cues = SubtitleImporter.parse("""1
00:00:01,200 --> 00:00:02,800
HELLO

2
00:00:03,000 --> 00:00:04,200
WORLD
""")
        assertEquals(2, cues.size)
        assertEquals(1200L, cues.first().startMs)
        assertEquals(1600L, cues.first().durationMs)
        assertTrue(cues.all { it.kind == ClipKind.TEXT })
    }
}
