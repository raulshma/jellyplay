package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.audio.AudioPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCachePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.engine.PlayerEnginePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.library.LibraryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.notification.NotificationPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflinePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.navigation.NavigationPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.reader.ReaderPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitlePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfilePreferenceSpecs
import kotlin.test.Test
import kotlin.test.fail

/**
 * The spec↔diff cross-pin guard for the Factory Reset review (the
 * PreferenceCategoryPresentationTest self-consistency pins' missing twin):
 * every SPEC row that participates in a reset category AND declares a search
 * face (a user-facing knob — the same search tier the settings screens
 * derive from) must have a [DiffField] row in that category's
 * [PreferenceCategoryViews] registry, matched by the label resource KEY the
 * spec's [PreferenceSearchSpec.titleKey] names. Without this pin a preference
 * added everywhere BUT the diff registry is silently absent from the
 * reset/import review — the user resets a knob they were never shown
 * (e.g. a layout-override pair that is declared, projected into the slice,
 * and diff-registered by no row).
 *
 * Match rule: key-level (spec titleKey == DiffField labelRes key) — the same
 * binding the fused rows' [verifyRowResourceKey] enforces between specs and
 * search faces, so a spec whose titleKey is a settings resource is covered by
 * exactly the resource its screen face uses. Sanctioned omissions live in
 * [exceptions] below, each with its reason; the list stays minimal rather
 * than pre-emptively admitting a lazy row.
 *
 * Scope: the nineteen spec-backed stores. A store without a spec tier has no
 * registration here — its diff coverage is the human review the registry
 * comments document.
 */
class PreferenceSpecDiffFieldRatchetTest {

    /** Every spec-backed store: (declaring object, its full spec rows). */
    private val specSources: List<Pair<String, List<PreferenceSpec<*>>>> = listOf(
        "AppearancePreferenceSpecs" to AppearancePreferenceSpecs.all,
        "PlaybackPreferenceSpecs" to PlaybackPreferenceSpecs.all,
        "VideoPlayerPreferenceSpecs" to VideoPlayerPreferenceSpecs.all,
        "SubtitlePreferenceSpecs" to SubtitlePreferenceSpecs.all,
        "HomeDiscoveryPreferenceSpecs" to HomeDiscoveryPreferenceSpecs.all,
        "ScreensaverPreferenceSpecs" to ScreensaverPreferenceSpecs.all,
        "ExperimentalPreferenceSpecs" to ExperimentalPreferenceSpecs.all,
        "AudioEffectsPreferenceSpecs" to AudioEffectsPreferenceSpecs.all,
        "PlayerEnginePreferenceSpecs" to PlayerEnginePreferenceSpecs.all,
        "NotificationPreferenceSpecs" to NotificationPreferenceSpecs.all,
        // The reader rows carry no search face and no reset category, so every
        // row auto-skips the loop below — registered for the same totality the
        // other domains get (a future searchable+resettable reader row is
        // ratcheted by construction).
        "ReaderPreferenceSpecs" to ReaderPreferenceSpecs.all,
        // The library and downloads rows carry no search face either (their
        // catalog rows stay the feature-side residuals the appearance/storage
        // screens render), so every row auto-skips the loop below — registered
        // for the same totality (a future search-bearing row is ratcheted by
        // construction).
        "LibraryPreferenceSpecs" to LibraryPreferenceSpecs.all,
        "DownloadsPreferenceSpecs" to DownloadsPreferenceSpecs.all,
        // The audio-player, audio-cache and network/offline rows carry no
        // search face either (their catalog rows stay the feature-side
        // residuals the audio/network screens render), so every row
        // auto-skips the loop below — registered for the same totality (a
        // future search-bearing row is ratcheted by construction).
        "AudioPreferenceSpecs" to AudioPreferenceSpecs.all,
        "AudioCachePreferenceSpecs" to AudioCachePreferenceSpecs.all,
        "NetworkOfflinePreferenceSpecs" to NetworkOfflinePreferenceSpecs.all,
        // The SyncPlay/casting rows all carry search faces (the hand fused
        // rows' ss_* keys, which the SYNCPLAY_CASTING diff registry already
        // pins), so they hit the loop below; the volume-memory toggle's
        // ss_remember_volume_title face is pinned by its new PLAYBACK diff
        // row, and the two navigation toggles' faces by the HOME_DISCOVERY
        // diff rows that were always there (the customization blobs and the
        // levels blob carry no search face and auto-skip).
        "SyncPlayCastPreferenceSpecs" to SyncPlayCastPreferenceSpecs.all,
        "VolumeProfilePreferenceSpecs" to VolumeProfilePreferenceSpecs.all,
        "NavigationPreferenceSpecs" to NavigationPreferenceSpecs.all,
    )

    /**
     * Sanctioned omissions — (declaring source, spec titleKey) pairs the
     * reset review deliberately covers under a different resource or not at
     * all, each with its reason. Add an entry ONLY with the reason written
     * beside it.
     */
    private val exceptions: Set<Pair<String, String>> = setOf(
        // The per-variant accent picker's search face is the core:ui alias
        // resource (the search hit restates the enum's core_ui_* faces — no
        // settings-side ss_* key exists); the diff row reads the swatch under
        // ss_style_accent_title, the alias row the settings screen renders.
        "AppearancePreferenceSpecs" to "core_ui_accent_color_title",
        // The color-style knob IS surfaced — under settings_theme_style (the
        // one-key-family fold: the spec's core_ui_* search face restates the
        // row's screen face; the diff row reads the same colorStyle slice
        // field).
        "AppearancePreferenceSpecs" to "core_ui_color_style_title",
        // The per-library home-section overrides are surfaced by the
        // diff_library_home_overrides row (same slice field, count format) —
        // the configure-libraries search face is the screen entrance's
        // wording, not a second knob.
        "HomeDiscoveryPreferenceSpecs" to "ss_configure_libraries_title",
        // The excluded-series and hidden-CW sets are surfaced by their
        // diff_* count rows (diff_next_up_excluded_series /
        // diff_hidden_cw_items) — the ss_* faces are the management screens'
        // entrances, not separate reset keys.
        "HomeDiscoveryPreferenceSpecs" to "ss_next_up_hidden_title",
        "HomeDiscoveryPreferenceSpecs" to "ss_unhide_cw_title",
        // All three are members of the ONE enabled_experimental_features set
        // key (keyName identical on the three spec rows): the reset review
        // surfaces the set (diff_experimental_enabled_features, count) — per-
        // member rows would triple-count a single reset key.
        "ExperimentalPreferenceSpecs" to "ss_HOME_CARD_CLIPPING_title",
        "ExperimentalPreferenceSpecs" to "ss_MEDIA_CARD_PEEK_title",
        "ExperimentalPreferenceSpecs" to "ss_DIRECT_ARR_INTEGRATION_title",
    )

    @Test
    fun `every spec-backed reset row has a diff field row in its category`() {
        val problems = mutableListOf<String>()
        for ((source, specs) in specSources) {
            for (spec in specs) {
                val search = spec.search ?: continue // legacy/no-search row: no user-facing face to pin
                val category = spec.resetCategory ?: continue // reset participation owned elsewhere (the SHOW_ADVANCED_SETTINGS shape)
                val view = PreferenceCategoryViews.firstOrNull { it.category == category }
                if (view == null) {
                    problems += "  - $source/'${search.titleKey}': reset category $category has no PreferenceCategoryViews entry"
                    continue
                }
                val covered = view.diffFields.any { it.labelRes.key == search.titleKey }
                if (!covered && (source to search.titleKey) !in exceptions) {
                    problems += "  - $source/'${search.titleKey}' (${spec.keyName}, $category) has no DiffField row — " +
                        "the reset review resets a knob it never shows"
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail(
                "the diff registries are missing spec-backed rows (add the DiffField row — label + " +
                    "slice read + format mirroring its category's siblings — or exception-list it here " +
                    "with the reason):\n" + problems.joinToString("\n")
            )
        }
    }
}
