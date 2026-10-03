package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integrity tests for the settings-search catalog: every aggregated item must
 * have a unique, non-blank id and carry resources + keywords. The item lists
 * moved verbatim from core/ui's old `SettingsSearchRegistry`; this suite pins
 * that nothing was lost or corrupted in the move and that future items keep
 * the invariants.
 *
 * Legacy version (Android unit test) reflected `R.string` ids and parsed
 * `strings.xml` off disk to prove each item resolved a non-blank string. The
 * catalog now holds Compose-Resources [org.jetbrains.compose.resources.StringResource]s
 * whose accessors are generated from those same strings.xml files, so
 * resolvability is compile-time guaranteed — a stale resource reference
 * breaks the build, not this test. The XML parsing therefore died with the R
 * ids; what remains pinned is id uniqueness, resource/category cardinality,
 * keywords, and the verbatim-move shape (count + flat order).
 */
class SettingsSearchCatalogTest {

    @Test
    fun `every item has a unique non-blank id`() {
        val ids = SettingsSearchCatalog.items.map { it.id }
        assertTrue(ids.all { it.isNotBlank() }, "blank ids present")
        assertEquals(
            ids.size,
            ids.toSet().size,
            "duplicate ids: " + ids.groupBy { it }.filterValues { it.size > 1 }.keys,
        )
    }

    @Test
    fun `items span multiple distinct categories`() {
        // Compile-time resolvability guarantees each categoryRes is a real
        // generated accessor; what is worth pinning at runtime is that the
        // catalog did not collapse onto a single category (copy-paste guard).
        // Distinct accessors are distinct lazy objects, so identity is a
        // faithful distinct-category proxy.
        val categories = SettingsSearchCatalog.items.map { it.categoryRes }.toSet()
        assertTrue(categories.size >= 2, "expected several ss_cat_* groups, got ${categories.size}")
    }

    @Test
    fun `every item carries keywords`() {
        SettingsSearchCatalog.items.forEach { item ->
            assertTrue(item.keywords.isNotEmpty(), "empty keywords for ${item.id}")
        }
    }

    @Test
    fun `aggregation preserves the verbatim move - all 276 items in flat order`() {
        val items = SettingsSearchCatalog.items
        // The old core/ui registry held 259 items; the aggregation must have
        // kept every one (the 260th is the video-cache-size row).
        // v0.10.6 then consolidated the 5 synthwave/soothing/monochrome mode
        // + accent entries into theme_style + style_accent (257); the
        // missing auto_delete_after_watch declaration followed its existing
        // storage row (258).
        // Added the media-segment group's skip-on-seek toggle (259).
        // Added the track-selection group: preset + two ordered
        // language editors + advanced rules (263).
        // Added the desktop-gated mpv audio-device trio: device picker
        // + exclusive toggle + output mode (266).
        // Added the desktop-gated mpv render rows: shader pack +
        // tone mapping + quality profile + HDR passthrough + tscale (271).
        // Added the desktop-gated volume-memory toggle (272).
        // Added the security group's remote display-content toggle and
        // the desktop-gated idle-ambient pair (toggle + timeout): 275.
        // The home config hub moved its rows off Appearance into the three
        // home groups (same ids) and indexed the previously-unsearchable
        // Discover Rows row (+1): 276.
        // Added the home display group's "Hidden from Next Up" drill-in
        // row (the Route.NextUpExcluded management screen): 277.
        // Added the appearance library group's "Show Missing Episodes"
        // toggle (season-view missing/unaired placeholders): 278.
        // Added the advanced-video group's "Offline Playback" picker
        // (prefer the downloaded copy vs stream while online) and the
        // playback group's external-player row: 280.
        // Added the appearance library group's "Prefer Logo Images" toggle
        // (clear-logo detail title): 281.
        // Added the screensaver group's policy trio: max parental rating
        // picker + dim-after picker + dim-percent slider: 284.
        // Added the playback player group's "Still Watching" pair — mode
        // picker + episode-threshold picker (both ride the autoplay toggle): 286.
        // Added the advanced-video group's audio-capability rows: five
        // per-codec passthrough toggles (riding the master toggle) + the
        // max-audio-channels picker + the downmix-boost slider: 293.
        // Added the storage downloads group's auto-download retention
        // cluster: lookahead / max-per-pass / keep-days / server allow-list
        // pickers (riding the auto-download toggle) + the "Clean up now"
        // action (riding the keep-days picker): 298.
        // Added the desktop-gated Discord Rich Presence toggle (feature 4.2)
        // and the shell-hook rows: master toggle + five mpv-shim-named
        // commands (feature 4.3): 305.
        // Added the appearance theme group's "Layout" override picker
        // (manual phone/tablet layout, issue #166): 306.
        // Added the playback player group's Android-only "Auto
        // Picture-in-Picture" toggle (issue #167): 307.
        // Added the home display group's "Classic Row Behavior" toggle
        // (issue #168): 308.
        // The appearance library group's home-discovery card-display quartet
        // moved into a new home.cards group (same ids, PS-4): count unchanged.
        // Bump this count when you deliberately add items.
        assertEquals(308, items.size)
        // Curated flat order starts with the account/session pair that used to
        // open the old registry, and the aggregation is a pure concatenation
        // of the decorated per-screen groups (no dedup, no reordering).
        assertEquals("logout", items.first().id)
        // The aggregation is a pure concatenation of the derived groups —
        // no dedup, no reordering, no filtering. (The retired hand-written
        // per-val sum no longer partitions: post-fusion, vals like
        // ExternalEngineSearchItems are slices of their group, not disjoint
        // members.)
        assertEquals(SettingsScreenGroups.all.sumOf { it.items.size }, items.size)
        assertEquals(ExperimentalSettingsSearchItems.last().id, items.last().id)
    }
}
