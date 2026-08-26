package com.raulshma.jellyplay.feature.player.video

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 8C: the session cluster moved to commonMain, so its pure helpers are
 * jvmTest-reachable for the first time. These tests pin the seam transform
 * that replaced `android.net.Uri.fromFile(file).toString()`: the produced
 * strings must carry the `file:///` form (empty authority + absolute path)
 * with Android-compatible percent-encoding. On Android every input path is
 * absolute (Linux root), so the JVM tests anchor under the platform's own
 * absolute base instead of hardcoding a POSIX root (which Windows would
 * resolve against the current drive).
 */
class FileUriStringTest {

    private val base = File("").absoluteFile

    @Test
    fun `absolute path produces the file triple-slash authority form`() {
        val uri = fileUriString(File(base, "Android/data/movie.mkv"))
        assertTrue(uri.startsWith("file:///"), uri)
        assertTrue(uri.endsWith("Android/data/movie.mkv"), uri)
    }

    @Test
    fun `spaces are percent-encoded like android Uri#fromFile`() {
        val uri = fileUriString(File(base, "My Movie (2024).mkv"))
        assertTrue(uri.startsWith("file:///"), uri)
        assertTrue(uri.endsWith("My%20Movie%20(2024).mkv"), uri)
    }

    @Test
    fun `non-ascii path segments never break the uri form`() {
        // On Android (the only production consumer) the Linux-fs encoder
        // percent-encodes non-ASCII exactly like Uri.fromFile; a Windows JVM
        // leaves it raw, so the portable assertion is the scheme + the file
        // being addressable, not the byte-level encoding.
        val uri = fileUriString(File(base, "фильм.mkv"))
        assertTrue(uri.startsWith("file:///"), uri)
        assertTrue(uri.endsWith("фильм.mkv") || uri.endsWith("%D1%84%D0%B8%D0%BB%D1%8C%D0%BC.mkv"), uri)
    }

    @Test
    fun `relative path round-trips with the file scheme`() {
        // Downloads resolve to absolute paths in practice; this pins that the
        // helper never crashes and always carries the file scheme.
        val uri = fileUriString(File("relative/clip.mp4"))
        assertTrue(uri.startsWith("file:"), uri)
    }
}

/**
 * Pins the PlayerSessionState defaults the ViewModel's collectors read before
 * the first load lands (empty-item guards in playbackSessionId resolution,
 * episode discovery branching, offline gating).
 */
class PlayerSessionStateDefaultsTest {

    @Test
    fun `defaults are unloaded, online and session-id-less`() {
        val state = PlayerSessionState()
        assertNull(state.currentItemId)
        assertNull(state.mediaDetail)
        assertNull(state.playSessionId)
        assertNull(state.offlineTrickplayDir)
        assertNull(state.streamUrl)
        assertFalse(state.isReady)
        assertFalse(state.isOffline)
        assertFalse(state.isDirectPlayForced)
        assertTrue(state.transcodeReasons.isEmpty())
        assertEquals("", state.title)
    }
}
