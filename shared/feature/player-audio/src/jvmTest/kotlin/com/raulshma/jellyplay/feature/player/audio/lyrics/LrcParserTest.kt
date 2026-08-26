package com.raulshma.jellyplay.feature.player.audio.lyrics

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/** Unit tests for LrcParser – pure JVM, no Android dependencies. */
class LrcParserTest {

    // ─── parse: basic single-timestamp lines ──────────────────────────────────

    @Test
    fun parse_singleLine_returnsOneLine() {
        val lrc = "[00:01.00]Hello world"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.lines.size)
        assertEquals("Hello world", result.lines[0].text)
    }

    @Test
    fun parse_singleLine_correctTimeMs() {
        val lrc = "[00:01.00]Hello world"
        val result = LrcParser.parse(lrc)
        assertEquals(1_000L, result.lines[0].timeMs)
    }

    @Test
    fun parse_twoLines_returnsTwo() {
        val lrc = "[00:01.00]Line one\n[00:05.00]Line two"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.lines.size)
    }

    @Test
    fun parse_linesAreSortedByTime() {
        val lrc = "[00:05.00]Second\n[00:01.00]First"
        val result = LrcParser.parse(lrc)
        assertEquals("First", result.lines[0].text)
        assertEquals("Second", result.lines[1].text)
    }

    @Test
    fun parse_sourceIsLrcFile() {
        val lrc = "[00:01.00]Hello"
        val result = LrcParser.parse(lrc)
        assertEquals(LyricsSource.LRC_FILE, result.source)
    }

    @Test
    fun parse_emptyContent_returnsEmptyLines() {
        val result = LrcParser.parse("")
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun parse_metadataLinesSkipped() {
        val lrc = "[ti:Song Title]\n[ar:Artist]\n[00:01.00]First lyric line"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.lines.size)
        assertEquals("First lyric line", result.lines[0].text)
    }

    @Test
    fun parse_offsetApplied() {
        val lrc = "[offset:500]\n[00:01.00]Hello"
        val result = LrcParser.parse(lrc)
        // 1000ms + 500ms offset
        assertEquals(1_500L, result.lines[0].timeMs)
    }

    @Test
    fun parse_negativeOffsetApplied() {
        val lrc = "[offset:-200]\n[00:02.00]Hello"
        val result = LrcParser.parse(lrc)
        // 2000ms - 200ms offset
        assertEquals(1_800L, result.lines[0].timeMs)
    }

    @Test
    fun parse_blankLineSkipped() {
        val lrc = "[00:01.00]Line 1\n\n[00:03.00]Line 2"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.lines.size)
    }

    // ─── parse: multi-timestamp lines ─────────────────────────────────────────

    @Test
    fun parse_multipleTimestamps_oneLine_createMultipleLines() {
        val lrc = "[00:01.00][00:05.00]Chorus line"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.lines.size)
        result.lines.forEach { assertEquals("Chorus line", it.text) }
    }

    // ─── parse: duration calculation ──────────────────────────────────────────

    @Test
    fun parse_lineDuration_isTimeDifference() {
        val lrc = "[00:01.00]First\n[00:05.00]Second"
        val result = LrcParser.parse(lrc)
        assertEquals(4_000L, result.lines[0].durationMs)  // 5000 - 1000
    }

    @Test
    fun parse_lastLine_durationIsZero() {
        val lrc = "[00:01.00]First\n[00:05.00]Last"
        val result = LrcParser.parse(lrc)
        assertEquals(0L, result.lines[1].durationMs)
    }

    // ─── parse: enhanced word timing ──────────────────────────────────────────

    @Test
    fun parse_wordTimings_parsedCorrectly() {
        val lrc = "[00:10.00][00:10.50]Hello [00:10.80]World"
        val result = LrcParser.parse(lrc)
        // First line at 10000ms has inline word timings
        val lineWithWords = result.lines.find { it.words.isNotEmpty() }
        assertTrue(lineWithWords != null)
        assertEquals(2, lineWithWords!!.words.size)
        assertEquals("Hello", lineWithWords.words[0].text)
        assertEquals("World", lineWithWords.words[1].text)
    }

    // ─── findCurrentLine ──────────────────────────────────────────────────────

    @Test
    fun findCurrentLine_emptyLines_returnsMinusOne() {
        assertEquals(-1, LrcParser.findCurrentLine(emptyList(), 5_000L))
    }

    @Test
    fun findCurrentLine_beforeFirstLine_returnsZero() {
        val lines = listOf(LyricsLine(timeMs = 5_000L, text = "Hello"))
        // position 0 is before first line but we clamp to 0
        assertEquals(0, LrcParser.findCurrentLine(lines, 0L))
    }

    @Test
    fun findCurrentLine_atFirstLine_returnsZero() {
        val lines = listOf(
            LyricsLine(timeMs = 1_000L, text = "First"),
            LyricsLine(timeMs = 5_000L, text = "Second"),
        )
        assertEquals(0, LrcParser.findCurrentLine(lines, 1_000L))
    }

    @Test
    fun findCurrentLine_betweenLines_returnsFirstLine() {
        val lines = listOf(
            LyricsLine(timeMs = 1_000L, text = "First"),
            LyricsLine(timeMs = 5_000L, text = "Second"),
        )
        assertEquals(0, LrcParser.findCurrentLine(lines, 3_000L))
    }

    @Test
    fun findCurrentLine_atSecondLine_returnsOne() {
        val lines = listOf(
            LyricsLine(timeMs = 1_000L, text = "First"),
            LyricsLine(timeMs = 5_000L, text = "Second"),
        )
        assertEquals(1, LrcParser.findCurrentLine(lines, 5_000L))
    }

    @Test
    fun findCurrentLine_afterLastLine_returnsLastIndex() {
        val lines = listOf(
            LyricsLine(timeMs = 1_000L, text = "First"),
            LyricsLine(timeMs = 5_000L, text = "Second"),
        )
        assertEquals(1, LrcParser.findCurrentLine(lines, 99_000L))
    }

    // ─── findCurrentWordIndex ─────────────────────────────────────────────────

    @Test
    fun findCurrentWordIndex_noWords_returnsMinusOne() {
        val line = LyricsLine(timeMs = 1_000L, text = "Hello")
        assertEquals(-1, LrcParser.findCurrentWordIndex(line, 0L))
    }

    @Test
    fun findCurrentWordIndex_beforeFirstWord_returnsMinusOne() {
        val line = LyricsLine(
            timeMs = 1_000L,
            text = "Hello World",
            words = listOf(
                LyricsWord(timeMs = 1_500L, text = "Hello"),
                LyricsWord(timeMs = 2_000L, text = "World"),
            )
        )
        // positionInLine = 0ms, first word starts at 500ms into line (1500-1000)
        assertEquals(-1, LrcParser.findCurrentWordIndex(line, 0L))
    }

    @Test
    fun findCurrentWordIndex_atFirstWord_returnsZero() {
        val line = LyricsLine(
            timeMs = 1_000L,
            text = "Hello World",
            words = listOf(
                LyricsWord(timeMs = 1_000L, text = "Hello"),
                LyricsWord(timeMs = 1_500L, text = "World"),
            )
        )
        // positionInLine = 0ms = at first word start (1000 - 1000 = 0)
        assertEquals(0, LrcParser.findCurrentWordIndex(line, 0L))
    }

    @Test
    fun findCurrentWordIndex_atSecondWord_returnsOne() {
        val line = LyricsLine(
            timeMs = 1_000L,
            text = "Hello World",
            words = listOf(
                LyricsWord(timeMs = 1_000L, text = "Hello"),
                LyricsWord(timeMs = 1_500L, text = "World"),
            )
        )
        // positionInLine = 500ms = at second word start (1500 - 1000)
        assertEquals(1, LrcParser.findCurrentWordIndex(line, 500L))
    }

    // ─── hasWordTimings ────────────────────────────────────────────────────────

    @Test
    fun hasWordTimings_noWords_returnsFalse() {
        val line = LyricsLine(timeMs = 0L, text = "Hello")
        assertFalse(LrcParser.hasWordTimings(line))
    }

    @Test
    fun hasWordTimings_withWords_returnsTrue() {
        val line = LyricsLine(
            timeMs = 0L,
            text = "Hello",
            words = listOf(LyricsWord(timeMs = 0L, text = "Hello")),
        )
        assertTrue(LrcParser.hasWordTimings(line))
    }

    // ─── findCurrentWordIndex: past-last-word edge case ────────────────────────

    @Test
    fun findCurrentWordIndex_pastLastWord_returnsLastIndex() {
        val line = LyricsLine(
            timeMs = 1_000L,
            text = "Hello World",
            words = listOf(
                LyricsWord(timeMs = 1_000L, text = "Hello"),
                LyricsWord(timeMs = 1_500L, text = "World"),
            )
        )
        // positionInLine far past the last word start (500ms in) should clamp
        // to the last word index (1), not return -1.
        assertEquals(1, LrcParser.findCurrentWordIndex(line, 10_000L))
    }

    @Test
    fun findCurrentWordIndex_singleWord_alwaysAtZero() {
        val line = LyricsLine(
            timeMs = 0L,
            text = "Solo",
            words = listOf(LyricsWord(timeMs = 0L, text = "Solo")),
        )
        assertEquals(0, LrcParser.findCurrentWordIndex(line, 0L))
        assertEquals(0, LrcParser.findCurrentWordIndex(line, 5_000L))
    }

    @Test
    fun findCurrentWordIndex_threeWords_advancesAcrossBoundaries() {
        val line = LyricsLine(
            timeMs = 10_000L,
            text = "A B C",
            words = listOf(
                LyricsWord(timeMs = 10_000L, text = "A"),
                LyricsWord(timeMs = 10_500L, text = "B"),
                LyricsWord(timeMs = 11_000L, text = "C"),
            )
        )
        assertEquals(0, LrcParser.findCurrentWordIndex(line, 0L))      // at A (10k-10k=0)
        assertEquals(1, LrcParser.findCurrentWordIndex(line, 500L))    // at B (10.5k-10k=500)
        assertEquals(2, LrcParser.findCurrentWordIndex(line, 1_000L))  // at C (11k-10k=1000)
        assertEquals(2, LrcParser.findCurrentWordIndex(line, 9_999L))  // past C → clamp
    }

    // ─── parse: 3-digit millisecond timestamp precision ────────────────────────

    @Test
    fun parse_threeDigitMsTimestamp_parsedCorrectly() {
        val lrc = "[00:01.500]Hello"
        val result = LrcParser.parse(lrc)
        assertEquals(1_500L, result.lines[0].timeMs)
    }

    @Test
    fun parse_threeDigitMsTimestamp_wordTimingParsedCorrectly() {
        // Single line-start timestamp, then inline word timestamps.
        val lrc = "[00:10.000]Hi [00:10.250]there"
        val result = LrcParser.parse(lrc)
        val line = result.lines.first { it.words.isNotEmpty() }
        assertEquals(2, line.words.size)
        // "Hi" precedes the first inline timestamp → anchored at line start (10000).
        assertEquals(10_000L, line.words[0].timeMs)
        assertEquals("Hi", line.words[0].text)
        // "there" follows its inline [00:10.250] timestamp.
        assertEquals(10_250L, line.words[1].timeMs)
        assertEquals("there", line.words[1].text)
    }

    // ─── parse: word durations (computeWordDurations branches) ─────────────────

    @Test
    fun parse_wordDuration_isDifferenceToNextWord() {
        val lrc = "[00:10.00]A [00:10.50]B\n[00:11.00]Next"
        val result = LrcParser.parse(lrc)
        val line = result.lines.first { it.words.isNotEmpty() }
        // Word A (10000) → B (10500): duration 500ms
        assertEquals(500L, line.words[0].durationMs)
    }

    @Test
    fun parse_lastWordDuration_usesRemainingLineDuration() {
        // Line 10s→11s (duration 1000ms); words A(10000), B(10500).
        // B is the last word → duration = lineDuration - (B.time - A.time) = 1000 - 500 = 500.
        val lrc = "[00:10.00]A [00:10.50]B\n[00:11.00]Next"
        val result = LrcParser.parse(lrc)
        val line = result.lines.first { it.words.isNotEmpty() }
        assertEquals(500L, line.words[1].durationMs)
    }

    @Test
    fun parse_lastLineWords_durationZeroWhenNoLineDuration() {
        val lrc = "[00:10.00]A [00:10.50]B"
        val result = LrcParser.parse(lrc)
        val line = result.lines.first { it.words.isNotEmpty() }
        // A has next word B → 500ms; B is last with no following line → 0ms.
        assertEquals(500L, line.words[0].durationMs)
        assertEquals(0L, line.words[1].durationMs)
    }

    // ─── parse: offset combined with inline word timings ───────────────────────

    @Test
    fun parse_offsetAppliedToWordTimings() {
        val lrc = "[offset:1000]\n[00:05.00]Hi"
        val result = LrcParser.parse(lrc)
        val line = result.lines[0]
        // Line time 5000 + 1000 offset = 6000.
        assertEquals(6_000L, line.timeMs)
    }

    // ─── parse: metadata + offset + multi-timestamp combination ────────────────

    @Test
    fun parse_metadataOffsetAndMultitimestamp_combine() {
        val lrc = "[ti:Title]\n[offset:500]\n[00:01.00][00:03.00]Line"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.lines.size)
        // 1000 + 500 = 1500; 3000 + 500 = 3500
        assertEquals(1_500L, result.lines[0].timeMs)
        assertEquals(3_500L, result.lines[1].timeMs)
        result.lines.forEach { assertEquals("Line", it.text) }
    }

    // ─── hasKaraokeLyrics state contract ───────────────────────────────────────

    @Test
    fun parse_hasKaraokeLyricsTrueWhenWordTimingsPresent() {
        val lrc = "[00:10.00]Hello [00:10.50]World"
        val result = LrcParser.parse(lrc)
        assertTrue(result.lines.any { it.words.isNotEmpty() })
    }
}
