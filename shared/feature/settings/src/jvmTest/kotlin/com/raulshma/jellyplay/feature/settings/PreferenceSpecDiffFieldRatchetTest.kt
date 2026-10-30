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
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The spec↔diff cross-pin guard for the Factory Reset review — the OUTSIDE-IN
 * twin of the ratchet that now lives in [diffRows] itself. The registration
 * inverted: a category's review rows pair spec rows (filtered by reset
 * category + search face — the exact filter below) with feature-side
 * read/format entries, and the pairing is enforced AT REGISTRY INIT — a
 * read pairing no spec row fails there, and a spec row without a read (or a
 * [diffFieldOmissions] entry) fails there too. Touching
 * [PreferenceCategoryViews] below exercises that init: a broken pairing is a
 * hard initialization failure, not a silent review gap.
 *
 * What still needs this runtime test:
 *  - the omissions ledger's exact shape (the documented exceptions, keyed per
 *    category — a new entry must land here loudly);
 *  - every ledger entry still pairs a searchable spec row (stale-exception
 *    detection against the live specs);
 *  - the outside-in coverage contract: every spec row the filter selects is
 *    covered by its category's registry rows by label-resource key — the same
 *    binding the fused rows' verifyRowResourceKey enforces between specs and
 *    search faces.
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
        // ss_remember_volume_title face is pinned by its PLAYBACK diff
        // read, and the two navigation toggles' faces by the HOME_DISCOVERY
        // reads that were always there (the customization blobs and the
        // levels blob carry no search face and auto-skip).
        "SyncPlayCastPreferenceSpecs" to SyncPlayCastPreferenceSpecs.all,
        "VolumeProfilePreferenceSpecs" to VolumeProfilePreferenceSpecs.all,
        "NavigationPreferenceSpecs" to NavigationPreferenceSpecs.all,
    )

    private val filteredSpecs: List<Triple<String, PreferenceResetCategory, String>> =
        specSources
            .flatMap { (source, specs) -> specs.map { source to it } }
            .mapNotNull { (source, spec) ->
                val search = spec.search ?: return@mapNotNull null
                val category = spec.resetCategory ?: return@mapNotNull null
                Triple(source, category, search.titleKey)
            }

    @Test
    fun `the omissions ledger is exactly the documented exceptions`() {
        // A new sanctioned omission lands here WITH its reason beside it in
        // diffFieldOmissions — the ledger stays minimal rather than
        // pre-emptively admitting a lazy row.
        assertEquals(
            mapOf(
                PreferenceResetCategory.APPEARANCE to setOf(
                    "core_ui_accent_color_title",
                    "core_ui_color_style_title",
                ),
                PreferenceResetCategory.HOME_DISCOVERY to setOf(
                    "ss_configure_libraries_title",
                    "ss_next_up_hidden_title",
                    "ss_unhide_cw_title",
                ),
                PreferenceResetCategory.EXPERIMENTAL to setOf(
                    "ss_HOME_CARD_CLIPPING_title",
                    "ss_MEDIA_CARD_PEEK_title",
                    "ss_DIRECT_ARR_INTEGRATION_title",
                ),
            ),
            diffFieldOmissions,
            "the omissions ledger drifted — every entry is a reviewed decision",
        )
    }

    @Test
    fun `every omission ledger entry pairs a searchable spec row`() {
        val filteredKeys = filteredSpecs.map { (source, category, key) -> category to key }.toSet()
        val stale = diffFieldOmissions
            .flatMap { (category, keys) -> keys.map { category to it } }
            .filterNot { it in filteredKeys }
        assertEquals(emptyList(), stale, "ledger entries that no longer pair a spec row")
    }

    @Test
    fun `every spec-backed reset row has a diff field row in its category`() {
        // The registry init (touched by reading PreferenceCategoryViews) has
        // already enforced the pairing — this pin restates the contract from
        // the outside: label-resource-key coverage per category, the ledger
        // being the only sanctioned gap.
        val covered = PreferenceCategoryViews.associate { view ->
            view.category to view.diffFields.map { it.labelRes.key }.toSet()
        }
        val problems = filteredSpecs.mapNotNull { (source, category, titleKey) ->
            val keys = covered[category]
            when {
                keys == null ->
                    "  - $source/'$titleKey': reset category $category has no PreferenceCategoryViews entry"
                titleKey !in keys && (category to titleKey) !in diffFieldOmissions.entries.flatMap { (c, k) -> k.map { c to it } } ->
                    "  - $source/'$titleKey' ($category) has no diff row — " +
                        "the reset review resets a knob it never shows"
                else -> null
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
