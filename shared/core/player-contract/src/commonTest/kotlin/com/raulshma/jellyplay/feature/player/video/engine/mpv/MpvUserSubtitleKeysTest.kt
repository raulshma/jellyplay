package com.raulshma.jellyplay.feature.player.video.engine.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the per-key mpv.conf subtitle ownership helper (issue #165):
 * which `sub-*` keys the user claims from mpv.conf / extra config, and how
 * app-side style entry lists are filtered against that set.
 */
class MpvUserSubtitleKeysTest {

    // ── ownedKeys: parsing + scope ──────────────────────────────────────────

    @Test
    fun `empty sources own nothing`() {
        assertTrue(MpvUserSubtitleKeys.ownedKeys(null, null).isEmpty())
        assertTrue(MpvUserSubtitleKeys.ownedKeys("", "").isEmpty())
    }

    @Test
    fun `conf and extra config sub keys are unioned`() {
        val owned = MpvUserSubtitleKeys.ownedKeys(
            mpvConfText = """
                # user styling
                sub-color=#FFFF0000
                sub-font-size=80
                vo=gpu-next
            """.trimIndent(),
            extraConfigText = "sub-pos=95\nscale=ewa_lanczossharp",
        )
        assertEquals(setOf("sub-color", "sub-font-size", "sub-pos"), owned)
    }

    @Test
    fun `bare flags claim ownership`() {
        val owned = MpvUserSubtitleKeys.ownedKeys(null, "sub-bold")
        assertEquals(setOf("sub-bold"), owned)
    }

    @Test
    fun `comments and blanks are ignored`() {
        val owned = MpvUserSubtitleKeys.ownedKeys("# sub-color=red\n\n  # sub-bold\n", null)
        assertTrue(owned.isEmpty())
    }

    @Test
    fun `functional keys never become user-owned`() {
        val owned = MpvUserSubtitleKeys.ownedKeys(
            "sub-visibility=no\nsub-delay=-0.5\nsub-use-margins=yes\nsub-font-provider=fontconfig\nsub-color=green",
            "secondary-sub-delay=1\nsub-ass-force-margins=yes\nsub-fonts-dir=/tmp\nsub-border-style=box",
        )
        assertEquals(setOf("sub-color", "sub-border-style"), owned)
    }

    // ── filterOwned ─────────────────────────────────────────────────────────

    @Test
    fun `filter is a no-op on an empty owned set`() {
        val entries = listOf("sub-color" to "white", "sub-bold" to "yes")
        assertEquals(entries, MpvUserSubtitleKeys.filterOwned(entries, emptySet()))
    }

    @Test
    fun `owned keys are dropped order preserved`() {
        val entries = listOf(
            "sub-color" to "#FFFFFFFF",
            "sub-border-style" to "outline-and-shadow",
            "sub-bold" to "no",
            "sub-italic" to "no",
        )
        val filtered = MpvUserSubtitleKeys.filterOwned(entries, setOf("sub-color", "sub-italic"))
        assertEquals(listOf("sub-border-style" to "outline-and-shadow", "sub-bold" to "no"), filtered)
    }

    @Test
    fun `keys under a profile section are not attributed`() {
        val owned = MpvUserSubtitleKeys.ownedKeys(
            """
                sub-color=#FFFF0000
                [myprofile]
                sub-pos=95
                sub-font-size=80
                [other]
                sub-bold=yes
            """.trimIndent(),
            null,
        )
        // Only the top-level key claims ownership; the section keys would
        // never be written by the app either, so attributing them would
        // yield the keys to nobody.
        assertEquals(setOf("sub-color"), owned)
    }

    @Test
    fun `section header in extra config does not stop later top-level conf keys`() {
        val owned = MpvUserSubtitleKeys.ownedKeys(
            mpvConfText = "sub-color=green",
            extraConfigText = "[profile]\nsub-pos=95",
        )
        assertEquals(setOf("sub-color"), owned)
    }

    @Test
    fun `utf8 bom on the first line does not hide the key`() {
        val owned = MpvUserSubtitleKeys.ownedKeys("\uFEFFsub-color=#FFFF0000\nsub-bold=yes", null)
        assertEquals(setOf("sub-color", "sub-bold"), owned)
    }

    // ── unsalvageableConfKeys: mpv conf parser drops unquoted # values ──────

    @Test
    fun `unquoted hash-only values are flagged`() {
        val flagged = MpvUserSubtitleKeys.unsalvageableConfKeys(
            "sub-color=#FF0000\nsub-back-color=#80FF0000\nsub-font-size=80",
        )
        // Everything from the first unquoted # is a comment for mpv's conf
        // parser, so these parse as empty values → mpv default (white).
        assertEquals(setOf("sub-color", "sub-back-color"), flagged)
    }

    @Test
    fun `quoted hash values are not flagged`() {
        val flagged = MpvUserSubtitleKeys.unsalvageableConfKeys(
            """
                sub-color="#FF0000"
                sub-back-color='#80FF0000'
                sub-border-color=%9%#FFFF0000
            """.trimIndent(),
        )
        assertTrue(flagged.isEmpty())
    }

    @Test
    fun `unterminated quotes are not flagged`() {
        // A stray opening quote/percent prefix suppresses flagging even though
        // the value never closes — pinned deliberately: the check is a cheap
        // starts-with guard, not a full mpv conf parser.
        val flagged = MpvUserSubtitleKeys.unsalvageableConfKeys(
            """
                sub-color="#FF0000
                sub-back-color='#80FF0000
            """.trimIndent(),
        )
        assertTrue(flagged.isEmpty())
    }

    @Test
    fun `value keeping a non-empty prefix before hash is not flagged`() {
        val flagged = MpvUserSubtitleKeys.unsalvageableConfKeys(
            "sub-font-size=80 # big\nsub-color=green # fallback\nsub-bold=yes",
        )
        assertTrue(flagged.isEmpty())
    }

    @Test
    fun `non styling keys and profile sections are never flagged`() {
        val flagged = MpvUserSubtitleKeys.unsalvageableConfKeys(
            """
                vo=#gpu-next
                sub-visibility=#always-app-owned
                [profile]
                sub-color=#FF0000
            """.trimIndent(),
        )
        assertTrue(flagged.isEmpty())
    }

    @Test
    fun `unsalvageable detection ignores null and handles blank input`() {
        assertTrue(MpvUserSubtitleKeys.unsalvageableConfKeys(null).isEmpty())
        assertTrue(MpvUserSubtitleKeys.unsalvageableConfKeys("").isEmpty())
    }

    // ── MpvSubtitleOwnership: the UI read-side snapshot ─────────────────────

    @Test
    fun `NONE is inactive so the UI notice stays hidden`() {
        assertFalse(MpvSubtitleOwnership.NONE.isActive)
    }

    @Test
    fun `dropped-only conf keys still activate the notice`() {
        // Owned AND valueless (unquoted `#`) is the case that looks like a
        // broken control from outside — it must render its own notice line
        // even when no other styling key is owned.
        assertTrue(
            MpvSubtitleOwnership(confKeysDroppedByParser = setOf("sub-color")).isActive,
        )
    }
}
