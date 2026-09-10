package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.settingssearch.filterFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ratchet for the catalog↔platform contract: a search item whose row is
 * platform-gated must carry the tag, so a surface never offers a hit whose
 * target row cannot exist there. [SettingsSearchCatalogTest]'s hand-bumped
 * integrity counts stay on the unfiltered [SettingsSearchCatalog.items];
 * this pins the platform dimension on top.
 */
class SettingsSearchCatalogPlatformFilterTest {

    private val androidOnlyIds = setOf(
        "dynamic_theming",
        "seek_duration",
        "orientation",
        "gestures",
        "gesture_indicator_side",
        "android_tv_watch_next",
        "tv_zoom_mode",
        "biometric_lock",
        "system_notification_settings",
        "app_language",
        "audio_caching_enabled",
        "audio_cache_size",
        "audio_prefetch_lookahead",
        "audio_prefetch_backfill",
        "audio_cache_clear",
        "audio_cache_network_policy",
    )

    @Test
    fun `desktop-filtered catalog drops every android-only item`() {
        val desktopIds = SettingsSearchCatalog.items.filterFor(PlatformKind.DESKTOP).map { it.id }.toSet()

        val leaked = androidOnlyIds.filter { it in desktopIds }
        assertEquals(emptyList(), leaked, "Android-only ids leaked into the desktop catalog: $leaked")
    }

    @Test
    fun `desktop-filtered catalog keeps whole android-only lists out`() {
        val desktopIds = SettingsSearchCatalog.items.filterFor(PlatformKind.DESKTOP).map { it.id }.toSet()

        assertTrue(
            desktopIds.intersect(NotificationSettingsSearchItems.map { it.id }.toSet()).isEmpty(),
            "the notification screen is Android-only — none of its items may surface on desktop",
        )
        assertTrue(
            desktopIds.intersect(ExoPlayerEngineSearchItems.map { it.id }.toSet()).isEmpty(),
            "desktop ships no ExoPlayer engine — no ExoPlayer config items",
        )
        assertTrue(
            desktopIds.intersect(VlcEngineSearchItems.map { it.id }.toSet()).isEmpty(),
            "desktop ships no VLC engine — no VLC config items",
        )
    }

    @Test
    fun `desktop-filtered catalog keeps the shared surface intact`() {
        val desktopIds = SettingsSearchCatalog.items.filterFor(PlatformKind.DESKTOP).map { it.id }.toSet()

        // The engine picker itself and the mpv config items ship on BOTH
        // platforms (Android's factory builds a real mpv engine).
        assertTrue("player_engine" in desktopIds)
        assertTrue(
            MpvEngineSearchItems.all { it.id in desktopIds },
            "mpv items stay platform-wide",
        )
    }

    @Test
    fun `every ratcheted id exists and is tagged android-only`() {
        // Renaming an id without updating [androidOnlyIds] fails here — not
        // silently as an untagged stale hit.
        val byId = SettingsSearchCatalog.items.associateBy { it.id }
        val broken = androidOnlyIds.filter { id ->
            val item = byId[id] ?: return@filter true
            item.platforms != ANDROID_ONLY_PLATFORMS
        }
        assertEquals(emptyList(), broken, "missing from the catalog or not ANDROID-tagged: $broken")
    }

    @Test
    fun `android-filtered catalog is the full catalog today`() {
        // No desktop-only tags exist yet; if a desktop-only setting arrives,
        // this assertion is the place that must be consciously updated.
        assertEquals(
            SettingsSearchCatalog.items.map { it.id },
            SettingsSearchCatalog.items.filterFor(PlatformKind.ANDROID).map { it.id },
        )
    }
}
