package com.raulshma.jellyplay.feature.player.audio.lyrics

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource

object LrcParser {

    private val LINE_REGEX = Regex("""^(\[(\d{1,2}):(\d{2}\.\d{2,3})])+(.+)$""")
    private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})]""")
    private val OFFSET_REGEX = Regex("""\[offset:([+-]?\d+)]""")

    fun parse(lrcContent: String): LyricsResult {
        val lines = mutableListOf<LyricsLine>()
        var offsetMs = 0L

        lrcContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            // Parse offset tag
            val offsetMatch = OFFSET_REGEX.find(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLong()
                return@forEach
            }

            // Skip metadata tags like [ti:Title], [ar:Artist]
            if (line.matches(Regex("""^\[\w+:.+]$""")) && !line.matches(TIME_REGEX)) {
                return@forEach
            }

            val lineMatch = LINE_REGEX.find(line) ?: return@forEach
            val text = lineMatch.groupValues.last()
            val times = TIME_REGEX.findAll(line).map { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toDouble()
                minutes * 60_000 + (seconds * 1000).toLong()
            }.toList()

            times.forEach { timeMs ->
                lines.add(LyricsLine(timeMs = timeMs + offsetMs, text = text))
            }
        }

        val sortedLines = lines.sortedBy { it.timeMs }
        val resultLines = sortedLines.mapIndexed { index, line ->
            val nextTimeMs = sortedLines.getOrNull(index + 1)?.timeMs
            val duration = if (nextTimeMs != null) nextTimeMs - line.timeMs else 0L
            line.copy(durationMs = duration)
        }

        return LyricsResult(
            lines = resultLines,
            source = LyricsSource.LRC_FILE,
        )
    }

    fun findCurrentLine(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                lines[mid].timeMs <= positionMs -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return (low - 1).coerceAtLeast(0)
    }
}
