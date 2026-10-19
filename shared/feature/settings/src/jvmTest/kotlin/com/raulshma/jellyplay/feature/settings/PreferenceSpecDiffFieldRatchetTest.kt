package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitlePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
import kotlin.test.Test
import kotlin.test.fail

/**
 * The spec↔diff cross-pin ratchet for the Factory Reset review (the
 * PreferenceCategoryPresentationTest self-consistency pins' missing twin):
 * every SPEC row that participates in a reset category AND declares a search
 * face (a user-facing knob — the same search tier the settings screens
 * derive from) must have a [DiffField] row in that category's
 * [PreferenceCategoryViews] registry, matched by the label resource KEY the
 * spec's [PreferenceSearchSpec.titleKey] names. Without this pin a preference
 * added everywhere BUT the diff registry is silently absent from the
 * reset/import review — the user resets a knob they were never shown
 * (the layout-override pair this ratchet's first run surfaced: declared,
 * projected into the slice, diff-registered by no row).
 *
 * Match rule: key-level (spec titleKey == DiffField labelRes key) — the same
 * binding the fused rows' [verifyRowResourceKey] enforces between specs and
 * search faces, so a spec whose titleKey is a settings resource is covered by
 * exactly the resource its screen face uses. Sanctioned omissions live in
 * [exceptions] below, each with its reason; the list is derived from what
 * this test found, never pre-emptively grown to admit a lazy row.
 *
 * Scope: the seven spec-backed stores. The spec-less stores (the audio /
 * effects / navigation / library residuals) have no spec tier to cross-pin —
 * their diff coverage is the human review the registry comments document.
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
