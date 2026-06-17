package com.raulshma.jellyplay.feature.player.audio.lyrics

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
