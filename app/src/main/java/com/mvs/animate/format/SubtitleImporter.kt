package com.mvs.animate.format

import com.mvs.animate.model.ClipKind
import com.mvs.animate.model.EditClip

object SubtitleImporter {
    fun parse(text: String): List<EditClip> {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("\\n\\s*\\n"))
        return blocks.mapNotNull { block ->
            val lines = block.lines().map(String::trim).filter(String::isNotBlank)
            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex < 0) return@mapNotNull null
            val timeLine = lines[timeLineIndex]
            val times = timeLine.split("-->", limit = 2)
            val start = parseTime(times.getOrNull(0).orEmpty()) ?: return@mapNotNull null
            val end = parseTime(times.getOrNull(1).orEmpty().substringBefore(' ').trim()) ?: return@mapNotNull null
            val caption = lines.drop(timeLineIndex + 1).joinToString("\n").replace(Regex("<[^>]+>"), "").trim()
            if (caption.isBlank()) return@mapNotNull null
            EditClip(
                name = "Subtitle ${start / 1000}s",
                kind = ClipKind.TEXT,
                startMs = start,
                durationMs = (end - start).coerceAtLeast(100L),
                text = caption,
            )
        }
    }

    private fun parseTime(value: String): Long? {
        val cleaned = value.trim().replace(',', '.')
        val parts = cleaned.split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong()).coerceAtLeast(0L)
    }
}
