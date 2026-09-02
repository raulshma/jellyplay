package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [mpvOpenableUrl] against the on-device failure shape: mpv's file
 * stream handler only strips the scheme for `file://…`, so the single-slash
 * `file:/…` URIs produced by [java.io.File.toURI] must be converted to bare
 * absolute paths before being handed to `sub-add`.
 */
class MpvOpenableUrlTest {

    @Test
    fun singleSlashFileUri_becomesAbsolutePath() {
        assertEquals(
            "/data/user/0/pkg/files/streaming-subtitles/abc/wyzie_1.srt",
            mpvOpenableUrl("file:/data/user/0/pkg/files/streaming-subtitles/abc/wyzie_1.srt"),
        )
    }

    @Test
    fun tripleSlashFileUri_becomesAbsolutePath() {
        assertEquals("/tmp/a.srt", mpvOpenableUrl("file:///tmp/a.srt"))
    }

    @Test
    fun encodedCharacters_decoded() {
        assertEquals("/data/subs/my file.srt", mpvOpenableUrl("file:/data/subs/my%20file.srt"))
    }

    @Test
    fun remoteAndContentSchemes_passThrough() {
        assertEquals("https://server/Subtitles/3/0/Stream.srt", mpvOpenableUrl("https://server/Subtitles/3/0/Stream.srt"))
        assertEquals("content://media/external/sub", mpvOpenableUrl("content://media/external/sub"))
    }

    @Test
    fun plainPath_passThrough() {
        assertEquals("/data/local/tmp/x.srt", mpvOpenableUrl("/data/local/tmp/x.srt"))
    }

    @Test
    fun malformedFileUri_fallsBackToRaw() {
        // A parse failure must degrade to the original string, never throw.
        assertEquals("file:%zz", mpvOpenableUrl("file:%zz"))
    }
}
