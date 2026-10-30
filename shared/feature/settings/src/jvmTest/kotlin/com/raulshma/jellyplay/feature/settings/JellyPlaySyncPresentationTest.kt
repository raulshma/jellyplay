package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.Book2
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Upload
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the sync screen's presentation decisions as plain folds
 * ([syncNamespaceFace], [syncNamespaceSubtitleParts], [SyncOpFaces]) — the
 * log-line-parse precedent: the decision is JVM-testable beside this test,
 * the screen only maps each verdict to its resolved string / drawn icon.
 */
class JellyPlaySyncPresentationTest {

    // ── syncNamespaceFace (the 11-namespace table) ──────────────────────

    @Test
    fun `every known namespace carries title, description and icon`() {
        val expected = mapOf(
            "prefs" to Triple(
                "settings_jellyplay_sync_ns_prefs_title",
                "settings_jellyplay_sync_ns_prefs_desc",
                Tabler.Outline.AdjustmentsHorizontal,
            ),
            "books" to Triple(
                "settings_jellyplay_sync_ns_books_title",
                "settings_jellyplay_sync_ns_books_desc",
                Tabler.Outline.Book,
            ),
            "search" to Triple(
                "settings_jellyplay_sync_ns_search_title",
                "settings_jellyplay_sync_ns_search_desc",
                Tabler.Outline.Search,
            ),
            "cw" to Triple(
                "settings_jellyplay_sync_ns_cw_title",
                "settings_jellyplay_sync_ns_cw_desc",
                Tabler.Outline.PlayerPlay,
            ),
            "reader" to Triple(
                "settings_jellyplay_sync_ns_reader_title",
                "settings_jellyplay_sync_ns_reader_desc",
                Tabler.Outline.Book2,
            ),
            "homelayout" to Triple(
                "settings_jellyplay_sync_ns_homelayout_title",
                "settings_jellyplay_sync_ns_homelayout_desc",
                Tabler.Outline.LayoutGrid,
            ),
            "integrations" to Triple(
                "settings_jellyplay_sync_ns_integrations_title",
                "settings_jellyplay_sync_ns_integrations_desc",
                Tabler.Outline.Apps,
            ),
            "itemprefs" to Triple(
                "settings_jellyplay_sync_ns_itemprefs_title",
                "settings_jellyplay_sync_ns_itemprefs_desc",
                Tabler.Outline.Movie,
            ),
            "playlists" to Triple(
                "settings_jellyplay_sync_ns_playlists_title",
                "settings_jellyplay_sync_ns_playlists_desc",
                Tabler.Outline.Playlist,
            ),
            "ui" to Triple(
                "settings_jellyplay_sync_ns_ui_title",
                "settings_jellyplay_sync_ns_ui_desc",
                Tabler.Outline.Palette,
            ),
        )
        // The table has exactly these ten known faces + the unknown arm — a
        // new namespace lands here or rides the fallback, never silently.
        assertEquals(10, expected.size)

        expected.forEach { (ns, face) ->
            val actual = syncNamespaceFace(ns)
            assertEquals(face.first, actual.titleRes?.key, "title of $ns")
            assertEquals(face.second, actual.descriptionRes?.key, "description of $ns")
            assertSame(face.third, actual.icon, "icon of $ns")
            assertEquals("", actual.fallbackTitle, "fallback title of $ns")
        }
    }

    @Test
    fun `namespace face lookup is case-insensitive`() {
        // The server's payload is expected lowercase; a stray uppercase must
        // not lose the face.
        assertEquals("settings_jellyplay_sync_ns_prefs_title", syncNamespaceFace("PREFS").titleRes?.key)
    }

    @Test
    fun `unknown namespace folds to title-cased raw key and the database icon`() {
        val face = syncNamespaceFace("customStuff")
        assertNull(face.titleRes)
        assertNull(face.descriptionRes)
        assertSame(Tabler.Outline.Database, face.icon)
        assertEquals("CustomStuff", face.fallbackTitle)
    }

    // ── syncNamespaceSubtitleParts (the subtitle gate order) ────────────

    private fun row(
        toggleable: Boolean = true,
        enabled: Boolean = true,
        pending: Int = 0,
        keys: Int = 0,
        bytes: Long = 0,
    ) = JellyPlaySyncNamespaceRow(
        ns = "prefs",
        keys = keys,
        bytes = bytes,
        pending = pending,
        toggleable = toggleable,
        enabled = enabled,
    )

    @Test
    fun `master-off arm suppresses the namespace-off and pending notes`() {
        val parts = syncNamespaceSubtitleParts(
            namespace = row(pending = 3, keys = 4, bytes = 8),
            description = "General settings",
            masterSyncEnabled = false,
        )

        assertEquals(
            listOf(
                SyncNamespaceSubtitlePart.MasterOffHint,
                SyncNamespaceSubtitlePart.Description("General settings"),
                SyncNamespaceSubtitlePart.Usage(4, 8),
            ),
            parts,
        )
    }

    @Test
    fun `disabled toggleable namespace shows the off note, not the master hint`() {
        val parts = syncNamespaceSubtitleParts(
            namespace = row(enabled = false, pending = 1, keys = 2, bytes = 4),
            description = "",
            masterSyncEnabled = true,
        )

        assertEquals(
            listOf(
                SyncNamespaceSubtitlePart.NamespaceOff,
                SyncNamespaceSubtitlePart.Pending(1),
                SyncNamespaceSubtitlePart.Usage(2, 4),
            ),
            parts,
        )
    }

    @Test
    fun `non-toggleable namespace never claims to be off`() {
        // Server-only usage rows render usage without a toggle — the off arm
        // requires the row to be toggleable.
        val parts = syncNamespaceSubtitleParts(
            namespace = row(toggleable = false, enabled = false),
            description = "",
            masterSyncEnabled = true,
        )

        assertTrue(parts.none { it is SyncNamespaceSubtitlePart.NamespaceOff })
        assertTrue(parts.none { it is SyncNamespaceSubtitlePart.Usage })
        assertEquals(emptyList(), parts)
    }

    @Test
    fun `zero-usage namespace suppresses the usage line and pending zero suppresses the count`() {
        val parts = syncNamespaceSubtitleParts(
            namespace = row(pending = 0, keys = 0, bytes = 0),
            description = "General settings",
            masterSyncEnabled = true,
        )

        assertEquals(listOf(SyncNamespaceSubtitlePart.Description("General settings")), parts)
    }

    // ── SyncOpFaces (the history rows' op faces) ────────────────────────

    @Test
    fun `wire ops carry their declared faces`() {
        assertEquals(
            "settings_jellyplay_sync_op_push",
            syncOpFace("push").second.key,
        )
        assertSame(Tabler.Outline.Upload, syncOpFace("push").first)
        assertEquals("settings_jellyplay_sync_op_pull", syncOpFace("pull").second.key)
        assertSame(Tabler.Outline.ArrowDown, syncOpFace("pull").first)
        assertEquals("settings_jellyplay_sync_op_reset", syncOpFace("reset").second.key)
        assertSame(Tabler.Outline.Refresh, syncOpFace("reset").first)
    }

    @Test
    fun `op face lookup is case-insensitive and unknown ops get the generic arrow`() {
        assertEquals("settings_jellyplay_sync_op_push", syncOpFace("PUSH").second.key)
        assertEquals("settings_jellyplay_sync_op_unknown", syncOpFace("compact").second.key)
        assertSame(Tabler.Outline.ArrowUp, syncOpFace("compact").first)
    }
}
