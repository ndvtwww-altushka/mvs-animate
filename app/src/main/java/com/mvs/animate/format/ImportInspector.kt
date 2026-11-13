package com.mvs.animate.format

import android.content.ContentResolver
import android.net.Uri
import com.mvs.animate.data.ProjectCodec
import com.mvs.animate.model.ClipKind
import com.mvs.animate.model.EditProject
import java.util.zip.ZipInputStream

object ImportInspector {
    data class Result(
        val kind: ClipKind?,
        val project: EditProject? = null,
        val warnings: List<String> = emptyList(),
        val error: String? = null,
        val isProject: Boolean = false,
    )

    fun inspect(resolver: ContentResolver, uri: Uri, displayName: String): Result {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri).orEmpty()
        return try {
            when {
                ext == "xml" || mime.contains("xml") -> {
                    val text = readTextLimited(resolver, uri, 20L * 1024 * 1024)
                    val parsed = AlightMotionXml.importScene(text)
                    Result(null, parsed.project, parsed.warnings, isProject = true)
                }
                ext in setOf("mvsanim", "json") || mime.contains("json") -> {
                    val text = readTextLimited(resolver, uri, 40L * 1024 * 1024)
                    Result(null, ProjectCodec.decode(text), isProject = true)
                }
                ext in setOf("zip", "alight") || mime == "application/zip" -> inspectArchive(resolver, uri)
                ext in setOf("srt", "vtt") -> {
                    val text = readTextLimited(resolver, uri, 10L * 1024 * 1024)
                    val clips = SubtitleImporter.parse(text)
                    if (clips.isEmpty()) Result(null, error = "No subtitle cues were recognised")
                    else Result(null, EditProject(title = displayTitle(displayName), clips = clips), listOf("Subtitle cues imported as text layers."), isProject = true)
                }
                ext == "wj" -> inspectUnknownWj(resolver, uri)
                mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") -> Result(ClipKind.IMAGE)
                mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm", "3gp") -> Result(ClipKind.VIDEO)
                mime.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac") -> Result(ClipKind.AUDIO)
                else -> Result(ClipKind.UNKNOWN, error = "Unknown format ($mime). File kept untouched; no decoder is registered.")
            }
        } catch (e: Exception) {
            Result(null, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun inspectArchive(resolver: ContentResolver, uri: Uri): Result {
        val stream = resolver.openInputStream(uri) ?: error("Could not read archive")
        var xml: String? = null
        var manifest: String? = null
        var entryCount = 0
        var expandedBytes = 0L
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > 2_000) error("Archive contains too many entries")
                if (!entry.isDirectory && (entry.name.endsWith(".xml", true) || entry.name.endsWith(".mvsanim", true) || entry.name.endsWith("project.json", true))) {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        expandedBytes += count
                        if (expandedBytes > 40L * 1024 * 1024) error("Project metadata exceeds 40 MB")
                        out.write(buffer, 0, count)
                    }
                    val content = out.toByteArray().toString(Charsets.UTF_8)
                    if (entry.name.endsWith(".xml", true)) xml = content
                    else manifest = content
                } else {
                    // Drain entries in bounded chunks to avoid archive expansion surprises.
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        expandedBytes += count
                        if (expandedBytes > 250L * 1024 * 1024) error("Archive expands beyond 250 MB limit")
                    }
                }
                zip.closeEntry()
            }
        }
        if (!manifest.isNullOrBlank()) return Result(null, ProjectCodec.decode(manifest!!), listOf("MVS package metadata imported. Media inside the archive is not automatically copied into Alight Motion."), isProject = true)
        if (!xml.isNullOrBlank()) {
            val parsed = AlightMotionXml.importScene(xml!!)
            return Result(null, parsed.project, parsed.warnings + "Archive scene imported. Check whether media resources were included and remain accessible.", isProject = true)
        }
        return Result(null, warnings = listOf("Archive has no recognised scene XML or MVS manifest."), error = "No project descriptor found.")
    }


    private fun readTextLimited(resolver: ContentResolver, uri: Uri, maxBytes: Long): String {
        val input = resolver.openInputStream(uri) ?: error("Could not open input file")
        input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) error("File exceeds ${maxBytes / (1024 * 1024)} MB import limit")
                output.write(buffer, 0, count)
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        }
    }

    private fun displayTitle(name: String): String = name.substringBeforeLast('.', name).ifBlank { "Imported subtitles" }

    private fun inspectUnknownWj(resolver: ContentResolver, uri: Uri): Result {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64)
            val count = input.read(buffer)
            if (count <= 0) ByteArray(0) else buffer.copyOf(count)
        } ?: error("Could not read .wj file")
        val preview = bytes.joinToString(" ") { "%02X".format(it) }
        val asText = bytes.toString(Charsets.UTF_8).trimStart()
        if (asText.startsWith("{") || asText.startsWith("<")) {
            return Result(null, error = ".wj appears to contain text data, but its schema is unknown. First bytes: $preview")
        }
        return Result(null, error = ".wj is not a documented Alight Motion interchange format in this build. Binary signature: $preview. File was not modified.")
    }
}
