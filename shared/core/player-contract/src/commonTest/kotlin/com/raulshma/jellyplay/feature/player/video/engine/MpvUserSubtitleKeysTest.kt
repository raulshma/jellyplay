package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
