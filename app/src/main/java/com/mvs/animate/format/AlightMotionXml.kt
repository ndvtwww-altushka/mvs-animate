package com.mvs.animate.format

import com.mvs.animate.data.AlightMotionEffectCatalog
import android.util.Xml
import com.mvs.animate.model.ClipKind
import com.mvs.animate.model.EditClip
import com.mvs.animate.model.EditProject
import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.MotionKeyframe
import com.mvs.animate.model.MotionTrack
import com.mvs.animate.model.NativeEffectRecipe
import com.mvs.animate.model.NativeEffectParameter
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

/**
 * Interchange adapter for Alight Motion's documented scene XML structure.
 * Unknown nodes are ignored on import and reported; this is not a binary-project decoder.
 */
object AlightMotionXml {
    data class ImportResult(val project: EditProject, val warnings: List<String>, val originalXml: String)
    data class ExportResult(val xml: String, val warnings: List<String>)

    fun importScene(xml: String): ImportResult {
        require(xml.length <= 20 * 1024 * 1024) { "XML is larger than 20 MB" }
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        var title = "Imported Alight Motion scene"
        var width = 1080
        var height = 1920
        var fps = 30.0
        var background = "#FF000000"
        var rootSeen = false
        val clips = mutableListOf<EditClip>()
        val warnings = mutableListOf<String>()
        var current: MutableLayer? = null
        var currentTag = ""
        var currentProperty = ""
        var textContent = StringBuilder()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == "scene") {
                        rootSeen = true
                        title = parser.getAttributeValue(null, "title") ?: title
                        width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: width
                        height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: height
                        fps = parser.getAttributeValue(null, "fps")?.toDoubleOrNull() ?: fps
                        background = parser.getAttributeValue(null, "bgcolor") ?: background
                    }
                    if (tag in setOf("shape", "text", "group", "drawing", "color", "media") &&
                        parser.getAttributeValue(null, "startTime") != null) {
                        if (current != null) warnings += "Nested layer '${currentTag}' was flattened."
                        currentTag = tag
                        current = MutableLayer(
                            name = parser.getAttributeValue(null, "label") ?: "$tag layer ${clips.size + 1}",
                            type = when (tag) {
                                "text" -> ClipKind.TEXT
                                "media" -> when (parser.getAttributeValue(null, "type").orEmpty()) {
                                    "image/jpeg", "image/png", "image/webp" -> ClipKind.IMAGE
                                    "video/mp4", "video/quicktime" -> ClipKind.VIDEO
                                    "audio/mpeg", "audio/mp4", "audio/wav" -> ClipKind.AUDIO
                                    else -> ClipKind.UNKNOWN
                                }
                                "shape" -> if (parser.getAttributeValue(null, "fillType") == "media") ClipKind.IMAGE else ClipKind.SHAPE
                                "color" -> ClipKind.SHAPE
                                else -> ClipKind.SHAPE
                            },
                            start = parser.getAttributeValue(null, "startTime")?.toLongOrNull() ?: 0L,
                            end = parser.getAttributeValue(null, "endTime")?.toLongOrNull() ?: 4_000L,
                            uri = parser.getAttributeValue(null, "uri"),
                            templateId = parser.getAttributeValue(null, "id"),
                        )
                    } else if (current != null) {
                        when (tag) {
                            "content" -> textContent = StringBuilder()
                            "effect" -> current!!.effects += parser.getAttributeValue(null, "id").orEmpty()
                            "location", "scale", "rotation", "opacity" -> {
                                currentProperty = tag
                                when (tag) {
                                    "location" -> current!!.position = parser.getAttributeValue(null, "value").orEmpty()
                                    "scale" -> current!!.scale = parser.getAttributeValue(null, "value")?.substringBefore(',')?.toFloatOrNull() ?: 1f
                                    "rotation" -> current!!.rotation = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f
                                    "opacity" -> current!!.opacity = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: 1f
                                }
                            }
                            "kf" -> if (currentProperty.isNotBlank()) {
                                val time = parser.getAttributeValue(null, "t")?.toFloatOrNull()
                                val rawValue = parser.getAttributeValue(null, "v").orEmpty()
                                if (time != null) {
                                    val property = when (currentProperty) { "location" -> "position"; else -> currentProperty }
                                    val values = rawValue.split(',').mapNotNull { it.trim().toFloatOrNull() }
                                    if (values.isNotEmpty()) current!!.keyframes.getOrPut(property) { mutableListOf() } +=
                                        ImportedFrame(time.coerceIn(0f, 1f), values, parser.getAttributeValue(null, "e").orEmpty())
                                }
                            }
                            "fillimage" -> current!!.uri = parser.getAttributeValue(null, "uri") ?: current!!.uri
                        }
                    }
                }
                XmlPullParser.TEXT -> if (current != null && currentTag == "text") textContent.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("content", true) && current != null) current!!.text = textContent.toString()
                    if (parser.name.equals(currentProperty, true)) currentProperty = ""
                    if (current != null && parser.name.equals(currentTag, true)) {
                        val layer = current!!
                        clips += EditClip(
                            name = layer.name,
                            kind = layer.type,
                            uri = layer.uri,
                            startMs = layer.start,
                            durationMs = (layer.end - layer.start).coerceAtLeast(1L),
                            text = layer.text,
                            scale = layer.scale,
                            rotation = layer.rotation,
                            opacity = layer.opacity.coerceIn(0f, 1f),
                            effects = layer.effects,
                            templateLayer = true,
                            templateLayerId = layer.templateId,
                            templateUri = layer.uri,
                            animation = layer.toAnimation(width, height),
                        )
                        current = null
                        currentTag = ""
                    }
                }
            }
            parser.next()
        }
        require(rootSeen) { "XML does not contain an Alight Motion <scene> root" }
        if (clips.isEmpty()) warnings += "No editable layers were found. The XML may be a preset wrapper or an unsupported version."
        if (width <= 0 || height <= 0) warnings += "Invalid canvas size; using 1080×1920."
        return ImportResult(
            EditProject(title, width.coerceIn(1, 8192), height.coerceIn(1, 8192), fps.coerceIn(1.0, 240.0), background, clips, sourceSceneXml = xml),
            warnings,
            xml,
        )
    }

    fun exportScene(project: EditProject): ExportResult {
        val source = project.sourceSceneXml
        return if (!source.isNullOrBlank()) exportTemplateScene(project, source) else buildScene(project)
    }

    private fun buildScene(project: EditProject): ExportResult {
        val warnings = mutableListOf<String>()
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "scene")
        serializer.attribute(null, "title", project.title.take(120))
        serializer.attribute(null, "width", project.width.toString())
        serializer.attribute(null, "height", project.height.toString())
        serializer.attribute(null, "exportWidth", project.width.toString())
        serializer.attribute(null, "exportHeight", project.height.toString())
        serializer.attribute(null, "bgcolor", project.background)
        serializer.attribute(null, "totalTime", project.clips.maxOfOrNull { it.startMs + it.durationMs }?.coerceAtLeast(1L)?.toString() ?: "1000")
        serializer.attribute(null, "fps", project.fps.toString())
        serializer.attribute(null, "modifiedTime", System.currentTimeMillis().toString())
        serializer.attribute(null, "amver", "106")
        serializer.attribute(null, "ffver", "101")
        serializer.attribute(null, "am", "com.alightcreative.motion/5.0.0")
        serializer.attribute(null, "amplatform", "android")

        project.clips.forEachIndexed { index, clip ->
            val start = clip.startMs.coerceAtLeast(0L)
            val end = (clip.startMs + clip.durationMs).coerceAtLeast(start + 1L)
            when (clip.kind) {
                ClipKind.TEXT -> {
                    serializer.startTag(null, "text")
                    serializer.attribute(null, "id", (index + 1).toString())
                    serializer.attribute(null, "label", clip.name)
                    serializer.attribute(null, "startTime", start.toString())
                    serializer.attribute(null, "endTime", end.toString())
                    serializer.attribute(null, "fillType", "color")
                    serializer.attribute(null, "size", "64.000000")
                    serializer.attribute(null, "font", "googlefonts?name=Roboto&weight=700")
                    serializer.attribute(null, "wrapWidth", (project.width * 0.88).toInt().toString())
                    serializer.attribute(null, "align", "center")
                    writeTransform(serializer, project, clip)
                    serializer.startTag(null, "fillColor"); serializer.attribute(null, "value", "#FFFFFFFF"); serializer.endTag(null, "fillColor")
                    serializer.startTag(null, "content"); serializer.text(clip.text.ifBlank { clip.name }); serializer.endTag(null, "content")
                    writeEffects(serializer, clip, warnings)
                    serializer.endTag(null, "text")
                }
                ClipKind.IMAGE, ClipKind.VIDEO -> {
                    serializer.startTag(null, "shape")
                    serializer.attribute(null, "id", (index + 1).toString())
                    serializer.attribute(null, "label", clip.name)
                    serializer.attribute(null, "startTime", start.toString())
                    serializer.attribute(null, "endTime", end.toString())
                    serializer.attribute(null, "fillType", "media")
                    serializer.attribute(null, "s", ".rect")
                    writeTransform(serializer, project, clip)
                    serializer.startTag(null, "fillImage")
                    serializer.attribute(null, "uri", clip.uri ?: "")
                    serializer.endTag(null, "fillImage")
                    writeEffects(serializer, clip, warnings)
                    serializer.endTag(null, "shape")
                    if (clip.uri.isNullOrBlank()) warnings += "${clip.name}: media URI missing; relink media in Alight Motion."
                    else warnings += "${clip.name}: external Android content URI may need relinking after XML import."
                }
                ClipKind.AUDIO -> {
                    warnings += "${clip.name}: audio track kept in MVS Animate project manifest; Alight Motion XML audio relinking is not exported yet."
                }
                ClipKind.SHAPE -> {
                    serializer.startTag(null, "shape")
                    serializer.attribute(null, "id", (index + 1).toString())
                    serializer.attribute(null, "label", clip.name)
                    serializer.attribute(null, "startTime", start.toString())
                    serializer.attribute(null, "endTime", end.toString())
                    serializer.attribute(null, "fillType", "color")
                    serializer.attribute(null, "s", ".rect")
                    writeTransform(serializer, project, clip)
                    serializer.startTag(null, "fillColor"); serializer.attribute(null, "value", "#FF7B61FF"); serializer.endTag(null, "fillColor")
                    writeEffects(serializer, clip, warnings)
                    serializer.endTag(null, "shape")
                }
                ClipKind.UNKNOWN -> warnings += "${clip.name}: unsupported layer type was omitted."
            }
        }
        serializer.endTag(null, "scene")
        serializer.endDocument()
        return ExportResult(writer.toString(), warnings.distinct())
    }

    private fun exportTemplateScene(project: EditProject, sourceXml: String): ExportResult {
        val warnings = mutableListOf<String>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(sourceXml)))
        val root = document.documentElement
        if (root.tagName != "scene") throw IllegalArgumentException("Imported template no longer has a scene root")
        root.setAttribute("title", project.title.take(120))
        root.setAttribute("width", project.width.toString())
        root.setAttribute("height", project.height.toString())
        root.setAttribute("exportWidth", project.width.toString())
        root.setAttribute("exportHeight", project.height.toString())
        root.setAttribute("fps", project.fps.toString())
        root.setAttribute("modifiedTime", System.currentTimeMillis().toString())
        root.setAttribute("totalTime", (project.clips.maxOfOrNull { it.startMs + it.durationMs } ?: 1_000L).coerceAtLeast(1L).toString())

        val allElements = root.getElementsByTagName("*")
        project.clips.filter { it.templateLayer }.forEach { clip ->
            val layerId = clip.templateLayerId
            if (layerId.isNullOrBlank()) {
                warnings += "${clip.name}: original layer ID is missing; template data was left unchanged."
                return@forEach
            }
            var target: Element? = null
            for (i in 0 until allElements.length) {
                val element = allElements.item(i) as? Element ?: continue
                if (element.getAttribute("id") == layerId) { target = element; break }
            }
            val layer = target
            if (layer == null) {
                warnings += "${clip.name}: template layer $layerId was not found; original XML was preserved."
                return@forEach
            }
            layer.setAttribute("label", clip.name)
            layer.setAttribute("startTime", clip.startMs.coerceAtLeast(0L).toString())
            layer.setAttribute("endTime", (clip.startMs + clip.durationMs).coerceAtLeast(clip.startMs + 1L).toString())
            if (clip.kind == ClipKind.TEXT) {
                val contents = layer.getElementsByTagName("content")
                if (contents.length > 0) {
                    contents.item(0).textContent = clip.text
                } else {
                    warnings += "${clip.name}: text content node was not found; original template text was preserved."
                }
            }
            if (!clip.uri.isNullOrBlank() && clip.uri != clip.templateUri) {
                var replaced = false
                if (!clip.templateUri.isNullOrBlank()) {
                    val nodes = root.getElementsByTagName("*")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as? Element ?: continue
                        if (element.getAttribute("uri") == clip.templateUri) {
                            element.setAttribute("uri", clip.uri)
                            replaced = true
                        }
                    }
                } else {
                    val fills = layer.getElementsByTagName("fillImage")
                    if (fills.length > 0) {
                        (fills.item(0) as Element).setAttribute("uri", clip.uri)
                        replaced = true
                    }
                }
                if (!replaced) warnings += "${clip.name}: no media reference node was found; choose a media placeholder layer in the template."
            }
            // Keep imported transforms, effects and unknown children intact. Only update simple static transform values.
            val transforms = layer.getElementsByTagName("transform")
            if (transforms.length > 0) {
                val transform = transforms.item(0) as Element
                updateStaticProperty(transform, "scale", "${clip.scale},${clip.scale}")
                updateStaticProperty(transform, "rotation", clip.rotation.toString())
                updateStaticProperty(transform, "opacity", clip.opacity.coerceIn(0f, 1f).toString())
                if ("zoom_punch" in clip.effects && clip.animation?.tracks?.none { it.property == "scale" } != false) animateProperty(transform, "scale", listOf(
                    Triple("0.000000", "${clip.scale * 0.86f},${clip.scale * 0.86f}", null),
                    Triple("0.220000", "${clip.scale * 1.12f},${clip.scale * 1.12f}", "cubicBezier 0.0 0.0 0.58 1.0"),
                    Triple("0.620000", "${clip.scale},${clip.scale}", null),
                ), warnings, clip.name)
                if ("fade_in" in clip.effects && clip.animation?.tracks?.none { it.property == "opacity" } != false) animateProperty(transform, "opacity", listOf(
                    Triple("0.000000", "0.000000", null),
                    Triple("0.180000", clip.opacity.toString(), "cubicBezier 0.0 0.0 0.58 1.0"),
                ), warnings, clip.name)
                if ("fade_out" in clip.effects && clip.animation?.tracks?.none { it.property == "opacity" } != false) animateProperty(transform, "opacity", listOf(
                    Triple("0.820000", clip.opacity.toString(), null),
                    Triple("1.000000", "0.000000", "cubicBezier 0.42 0.0 1.0 1.0"),
                ), warnings, clip.name)
                clip.animation?.tracks?.forEach { track ->
                    when (track.property) {
                        "scale" -> animateProperty(transform, "scale", track.keyframes.map { Triple(fmt(it.t), "${it.value},${it.value}", easingXml(it.easing)) }, warnings, clip.name)
                        "rotation" -> animateProperty(transform, "rotation", track.keyframes.map { Triple(fmt(it.t), it.value.toString(), easingXml(it.easing)) }, warnings, clip.name)
                        "opacity" -> animateProperty(transform, "opacity", track.keyframes.map { Triple(fmt(it.t), it.value.toString(), easingXml(it.easing)) }, warnings, clip.name)
                    }
                }
                if (clip.animation?.tracks?.any { it.property == "position_x" || it.property == "position_y" } == true) {
                    animateLocation(transform, project, clip, warnings)
                }
            }
            val existingEffectIds = (0 until layer.getElementsByTagName("effect").length).mapNotNull { i ->
                (layer.getElementsByTagName("effect").item(i) as? Element)?.getAttribute("id")
            }.toSet()
            val addedEffectIds = existingEffectIds.toMutableSet()
            clip.animation?.nativeEffects.orEmpty().take(5).forEach { recipe ->
                if (recipe.id !in addedEffectIds && AlightMotionEffectCatalog.find(recipe.id) != null) {
                    appendNativeEffect(document, layer, recipe, warnings)
                    addedEffectIds += recipe.id
                }
            }
            clip.effects.forEach { effectId ->
                val presetRecipe = AlightMotionEffectCatalog.presetRecipe(effectId)
                if (presetRecipe != null && presetRecipe.id !in addedEffectIds) {
                    appendNativeEffect(document, layer, presetRecipe, warnings)
                    addedEffectIds += presetRecipe.id
                } else if (effectId !in setOf("zoom_punch", "fade_in", "fade_out", "beat_cut") &&
                    effectId !in existingEffectIds && effectId !in addedEffectIds &&
                    AlightMotionEffectCatalog.find(effectId) == null) {
                    warnings += "$effectId: not mapped to a verified Alight Motion effect; original template was kept unchanged."
                }
            }
        }

        val additions = project.clips.filterNot { it.templateLayer || it.kind == ClipKind.AUDIO }
        if (additions.isNotEmpty()) {
            val generated = buildScene(project.copy(sourceSceneXml = null, clips = additions)).xml
            val generatedDoc = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(generated)))
            val generatedRoot = generatedDoc.documentElement
            val layerNames = setOf("shape", "text", "group", "drawing", "color", "media")
            val childNodes = generatedRoot.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node is Element && node.tagName in layerNames) root.appendChild(document.importNode(node, true))
            }
        }
        project.clips.filter { it.kind == ClipKind.AUDIO }.takeIf { it.isNotEmpty() }?.let {
            warnings += "Audio is retained in the MVS project but is not rewritten into this Alight Motion XML template."
        }
        warnings += "Original template nodes, known effects and keyframe curves were preserved; verify the result in Alight Motion."
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        val output = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(output))
        return ExportResult(output.toString(), warnings.distinct())
    }

    private fun animateLocation(transform: Element, project: EditProject, clip: EditClip, warnings: MutableList<String>) {
        val locationNodes = transform.getElementsByTagName("location")
        if (locationNodes.length == 0) return
        val location = locationNodes.item(0) as? Element ?: return
        if (location.getElementsByTagName("kf").length > 0) {
            warnings += "${clip.name}: existing location keyframes were preserved; AI position tracks were not layered over them."
            return
        }
        val tracks = clip.animation?.tracks.orEmpty().associateBy { it.property }
        val x = tracks["position_x"]
        val y = tracks["position_y"]
        if (x == null && y == null) return
        val times = (x?.keyframes.orEmpty().map { it.t } + y?.keyframes.orEmpty().map { it.t } + listOf(0f, 1f)).distinct().sorted()
        location.removeAttribute("value")
        while (location.firstChild != null) location.removeChild(location.firstChild)
        times.forEach { t ->
            val px = project.width / 2f + interpolate(x, t, 0f) * project.width / 2f
            val py = project.height / 2f + interpolate(y, t, 0f) * project.height / 2f
            val frame = transform.ownerDocument.createElement("kf")
            frame.setAttribute("t", fmt(t))
            frame.setAttribute("v", "${fmt(px)},${fmt(py)}")
            val easing = easingAt(x, t).takeIf { it != "linear" } ?: easingAt(y, t)
            easingXml(easing)?.let { frame.setAttribute("e", it) }
            location.appendChild(frame)
        }
    }

    private fun animateProperty(
        transform: Element,
        tag: String,
        frames: List<Triple<String, String, String?>>,
        warnings: MutableList<String>,
        layerName: String,
    ) {
        val nodes = transform.getElementsByTagName(tag)
        if (nodes.length == 0) return
        val property = nodes.item(0) as? Element ?: return
        if (property.getElementsByTagName("kf").length > 0) {
            warnings += "$layerName: existing $tag keyframes were preserved; the new AI/MVS animation was not layered over them."
            return
        }
        property.removeAttribute("value")
        while (property.firstChild != null) property.removeChild(property.firstChild)
        frames.forEach { (time, value, easing) ->
            val keyframe = transform.ownerDocument.createElement("kf")
            keyframe.setAttribute("t", time)
            keyframe.setAttribute("v", value)
            if (easing != null) keyframe.setAttribute("e", easing)
            property.appendChild(keyframe)
        }
    }

    private fun appendNativeEffect(
        document: org.w3c.dom.Document,
        layer: Element,
        recipe: NativeEffectRecipe,
        warnings: MutableList<String>,
    ) {
        val spec = AlightMotionEffectCatalog.find(recipe.id)
        if (spec == null) {
            warnings += "${recipe.id}: unknown native effect; it was not emitted."
            return
        }
        val effect = document.createElement("effect")
        effect.setAttribute("id", recipe.id)
        effect.setAttribute("locallyApplied", recipe.locallyApplied.toString())
        val provided = recipe.parameters.associateBy { it.name }
        spec.parameters.forEach { parameterSpec ->
            val model = provided[parameterSpec.name]
                ?: AlightMotionEffectCatalog.normalizeParameter(recipe.id, parameterSpec.name, parameterSpec.defaultValue, emptyList())
                ?: return@forEach
            val property = document.createElement("property")
            property.setAttribute("name", parameterSpec.name)
            property.setAttribute("type", parameterSpec.type)
            if (model.keyframes.size >= 2 && parameterSpec.animatable) {
                model.keyframes.sortedBy { it.t }.forEach { frame ->
                    val keyframe = document.createElement("kf")
                    keyframe.setAttribute("t", fmt(frame.t))
                    keyframe.setAttribute("v", fmt(frame.value))
                    easingXml(frame.easing)?.let { keyframe.setAttribute("e", it) }
                    property.appendChild(keyframe)
                }
            } else {
                property.setAttribute("value", when (parameterSpec.type) {
                    "bool" -> if (model.value == "true" || model.value == "1") "1" else "0"
                    else -> model.value
                })
            }
            effect.appendChild(property)
        }
        layer.appendChild(effect)
    }

    private fun updateStaticProperty(transform: Element, tag: String, value: String) {
        val properties = transform.getElementsByTagName(tag)
        if (properties.length == 0) return
        val node = properties.item(0) as? Element ?: return
        // Do not flatten or destroy imported keyframe animations.
        if (node.getElementsByTagName("kf").length == 0) node.setAttribute("value", value)
    }

    private fun writeTransform(serializer: org.xmlpull.v1.XmlSerializer, project: EditProject, clip: EditClip) {
        serializer.startTag(null, "transform")
        val tracks = clip.animation?.tracks.orEmpty().associateBy { it.property }
        val xTrack = tracks["position_x"]
        val yTrack = tracks["position_y"]
        if (xTrack != null || yTrack != null) {
            val times = (xTrack?.keyframes.orEmpty().map { it.t } + yTrack?.keyframes.orEmpty().map { it.t } + listOf(0f, 1f)).distinct().sorted()
            serializer.startTag(null, "location")
            times.forEach { t ->
                val x = project.width / 2f + interpolate(xTrack, t, 0f) * project.width / 2f
                val y = project.height / 2f + interpolate(yTrack, t, 0f) * project.height / 2f
                serializer.startTag(null, "kf")
                serializer.attribute(null, "t", fmt(t))
                serializer.attribute(null, "v", "${fmt(x)},${fmt(y)}")
                val easing = easingAt(xTrack, t).takeIf { it != "linear" } ?: easingAt(yTrack, t)
                easingXml(easing)?.let { serializer.attribute(null, "e", it) }
                serializer.endTag(null, "kf")
            }
            serializer.endTag(null, "location")
        } else {
            serializer.startTag(null, "location")
            serializer.attribute(null, "value", "${project.width / 2.0},${project.height / 2.0}")
            serializer.endTag(null, "location")
        }
        writeAnimatedProperty(serializer, "scale", "${clip.scale},${clip.scale}", tracks["scale"], clip, "zoom_punch") { value -> "${value},${value}" }
        writeAnimatedProperty(serializer, "rotation", clip.rotation.toString(), tracks["rotation"], clip, null) { it.toString() }
        writeAnimatedProperty(serializer, "opacity", clip.opacity.coerceIn(0f, 1f).toString(), tracks["opacity"], clip, null) { it.toString() }
        serializer.startTag(null, "anchor"); serializer.attribute(null, "value", "0.0,0.0"); serializer.endTag(null, "anchor")
        serializer.endTag(null, "transform")
    }

    private fun writeAnimatedProperty(
        serializer: org.xmlpull.v1.XmlSerializer,
        tag: String,
        staticValue: String,
        track: MotionTrack?,
        clip: EditClip,
        legacyPreset: String?,
        formatter: (Float) -> String,
    ) {
        serializer.startTag(null, tag)
        when {
            track != null -> track.keyframes.forEach { frame ->
                serializer.startTag(null, "kf")
                serializer.attribute(null, "t", fmt(frame.t))
                serializer.attribute(null, "v", formatter(frame.value))
                easingXml(frame.easing)?.let { serializer.attribute(null, "e", it) }
                serializer.endTag(null, "kf")
            }
            legacyPreset == "zoom_punch" && legacyPreset in clip.effects -> {
                listOf(Triple(0f, clip.scale * .86f, "linear"), Triple(.22f, clip.scale * 1.12f, "ease_out"), Triple(.62f, clip.scale, "linear")).forEach { (t, value, easing) ->
                    serializer.startTag(null, "kf"); serializer.attribute(null, "t", fmt(t)); serializer.attribute(null, "v", formatter(value)); easingXml(easing)?.let { serializer.attribute(null, "e", it) }; serializer.endTag(null, "kf")
                }
            }
            tag == "opacity" && "fade_in" in clip.effects -> {
                listOf(Triple(0f, 0f, "linear"), Triple(.18f, clip.opacity, "ease_out")).forEach { (t, value, easing) ->
                    serializer.startTag(null, "kf"); serializer.attribute(null, "t", fmt(t)); serializer.attribute(null, "v", formatter(value)); easingXml(easing)?.let { serializer.attribute(null, "e", it) }; serializer.endTag(null, "kf")
                }
            }
            tag == "opacity" && "fade_out" in clip.effects -> {
                listOf(Triple(.82f, clip.opacity, "linear"), Triple(1f, 0f, "ease_in")).forEach { (t, value, easing) ->
                    serializer.startTag(null, "kf"); serializer.attribute(null, "t", fmt(t)); serializer.attribute(null, "v", formatter(value)); easingXml(easing)?.let { serializer.attribute(null, "e", it) }; serializer.endTag(null, "kf")
                }
            }
            else -> serializer.attribute(null, "value", staticValue)
        }
        serializer.endTag(null, tag)
    }

    private fun interpolate(track: MotionTrack?, t: Float, fallback: Float): Float {
        if (track == null || track.keyframes.isEmpty()) return fallback
        val frames = track.keyframes.sortedBy { it.t }
        if (t <= frames.first().t) return frames.first().value
        if (t >= frames.last().t) return frames.last().value
        val pair = frames.zipWithNext().firstOrNull { (a, b) -> t in a.t..b.t } ?: return frames.last().value
        val (a, b) = pair
        val fraction = if (b.t == a.t) 1f else (t - a.t) / (b.t - a.t)
        return a.value + (b.value - a.value) * fraction
    }

    private fun easingAt(track: MotionTrack?, t: Float): String = track?.keyframes?.lastOrNull { it.t <= t }?.easing ?: "linear"

    private fun easingXml(easing: String): String? = when (easing) {
        "ease_in" -> "cubicBezier 0.42 0.0 1.0 1.0"
        "ease_out" -> "cubicBezier 0.0 0.0 0.58 1.0"
        "ease_in_out" -> "cubicBezier 0.42 0.0 0.58 1.0"
        // Hold interpolation is represented by repeated values; do not emit an unverified AM easing token.
        "hold" -> null
        else -> null
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.6f", value)

    private fun writeEffects(serializer: org.xmlpull.v1.XmlSerializer, clip: EditClip, warnings: MutableList<String>) {
        val emitted = mutableSetOf<String>()
        clip.animation?.nativeEffects.orEmpty().take(5).forEach { recipe ->
            writeNativeEffect(serializer, recipe, warnings)
            emitted += recipe.id
        }
        clip.effects.forEach { effect ->
            val presetRecipe = AlightMotionEffectCatalog.presetRecipe(effect)
            val nativeRecipe = presetRecipe ?: AlightMotionEffectCatalog.defaultRecipe(effect)
            if (nativeRecipe != null) {
                if (nativeRecipe.id !in emitted) {
                    writeNativeEffect(serializer, nativeRecipe, warnings)
                    emitted += nativeRecipe.id
                }
            } else if (effect !in setOf("zoom_punch", "fade_in", "fade_out", "beat_cut")) {
                warnings += "$effect: MVS-only effect label; no native Alight Motion ID is emitted."
            }
        }
        clip.animation?.let { recipe ->
            val nonNative = recipe.effects.filter { label ->
                label !in clip.effects && AlightMotionEffectCatalog.presetRecipe(label) == null && AlightMotionEffectCatalog.find(label) == null
            }
            if (nonNative.isNotEmpty()) warnings += "${recipe.name}: MVS-only AI effect labels [${nonNative.joinToString()}] were kept in the .mvsanim project."
        }
    }

    private fun writeNativeEffect(
        serializer: org.xmlpull.v1.XmlSerializer,
        recipe: NativeEffectRecipe,
        warnings: MutableList<String>,
    ) {
        val spec = AlightMotionEffectCatalog.find(recipe.id)
        if (spec == null) {
            warnings += "${recipe.id}: unknown native effect ID was skipped."
            return
        }
        serializer.startTag(null, "effect")
        serializer.attribute(null, "id", recipe.id)
        serializer.attribute(null, "locallyApplied", recipe.locallyApplied.toString())
        val provided = recipe.parameters.associateBy { it.name }
        spec.parameters.forEach { parameterSpec ->
            val parameter = provided[parameterSpec.name]
                ?: AlightMotionEffectCatalog.normalizeParameter(recipe.id, parameterSpec.name, parameterSpec.defaultValue, emptyList())
                ?: return@forEach
            serializer.startTag(null, "property")
            serializer.attribute(null, "name", parameterSpec.name)
            serializer.attribute(null, "type", parameterSpec.type)
            if (parameterSpec.animatable && parameter.keyframes.size >= 2) {
                parameter.keyframes.sortedBy { it.t }.forEach { frame ->
                    serializer.startTag(null, "kf")
                    serializer.attribute(null, "t", fmt(frame.t))
                    serializer.attribute(null, "v", fmt(frame.value))
                    easingXml(frame.easing)?.let { serializer.attribute(null, "e", it) }
                    serializer.endTag(null, "kf")
                }
            } else {
                val value = if (parameterSpec.type == "bool") {
                    if (parameter.value == "true" || parameter.value == "1") "1" else "0"
                } else parameter.value
                serializer.attribute(null, "value", value)
            }
            serializer.endTag(null, "property")
        }
        serializer.endTag(null, "effect")
    }

    private data class MutableLayer(
        val name: String,
        val type: ClipKind,
        val start: Long,
        val end: Long,
        var uri: String? = null,
        var templateId: String? = null,
        var text: String = "",
        var position: String = "",
        var scale: Float = 1f,
        var rotation: Float = 0f,
        var opacity: Float = 1f,
        val effects: MutableList<String> = mutableListOf(),
        val keyframes: MutableMap<String, MutableList<ImportedFrame>> = mutableMapOf(),
    ) {
        fun toAnimation(canvasWidth: Int, canvasHeight: Int): ClipAnimation? {
            val tracks = mutableListOf<MotionTrack>()
            listOf("scale", "rotation", "opacity").forEach { property ->
                val frames = keyframes[property].orEmpty().mapNotNull { frame ->
                    val value = frame.values.firstOrNull() ?: return@mapNotNull null
                    MotionKeyframe(frame.t, value, easingFromXml(frame.easing))
                }
                if (frames.size >= 2) tracks += MotionTrack(property, frames)
            }
            val positions = keyframes["position"].orEmpty()
            if (positions.size >= 2) {
                val xFrames = positions.mapNotNull { f -> f.values.getOrNull(0)?.let { MotionKeyframe(f.t, ((it - canvasWidth / 2f) / (canvasWidth / 2f)).coerceIn(-1f, 1f), easingFromXml(f.easing)) } }
                val yFrames = positions.mapNotNull { f -> f.values.getOrNull(1)?.let { MotionKeyframe(f.t, ((it - canvasHeight / 2f) / (canvasHeight / 2f)).coerceIn(-1f, 1f), easingFromXml(f.easing)) } }
                if (xFrames.size >= 2) tracks += MotionTrack("position_x", xFrames)
                if (yFrames.size >= 2) tracks += MotionTrack("position_y", yFrames)
            }
            return if (tracks.isEmpty()) null else ClipAnimation("Imported keyframe animation", "Imported from Alight Motion XML", effects, tracks)
        }
    }

    private data class ImportedFrame(val t: Float, val values: List<Float>, val easing: String)

    private fun easingFromXml(easing: String): String = when {
        easing.contains("0.42 0.0 1.0 1.0") -> "ease_in"
        easing.contains("0.0 0.0 0.58 1.0") -> "ease_out"
        easing.contains("0.42 0.0 0.58 1.0") -> "ease_in_out"
        easing.equals("hold", true) -> "hold"
        else -> "linear"
    }
}
