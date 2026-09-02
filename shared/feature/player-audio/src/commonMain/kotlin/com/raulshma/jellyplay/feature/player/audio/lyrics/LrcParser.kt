package com.raulshma.jellyplay.feature.player.audio.lyrics

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord

object LrcParser {

    private val LINE_REGEX = Regex("""^(\[(\d{1,2}):(\d{2}\.\d{2,3})])+(.+)$""")
    private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})]""")
    private val OFFSET_REGEX = Regex("""\[offset:([+-]?\d+)]""")
    private val WHITESPACE_SPLIT = Regex("""\s+""")
    private val METADATA_TAG_REGEX = Regex("""^\[\w+:.+]$""")

    fun parse(lrcContent: String): LyricsResult {
        val lines = mutableListOf<LyricsLine>()
        var offsetMs = 0L

        lrcContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val offsetMatch = OFFSET_REGEX.find(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLong()
                return@forEach
            }

            if (line.matches(METADATA_TAG_REGEX) && !line.matches(TIME_REGEX)) {
                return@forEach
            }

            val lineMatch = LINE_REGEX.find(line) ?: return@forEach
            val text = lineMatch.groupValues.last()
            val prefixEnd = line.length - text.length
            val prefix = line.substring(0, prefixEnd)
            val lineStartTimes = TIME_REGEX.findAll(prefix).map { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toDouble()
                minutes * 60_000 + (seconds * 1000).toLong()
            }.toList()

            lineStartTimes.forEach { timeMs ->
                val adjustedTime = timeMs + offsetMs
                val words = parseInlineWordTimings(text, adjustedTime)
                val adjustedWords = if (words.isNotEmpty()) {
                    words.map { it.copy(timeMs = it.timeMs + offsetMs) }
                } else emptyList()
                lines.add(
                    LyricsLine(
                        timeMs = adjustedTime,
                        text = text,
                        words = adjustedWords,
                    )
                )
            }
        }

        val sortedLines = lines.sortedBy { it.timeMs }
        val resultLines = sortedLines.mapIndexed { index, line ->
            val nextTimeMs = sortedLines.getOrNull(index + 1)?.timeMs
            val lineDuration = if (nextTimeMs != null) nextTimeMs - line.timeMs else 0L
            val wordsWithDuration = computeWordDurations(line.words, lineDuration)
            line.copy(
                durationMs = lineDuration,
                words = wordsWithDuration,
            )
        }

        return LyricsResult(
            lines = resultLines,
            source = LyricsSource.LRC_FILE,
        )
    }

    /**
     * Parses Enhanced LRC word timings: "[00:12.34]Hello [00:12.89]world [00:13.45]test"
     * Returns a list of [LyricsWord] in order. Returns empty list if no inline
     * timestamps are present.
     */
    private fun parseInlineWordTimings(text: String, lineStartTimeMs: Long): List<LyricsWord> {
        if (text.isBlank()) return emptyList()
        val matches = TIME_REGEX.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val words = mutableListOf<LyricsWord>()

        // 1. First word (before the first inline timestamp)
        val firstMatch = matches.first()
        val firstWordText = text.substring(0, firstMatch.range.first).trim()
        if (firstWordText.isNotEmpty()) {
            words.add(
                LyricsWord(
                    timeMs = lineStartTimeMs,
                    text = firstWordText,
                    durationMs = 0L
                )
            )
        }

        // 2. Subsequent words
        matches.forEachIndexed { index, match ->
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toDouble()
            val timeMs = minutes * 60_000 + (seconds * 1000).toLong()
            val wordStart = match.range.last + 1
            val wordEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val rawWord = text.substring(wordStart, wordEnd).trim()
            if (rawWord.isNotEmpty()) {
                words.add(
                    LyricsWord(
                        timeMs = timeMs,
                        text = rawWord,
                        durationMs = 0L,
                    )
                )
            }
        }

        return words
    }

    private fun computeWordDurations(words: List<LyricsWord>, lineDuration: Long): List<LyricsWord> {
        if (words.isEmpty()) return emptyList()
        return words.mapIndexed { index, word ->
            val nextTime = words.getOrNull(index + 1)?.timeMs
            val duration = when {
                nextTime != null -> nextTime - word.timeMs
                lineDuration > 0L -> lineDuration - (word.timeMs - words.first().timeMs)
                else -> 0L
            }
            word.copy(durationMs = duration.coerceAtLeast(0L))
        }
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

    /**
     * Binary search for the current word index within a line, given a position
     * offset from the line's start time in milliseconds. Returns -1 if no word
     * has started yet, or the index of the last word that has ended if the
     * position is past the line's last word.
     */
    fun findCurrentWordIndex(line: LyricsLine, positionInLineMs: Long): Int {
        if (line.words.isEmpty()) return -1
        if (positionInLineMs < line.words.first().timeMs - line.timeMs) return -1
        var low = 0
        var high = line.words.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val wordStartInLine = line.words[mid].timeMs - line.timeMs
            when {
                wordStartInLine <= positionInLineMs -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return (low - 1).coerceAtLeast(0)
    }

    fun hasWordTimings(line: LyricsLine): Boolean = line.words.isNotEmpty()
}
