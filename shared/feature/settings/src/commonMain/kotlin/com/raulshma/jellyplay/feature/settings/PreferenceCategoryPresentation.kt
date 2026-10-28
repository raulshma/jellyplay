package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AccessPoint
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.BrandSublimeText
import com.composables.icons.tabler.outline.Cards
import com.composables.icons.tabler.outline.Cash
import com.composables.icons.tabler.outline.Cpu
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.Flask
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.ScreenShare
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.ShieldLock
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.Volume
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSliceSnapshot
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.audio.AudioPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCachePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.engine.PlayerEnginePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.library.LibraryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.navigation.NavigationPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.notification.NotificationPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflinePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.reader.ReaderPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitlePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfilePreferenceSpecs
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Plug
import com.composables.icons.tabler.outline.Playlist
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.HasDisplayName
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.core.ui.components.formatOneDecimal
import com.raulshma.jellyplay.core.ui.model.labelResource
import com.raulshma.jellyplay.core.ui.model.preferenceEnumLabelResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
// The diff rows' label sources: the settings-search titles (ss_* — plus the
// settings_* keys that carry the same row titles) plus the diff_* residue —
// extension accessors on Res.string, imported in bulk.
import com.raulshma.jellyplay.feature.settings.generated.resources.*

/**
 * Presentation model for a single preference field shown on the Factory Reset
 * / Import Preview screens: its localized label plus the formatted current and
 * baseline values. [changed] is precomputed by
 * [PreferenceCategoryView.changedFields] so the UI never needs to re-derive
 * equality.
 *
 * The label is RESOLVED TEXT — carried in the [PreferenceDiffSnapshot] the
 * ViewModels build (one `getString` per distinct row resource per snapshot
 * generation), because the rendering components take plain strings and the
 * diff lambdas run outside composition. The label SOURCES are the declared
 * [DiffField] resources: the settings-search row titles where a row has one,
 * plus `diff_*` resources added to the default locale for the residue.
 */
@Immutable
data class PreferenceField(
    val label: String,
    val currentValue: String,
    val factoryValue: String,
) {
    val changed: Boolean get() = currentValue != factoryValue
}

/**
 * The snapshot pair one diff renders: the [PreferenceSliceSnapshot] values
 * plus the label resolver for this snapshot generation. The ViewModels expose
 * this (current vs factory / current vs incoming); the registry lambdas below
 * consume it opaquely.
 */
@Immutable
class PreferenceDiffSnapshot(
    val slices: PreferenceSliceSnapshot,
    val label: (StringResource) -> String,
)

/**
 * One declared diff row of a category: the localized label source, the slice
 * read, and the value formatter. The per-category lists below are the ONE
 * declaration of "what the reset/import review surfaces per category", one
 * row per user-facing field read straight off the slices.
 *
 * @param labelRes an EXISTING localized string resource — a settings-search
 *   row title (`ss_*`) where the preference has one — so the review screens
 *   render the same wording the settings screens use.
 * @param read the field off [PreferenceSliceSnapshot]; the same slice field
 *   the corresponding screen consumes.
 * @param format the value rendering used when [enumLabel] is unset.
 * @param enumLabel the enum-label seam for enum-valued rows
 *   (`PreferenceEnumNames.labelResource`): the value maps to a localized
 *   resource instead of the [format] fallback, so the review screens show the
 *   same localized wording as the settings rows. When set, [format] is
 *   unused — declare the row with the read and the seam reference only.
 */
class DiffField<V>(
    val labelRes: StringResource,
    private val read: (PreferenceSliceSnapshot) -> V,
    private val format: (V) -> String = { it.toString() },
    private val enumLabel: ((V) -> StringResource)? = null,
) {
    /**
     * The formatted value for [slices] — the diff compares these strings.
     * Seam-covered enum rows resolve [enumLabel] through [resolve]; every
     * other row runs [format]. [resolve] defaults to the same
     * resource-`toString` fallback a label-table miss produces, so pure
     * tests can call this without a resolved table.
     */
    fun value(slices: PreferenceSliceSnapshot, resolve: (StringResource) -> String = { it.toString() }): String =
        enumLabel?.let { resolve(it(read(slices))) } ?: format(read(slices))
}

/**
 * Presentation bundle for one [PreferenceResetCategory]: icon, display name,
 * and the ordered list of user-facing diff rows. "User-facing" means the
 * fields a user would recognize from the corresponding settings screen —
 * internal bookkeeping keys (migration flags, recall slots) are intentionally
 * omitted even though the store's reset key list still covers them.
 *
 * Callers pass the current and baseline [PreferenceDiffSnapshot]s once (the
 * [FactoryResetViewModel] / [RestoreWizardViewModel] expose them) so labels
 * aren't re-resolved per field; use [changedFields] for the diff subset and
 * [totalFields] for the full count.
 */
@Immutable
class PreferenceCategoryView(
    val category: PreferenceResetCategory,
    val icon: ImageVector,
    val displayNameRes: StringResource,
    internal val diffFields: List<DiffField<*>>,
) {
    /** The label resources this category resolves — the snapshot's label-table workload. */
    val labelResources: List<StringResource> =
        (diffFields.map { it.labelRes } + preferenceEnumLabelResources).distinct()

    /** Rows whose current value differs from the baseline. */
    fun changedFields(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): List<PreferenceField> =
        render(prefs, baseline).filter { it.changed }

    /** Total rows surfaced for this category (for "X of Y changed"). */
    fun totalFields(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): Int =
        diffFields.size

    /** All rows rendered, changed or not — the full review list behind [changedFields]. */
    internal fun fields(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): List<PreferenceField> =
        render(prefs, baseline)

    private fun render(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): List<PreferenceField> =
        diffFields.map { field ->
            PreferenceField(
                label = prefs.label(field.labelRes),
                currentValue = field.value(prefs.slices, prefs.label),
                factoryValue = field.value(baseline.slices, prefs.label),
            )
        }
}

// ---------------------------------------------------------------------------
// Formatting helpers for the diff rows' value text; labels resolve through
// [PreferenceDiffSnapshot].
// ---------------------------------------------------------------------------

private fun Boolean.onOff(): String = if (this) "On" else "Off"

/**
 * Prettify an enum without a `displayName` (e.g. `HW_PREFERRED` → `Hw Preferred`).
 * Fallback only: the enums on the PreferenceEnumNames seam bypass this via
 * their row's [DiffField.enumLabel] and render localized text instead.
 */
private fun Enum<*>.pretty(): String =
    name.split('_').joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.titlecase() }
    }

private fun Any?.enumDisplay(): String = when (this) {
    null -> "System Default"
    is HasDisplayName -> displayName
    is Enum<*> -> pretty()
    else -> toString()
}

private fun Float.fmt1(): String = formatOneDecimal(this.toDouble())
private fun Float.pct(): String = "${(this * 100).toInt()}%"
private fun Long.millisToSeconds(): String = "${this / 1000.0}s"
private fun Long.millisToMinutes(): String = "${this / 60_000.0}m"
private fun Int.sections(): String = "$this sections"

private fun Map<*, SegmentBehavior>.segmentSummary(): String =
    entries.joinToString(", ") { "${it.key}: ${it.value.enumDisplay()}" }

/** Short, stable summary of a [SubtitleStyle] (skips nullable/empty fields). */
private fun SubtitleStyle.summary(): String =
    listOfNotNull(
        "Size ${fontSize}pt",
        fontColor.name.lowercase().replaceFirstChar { it.titlecase() },
        if (backgroundOpacity > 0f) "BG ${(backgroundOpacity * 100).toInt()}%" else null,
        edgeType.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
        if (bold) "Bold" else null,
        if (italic) "Italic" else null,
    ).joinToString(", ")

/** Short, stable summary of the user-named subtitle style preset list. */
private fun List<SubtitleStylePreset>.presetsSummary(): String =
    if (isEmpty()) "None" else joinToString(", ") { it.name }

private fun EqualizerSettings.summary(): String =
    "Preset bands: ${bandLevels.joinToString(",") { formatSignedInt(it) }}"

// ---------------------------------------------------------------------------
// Declared per-category diff rows. Each category's list is built by [diffRows]:
// spec-backed rows declare their READ only ([DiffRowsBuilder.spec] — the label
// resource derives from the spec row's bound search title, the one binding
// verifyRowResourceKey enforces at catalog init), while count-style rows,
// omission-covered rows and spec-less residuals stay fully hand-declared
// ([DiffRowsBuilder.hand]). Order is the display order;
// `PreferenceCategoryPresentationTest` pins the diff behavior and the row
// coverage, `PreferenceSpecDiffFieldRatchetTest` the pairing's totality.
// ---------------------------------------------------------------------------

/**
 * The diff pairing universe: every spec-backed store's rows. A spec row whose
 * [PreferenceSpec.search] face names a [PreferenceSpec.resetCategory] pairs a
 * read in exactly one category's builder — the same filter the ratchet test
 * pins from the outside.
 */
private val allDiffSpecs: List<PreferenceSpec<*>> = listOf(
    AppearancePreferenceSpecs.all,
    PlaybackPreferenceSpecs.all,
    VideoPlayerPreferenceSpecs.all,
    SubtitlePreferenceSpecs.all,
    HomeDiscoveryPreferenceSpecs.all,
    ScreensaverPreferenceSpecs.all,
    ExperimentalPreferenceSpecs.all,
    AudioEffectsPreferenceSpecs.all,
    PlayerEnginePreferenceSpecs.all,
    NotificationPreferenceSpecs.all,
    ReaderPreferenceSpecs.all,
    LibraryPreferenceSpecs.all,
    DownloadsPreferenceSpecs.all,
    AudioPreferenceSpecs.all,
    AudioCachePreferenceSpecs.all,
    NetworkOfflinePreferenceSpecs.all,
    SyncPlayCastPreferenceSpecs.all,
    VolumeProfilePreferenceSpecs.all,
    NavigationPreferenceSpecs.all,
).flatten()

/**
 * The diff rows' label resources, projected off the fused rows' bound
 * search/screen titles (searchTitleRes folding to titleRes — the binding
 * `verifyRowResourceKey` enforces at catalog init): titleKey → resource. A
 * projection, not a second declaration home — the resources are the rows'.
 */
private val diffLabelResourceByKey: Map<String, StringResource> =
    SettingsScreenGroups.all
        .flatMap { it.items }
        .associate { it.titleRes.key to it.titleRes }

/**
 * One spec-paired diff row's feature-side read/format pair — the slice read
 * and value rendering the spec rows cannot carry (the presentation stays
 * presentation). Paired with a spec row by its search titleKey; the label
 * resource derives at build time.
 */
private class SpecDiffRead<V>(
    val titleKey: String,
    private val read: (PreferenceSliceSnapshot) -> V,
    private val format: (V) -> String = { it.toString() },
    private val enumLabel: ((V) -> StringResource)? = null,
) {
    /** The resolved diff row: the spec's bound label + this read. */
    fun resolve(labelRes: StringResource): DiffField<*> = DiffField(labelRes, read, format, enumLabel)
}

/**
 * The per-category diff-row builder: mixes spec-paired reads ([spec]) with
 * fully-declared rows ([hand] — the count-style, omission-covered and
 * spec-less residual rows) in display order.
 */
private class DiffRowsBuilder {
    val rows = mutableListOf<DiffField<*>>()
    val claimed = mutableSetOf<String>()

    /**
     * A spec-paired row: [titleKey] names the spec row (its search face and
     * reset category must land in this builder's category — the init-fail
     * checks in [diffRows] enforce the pairing both ways); the label derives
     * from the row's bound resource, [read]/[format]/[enumLabel] are the
     * feature-side read/format pair.
     */
    fun <V> spec(
        titleKey: String,
        read: (PreferenceSliceSnapshot) -> V,
        format: (V) -> String = { it.toString() },
        enumLabel: ((V) -> StringResource)? = null,
    ) {
        val labelRes = requireNotNull(diffLabelResourceByKey[titleKey]) {
            "diff row '$titleKey' pairs no settings row — no bound label resource"
        }
        rows += SpecDiffRead(titleKey, read, format, enumLabel).resolve(labelRes)
        claimed += titleKey
    }

    /** A fully-declared row — the label resource is hand-named. */
    fun hand(field: DiffField<*>) {
        rows += field
    }
}

/**
 * The sanctioned omissions ledger — spec rows the reset review deliberately
 * covers under a count-style hand row or not at all, keyed per category,
 * each entry with its reason. Every [diffRows] builder subtracts its
 * category's entries from the coverage requirement; an entry pairing no
 * searchable spec row fails init (a stale exception), and an unledgered
 * uncovered spec row fails init too — the inverted ratchet.
 * `PreferenceSpecDiffFieldRatchetTest` pins this ledger's exact shape.
 */
internal val diffFieldOmissions: Map<PreferenceResetCategory, Set<String>> = mapOf(
    PreferenceResetCategory.APPEARANCE to setOf(
        // The per-variant accent picker's search face is the core:ui alias
        // resource (the search hit restates the enum's core_ui_* faces — no
        // settings-side ss_* key exists); the diff row reads the swatch under
        // ss_style_accent_title, the alias row the settings screen renders.
        "core_ui_accent_color_title",
        // The color-style knob IS surfaced — under settings_theme_style (the
        // one-key-family fold: the spec's core_ui_* search face restates the
        // row's screen face; the diff row reads the same colorStyle slice
        // field).
        "core_ui_color_style_title",
    ),
    PreferenceResetCategory.HOME_DISCOVERY to setOf(
        // The per-library home-section overrides are surfaced by the
        // diff_library_home_overrides row (same slice field, count format) —
        // the configure-libraries search face is the screen entrance's
        // wording, not a second knob.
        "ss_configure_libraries_title",
        // The excluded-series and hidden-CW sets are surfaced by their
        // diff_* count rows (diff_next_up_excluded_series /
        // diff_hidden_cw_items) — the ss_* faces are the management screens'
        // entrances, not separate reset keys.
        "ss_next_up_hidden_title",
        "ss_unhide_cw_title",
    ),
    PreferenceResetCategory.EXPERIMENTAL to setOf(
        // All three are members of the ONE enabled_experimental_features set
        // key (keyName identical on the three spec rows): the reset review
        // surfaces the set (diff_experimental_enabled_features, count) —
        // per-member rows would triple-count a single reset key.
        "ss_HOME_CARD_CLIPPING_title",
        "ss_MEDIA_CARD_PEEK_title",
        "ss_DIRECT_ARR_INTEGRATION_title",
    ),
)

/**
 * Builds one category's diff rows and enforces the pairing ratchet AT INIT —
 * the inverted PreferenceSpecDiffFieldRatchetTest: a read pairing no
 * (searchable, category-matching) spec row fails here, and a spec row the
 * filter selects without a read — unless [diffFieldOmissions] ledger it —
 * fails here too. Adding a searchable resettable setting without its read is
 * an init failure, not a silent review gap.
 */
private fun diffRows(
    category: PreferenceResetCategory,
    build: DiffRowsBuilder.() -> Unit,
): List<DiffField<*>> {
    val builder = DiffRowsBuilder().apply(build)
    val omissions = diffFieldOmissions[category].orEmpty()
    val filtered = allDiffSpecs
        .filter { it.resetCategory == category }
        .mapNotNull { it.search?.titleKey }
        .toSet()
    val stale = builder.claimed - filtered
    require(stale.isEmpty()) {
        "${category} diff reads pairing no searchable spec row: $stale — " +
            "the spec lost its search face or moved category; drop the read or re-key it"
    }
    val uncovered = filtered - builder.claimed - omissions
    require(uncovered.isEmpty()) {
        "${category} spec rows with a search face and no diff read: $uncovered — " +
            "add the spec(...) read/format row, or ledger the omission in diffFieldOmissions with the reason"
    }
    val staleOmissions = omissions - filtered
    require(staleOmissions.isEmpty()) {
        "${category} omission ledger entries pairing no searchable spec row: $staleOmissions"
    }
    return builder.rows
}

private val appearanceDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.APPEARANCE,
) {
    spec("ss_theme_mode_title", { it.appearance.themeMode }, { v -> v.enumDisplay() })
    spec("ss_dynamic_theming_title", { it.appearance.dynamicTheming }, Boolean::onOff)
    spec("ss_oled_mode_title", { it.appearance.oledMode }, Boolean::onOff)
    spec("ss_contrast_title", { it.appearance.contrastLevel }, { v -> v.enumDisplay() })
    hand(DiffField(Res.string.ss_style_accent_title, { it.appearance.accentColorSwatch }, ::identity))
    spec("settings_theme_style", { it.appearance.colorStyle }, { v -> v.enumDisplay() })
    spec("ss_performance_mode_title", { it.appearance.performanceMode }, Boolean::onOff)
    spec("ss_reduce_motion_title", { it.appearance.reduceMotionEnabled }, Boolean::onOff)
    hand(DiffField(Res.string.diff_synthwave_mode, { it.appearance.themeVariant == "synthwave" }, Boolean::onOff))
    hand(DiffField(Res.string.diff_soothing_mode, { it.appearance.themeVariant == "soothing" }, Boolean::onOff))
    hand(DiffField(Res.string.diff_monochrome_mode, { it.appearance.themeVariant == "monochrome" }, Boolean::onOff))
    spec("ss_theme_music_title", { it.appearance.backdropThemeMusicEnabled }, Boolean::onOff)
    spec("ss_blue_light_filter_title", { it.appearance.blueLightFilterEnabled }, Boolean::onOff)
    spec("ss_blue_light_strength_title", { it.appearance.blueLightFilterStrength }, Float::pct)
    // The details-page awards toggle — the APPEARANCE-category spec row
    // beside the screen's other display knobs.
    spec("ss_awards_badges_title", { it.appearance.detailsAwardsBadgesEnabled }, Boolean::onOff)
    spec("ss_date_format_title", { it.appearance.dateFormatPreference }, { v -> v.enumDisplay() })
    spec("ss_font_scale_title", { it.appearance.appFontScale }, { v -> v.enumDisplay() })
    spec("ss_scheduled_start_title", { it.appearance.scheduledThemeStartHour }, ::hour)
    spec("ss_scheduled_end_title", { it.appearance.scheduledThemeEndHour }, ::hour)
    spec("ss_color_blind_mode_title", { it.appearance.colorBlindMode }, { v -> v.enumDisplay() })
    spec("ss_hand_mode_title", { it.appearance.handMode }, { v -> v.enumDisplay() })
    // The layout-override pair: the settings rows and slice fields exist;
    // these are their diff rows.
    spec("settings_layout_mode", { it.appearance.layoutMode }, { v -> v.enumDisplay() })
    spec("settings_screen_fit", { it.appearance.tvOverscan }, { v -> v.enumDisplay() })
}

private val playbackDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.PLAYBACK,
) {
    spec("ss_player_engine_title", { it.playback.preferredPlayer }, { v -> v.enumDisplay() })
    spec("ss_streaming_quality_title", { it.playback.streamingQuality }, { v -> v.enumDisplay() })
    hand(DiffField(Res.string.ss_cellular_streaming_quality_title, { it.playback.cellularStreamingQuality }, { v -> v.enumDisplay() }))
    spec("ss_playback_mode_title", { it.playback.playbackMode }, { v -> v.enumDisplay() })
    spec("ss_decoder_title", { it.playback.decoderMode }, { v -> v.enumDisplay() })
    spec("ss_audio_passthrough_title", { it.playback.audioPassthrough }, Boolean::onOff)
    // The spatial toggle folds its search title onto the settings row face
    // (no ss_*_title twin) — same fold the max-audio-channels row uses.
    spec("settings_spatial_audio", { it.playback.spatialAudioEnabled }, Boolean::onOff)
    spec("ss_frame_rate_matching_title", { it.playback.frameRateMatching }, Boolean::onOff)
    spec("ss_orientation_title", { it.videoPlayer.videoDefaultOrientation }, enumLabel = OrientationMode::labelResource)
    spec("ss_default_aspect_title", { it.videoPlayer.videoDefaultAspectRatio }, ::identity)
    spec("ss_preload_buffer_title", { it.videoPlayer.videoPreloadBufferSize }, { v -> v.enumDisplay() })
    spec("ss_gestures_title", { it.videoPlayer.videoGestureMode }, enumLabel = GestureMode::labelResource)
    spec("ss_pass_out_protection_title", { it.videoPlayer.videoPassOutProtectionHours }, Int::toString)
    spec("ss_skip_back_on_resume_title", { it.videoPlayer.videoSkipBackOnResumeMs }, Long::millisToSeconds)
    hand(DiffField(Res.string.diff_hold_to_speed, { it.videoPlayer.videoHoldSpeedEnabled }, Boolean::onOff))
    spec("ss_hold_speed_multiplier_title", { it.videoPlayer.videoHoldSpeedMultiplier }, ::speedMultiplier)
    spec("ss_default_speed_title", { it.videoPlayer.videoDefaultSpeed }, ::speedMultiplier)
    spec("ss_default_brightness_level_title", { it.videoPlayer.videoBrightnessLevel }, Float::pct)
    spec("ss_video_autoplay_next_title", { it.videoPlayer.videoAutoplayNext }, Boolean::onOff)
    spec("ss_autoplay_trailers_title", { it.videoPlayer.trailerAutoplay }, Boolean::onOff)
    spec("ss_cinema_mode_title", { it.videoPlayer.cinemaModeEnabled }, Boolean::onOff)
    spec("ss_play_trailer_before_movie_title", { it.videoPlayer.playTrailerBeforeMovie }, Boolean::onOff)
    spec("ss_remember_brightness_title", { it.videoPlayer.videoRememberBrightness }, Boolean::onOff)
    hand(DiffField(Res.string.diff_auto_skip_intro, { it.videoPlayer.videoAutoSkipIntro }, Boolean::onOff))
    hand(DiffField(Res.string.diff_auto_skip_outro, { it.videoPlayer.videoAutoSkipOutro }, Boolean::onOff))
    hand(DiffField(Res.string.diff_remember_muted, { it.videoPlayer.videoRememberMuted }, Boolean::onOff))
    hand(DiffField(Res.string.diff_default_muted, { it.videoPlayer.videoMuted }, Boolean::onOff))
    spec("ss_gesture_indicator_side_title", { it.videoPlayer.videoGestureIndicatorSide }, enumLabel = GestureIndicatorSide::labelResource)
    spec("ss_seek_duration_title", { it.videoPlayer.videoSeekDurationMs }, Long::millisToSeconds)
    spec("ss_controls_timeout_title", { it.videoPlayer.videoControlsTimeoutMs }, Long::millisToSeconds)
    spec("settings_skip_prompt_duration", { it.videoPlayer.skipPromptDurationMs }, Long::millisToSeconds)
    spec("ss_swipe_seek_range_title", { it.videoPlayer.videoSwipeSeekMaxMs }, Long::millisToSeconds)
    hand(DiffField(Res.string.ss_audio_delay_title, { it.audio.audioDelayMs }, ::milliseconds))
    spec("ss_trickplay_preview_title", { it.videoPlayer.trickplayEnabled }, Boolean::onOff)
    spec("ss_trickplay_on_gestures_title", { it.videoPlayer.trickplayOnSeekGesture }, Boolean::onOff)
    spec("ss_episode_browser_title", { it.videoPlayer.videoEpisodeBrowserEnabled }, Boolean::onOff)
    spec("ss_playback_metadata_title", { it.videoPlayer.videoShowPlaybackMetadata }, Boolean::onOff)
    spec("ss_background_audio_title", { it.playback.backgroundVideoAudioEnabled }, Boolean::onOff)
    spec("settings_auto_pip", { it.playback.autoEnterPip }, Boolean::onOff)
    spec("ss_autoplay_countdown_title", { it.playback.autoPlayCountdownSec }, Int::toString)
    spec("ss_keep_screen_on_title", { it.playback.keepScreenOnDuringVideo }, Boolean::onOff)
    spec("ss_incognito_mode_title", { it.videoPlayer.incognitoModeEnabled }, Boolean::onOff)
    spec("ss_show_clock_player_title", { it.videoPlayer.showClockInPlayer }, Boolean::onOff)
    spec("ss_show_time_remaining_title", { it.videoPlayer.showTimeRemaining }, Boolean::onOff)
    spec("ss_pause_on_focus_loss_title", { it.playback.pauseOnAudioFocusLoss }, Boolean::onOff)
    spec("ss_duck_on_transient_focus_loss_title", { it.playback.duckOnTransientFocusLoss }, Boolean::onOff)
    spec("ss_tv_zoom_mode_title", { it.videoPlayer.tvZoomModePercent }, Float::pct)
    hand(DiffField(Res.string.diff_segment_behaviors, { it.videoPlayer.segmentBehaviors }, Map<*, SegmentBehavior>::segmentSummary))
    // PLAYBACK-category rows that are declared and projected into the slices,
    // registered here as diff rows.
    spec("settings_external_player_app", { it.playback.preferredExternalPlayer }, { v -> v.enumDisplay() })
    spec("settings_offline_playback", { it.playback.offlinePlaybackPreference }, { v -> v.enumDisplay() })
    spec("settings_max_audio_channels", { it.playback.maxAudioChannels }, { v -> v.enumDisplay() })
    spec("settings_downmix_boost", { it.playback.downmixBoostDb }, Float::fmt1)
    spec("settings_hide_osd_on_pause", { it.videoPlayer.videoHideOsdOnPause }, Boolean::onOff)
    spec("settings_resume_headset_plug", { it.videoPlayer.videoResumeOnHeadsetPlug }, Boolean::onOff)
    spec("settings_double_tap_hold_seek", { it.videoPlayer.videoDoubleTapHoldSeekEnabled }, Boolean::onOff)
    // The whole input-mapping blob (the screen's editor face) — rendered
    // verbatim like the aspect-ratio row above.
    spec("settings_input_bindings", { it.videoPlayer.videoInputBindings }, ::identity)
    spec("settings_still_watching_mode", { it.videoPlayer.stillWatchingMode }, { v -> v.enumDisplay() })
    spec("settings_still_watching_episodes", { it.videoPlayer.stillWatchingEpisodeThreshold }, Int::toString)
    spec("settings_video_cache_size", { it.videoPlayer.videoCacheSizeMb }, Int::toString)
    spec("settings_skip_segments_on_seek", { it.videoPlayer.skipSegmentsOnSeek }, Boolean::onOff)
    // The new playback knobs (the spec-backed rows whose specs land with
    // them — the diff registry rides the same ss_* title keys).
    spec("ss_session_restore_title", { it.playback.sessionRestoreMode }, enumLabel = ::sessionRestoreModeLabelRes)
    spec("ss_autoplay_episode_limit_title", { it.videoPlayer.autoplayEpisodeLimit }, Int::toString)
    spec("ss_next_episode_details_title", { it.videoPlayer.showNextEpisodeDetailsAfterFinish }, Boolean::onOff)
    spec("ss_offer_delete_movies_title", { it.playback.offerDeleteAfterPlaybackMovies }, Boolean::onOff)
    spec("ss_offer_delete_episodes_title", { it.playback.offerDeleteAfterPlaybackEpisodes }, Boolean::onOff)
    spec("ss_ambient_blur_title", { it.videoPlayer.ambientBlurEnabled }, Boolean::onOff)
    spec("ss_pause_details_title", { it.videoPlayer.pauseDetailsEnabled }, Boolean::onOff)
    spec("ss_trickplay_mode_title", { it.videoPlayer.trickplayMode }, enumLabel = ::trickplayModeLabelRes)
    spec("ss_pause_screensaver_title", { it.videoPlayer.pauseScreensaverMode }, enumLabel = ::pauseScreensaverModeLabelRes)
    spec("ss_pause_screensaver_delay_title", { it.videoPlayer.pauseScreensaverAfterMinutes }, Int::toString)
    spec("ss_hdr_format_title", { it.playback.hdrFormatPreference }, enumLabel = ::hdrFormatPreferenceLabelRes)
    spec("ss_force_transcode_above_height_title", { it.playback.forceTranscodeAboveHeight }, Int::toString)
    spec("ss_preferred_transcode_video_codec_title", { it.playback.preferredTranscodeVideoCodec }, enumLabel = ::transcodeVideoCodecLabelRes)
    spec("ss_preferred_transcode_audio_codec_title", { it.playback.preferredTranscodeAudioCodec }, enumLabel = ::transcodeAudioCodecLabelRes)
    spec("ss_custom_device_profile_title", { it.playback.customDeviceProfileJson }, { v -> v.ifBlank { "Off" } })
    spec("ss_mpv_tone_mapping_curve_title", { it.videoPlayer.mpvToneMappingCurve }, enumLabel = ::mpvToneMappingCurveLabelRes)
    spec("ss_mpv_deband_title", { it.videoPlayer.mpvDebandMode }, enumLabel = ::mpvDebandModeLabelRes)
    spec("ss_mpv_motion_interpolation_title", { it.videoPlayer.mpvMotionInterpolation }, Boolean::onOff)
    spec("ss_mpv_input_conf_title", { it.videoPlayer.mpvInputConf }, { v -> v.ifBlank { "Off" } })
    spec("ss_mpv_custom_buttons_title", { it.videoPlayer.mpvCustomButtons }, { v -> v.ifBlank { "Off" } })
    spec("ss_next_up_preroll_title", { it.videoPlayer.nextUpButtonPrerollSec }, Int::toString)
    spec("ss_bitmap_subtitle_scale_title", { it.videoPlayer.bitmapSubtitleScaleMode }, enumLabel = ::bitmapSubtitleScaleModeLabelRes)
    // The volume-memory toggle (the spec-backed VolumeProfilePreferenceSpecs
    // row's search face): the PLAYBACK-category diff row the spec ratchet
    // pins — the remembered levels blob beside it has no search face (its
    // user face IS this toggle).
    spec("ss_remember_volume_title", { it.volumeProfile.rememberVolumePerContentType }, Boolean::onOff)
}

private val audioDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.AUDIO,
) {
    hand(DiffField(Res.string.ss_audio_default_speed_title, { it.audio.audioDefaultSpeed }, ::speedMultiplier))
    hand(DiffField(Res.string.ss_audio_visualizer_title, { it.audio.audioVisualizerEnabled }, Boolean::onOff))
    hand(DiffField(Res.string.ss_gapless_playback_title, { it.audio.audioGaplessEnabled }, Boolean::onOff))
    hand(DiffField(Res.string.ss_crossfade_title, { it.audio.audioCrossfadeDurationMs }, Long::millisToSeconds))
    hand(DiffField(Res.string.ss_volume_normalization_title, { it.audio.audioNormalizationEnabled }, Boolean::onOff))
    hand(DiffField(Res.string.diff_audio_normalization_mode, { it.audio.audioNormalizationMode }, { v -> v.enumDisplay() }))
    hand(DiffField(Res.string.ss_replaygain_preamp_title, { it.audio.replayGainPreAmpDb }, Float::fmt1))
    hand(DiffField(Res.string.ss_channel_mixing_title, { it.audio.channelMixEnabled }, Boolean::onOff))
    hand(DiffField(Res.string.ss_channel_mix_mode_title, { it.audio.channelMixMode }, { v -> v.enumDisplay() }))
    spec("ss_equalizer_title", { it.audioEffects.equalizerEnabled }, Boolean::onOff)
    hand(DiffField(Res.string.diff_equalizer_settings, { it.audioEffects.equalizerSettings }, EqualizerSettings::summary))
    spec("ss_equalizer_preset_title", { it.audioEffects.equalizerPreset }, { v -> v.enumDisplay() })
    spec("ss_bass_boost_title", { it.audioEffects.bassBoostEnabled }, Boolean::onOff)
    spec("ss_bass_boost_strength_title", { it.audioEffects.bassBoostStrength }, { v -> v.enumDisplay() })
    spec("ss_virtualizer_title", { it.audioEffects.virtualizerEnabled }, Boolean::onOff)
    spec("ss_virtualizer_strength_title", { it.audioEffects.virtualizerStrength }, Int::toString)
    spec("ss_reverb_title", { it.audioEffects.reverbPreset }, { v -> v.enumDisplay() })
    spec("ss_volume_boost_title", { it.audioEffects.volumeBoostEnabled }, Boolean::onOff)
    spec("ss_volume_boost_gain_title", { it.audioEffects.volumeBoostGain }, ::decibels)
    spec("ss_lr_balance_title", { it.audioEffects.lrBalance }, Float::fmt1)
    spec("ss_auto_eq_by_genre_title", { it.audioEffects.autoEqByGenre }, Boolean::onOff)
    spec("ss_pitch_shift_title", { it.audioEffects.pitchSemitones }, Float::fmt1)
    hand(DiffField(Res.string.ss_audio_autoplay_next_title, { it.audio.audioAutoplayNext }, Boolean::onOff))
    hand(DiffField(Res.string.ss_audio_preload_buffer_title, { it.audio.audioPreloadBufferSize }, { v -> v.enumDisplay() }))
    hand(DiffField(Res.string.ss_night_mode_volume_title, { it.audio.audioNightModeVolume }, Float::pct))
    hand(DiffField(Res.string.ss_night_mode_gain_title, { it.audio.audioNightModeGain }, Int::toString))
    hand(DiffField(Res.string.ss_audio_skip_prev_threshold_title, { it.audio.audioSkipPreviousThresholdMs }, Long::millisToSeconds))
    spec("ss_night_mode_title", { it.audioEffects.nightModeEnabled }, Boolean::onOff)
    spec("ss_night_mode_strength_title", { it.audioEffects.nightModeStrength }, { v -> v.enumDisplay() })
    spec("ss_dialogue_boost_title", { it.audioEffects.dialogueBoostEnabled }, Boolean::onOff)
    spec("settings_dialogue_boost_strength", { it.audioEffects.dialogueBoostStrength }, { v -> v.enumDisplay() })
    hand(DiffField(Res.string.ss_sleep_timer_title, { it.audio.sleepTimerDurationMs }, Long::millisToMinutes))
    hand(DiffField(Res.string.diff_sleep_timer_end_of_episode, { it.audio.sleepTimerEndOfEpisode }, Boolean::onOff))
    hand(DiffField(Res.string.diff_lyrics_visible, { it.audio.audioLyricsVisible }, Boolean::onOff))
}

private val subtitlesLanguageDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.SUBTITLES_LANGUAGE,
) {
    spec("ss_subtitle_language_title", { it.subtitle.preferredSubtitleLanguage }, ::systemFallback)
    spec("ss_audio_language_title", { it.subtitle.preferredAudioLanguage }, ::systemFallback)
    spec("ss_subtitle_forced_only_title", { it.subtitle.subtitlesForcedOnly }, Boolean::onOff)
    spec("ss_show_only_preferred_subtitles_title", { it.subtitle.showOnlyPreferredSubtitles }, Boolean::onOff)
    hand(DiffField(Res.string.diff_subtitle_preview, { it.subtitle.subtitlePreviewInSettings }, Boolean::onOff))
    hand(DiffField(Res.string.diff_subtitle_style, { it.subtitle.subtitleStyle }, SubtitleStyle::summary))
    hand(DiffField(Res.string.diff_subtitle_style_presets, { it.subtitle.userStylePresets }, List<SubtitleStylePreset>::presetsSummary))
    spec("ss_high_contrast_subtitles_title", { it.subtitle.highContrastSubtitles }, Boolean::onOff)
    hand(DiffField(Res.string.ss_pgs_direct_play_title, { it.playback.pgsSubtitleDirectPlay }, Boolean::onOff))
    spec("ss_hdr_subtitle_style_title", { it.subtitle.hdrSubtitleStyleEnabled }, Boolean::onOff)
    hand(DiffField(Res.string.diff_hdr_subtitle_style, { it.subtitle.hdrSubtitleStyle }, SubtitleStyle::summary))
}

// The downloads/network category: no spec row carries a search face for it
// (the downloads/networkOffline stores have no spec tier) — fully
// hand-declared, no pairing possible.
private val downloadsNetworkDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_wifi_only_downloads_title, { it.downloads.wifiOnlyDownloads }, Boolean::onOff),
    DiffField(Res.string.ss_download_connections_title, { it.downloads.downloadConnections }, Int::toString),
    DiffField(Res.string.ss_max_concurrent_downloads_title, { it.downloads.maxConcurrentDownloads }, Int::toString),
    DiffField(Res.string.ss_download_quality_title, { it.downloads.downloadQuality }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_smart_downloads_title, { it.downloads.smartDownloadsEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_auto_download_mode_title, { it.downloads.autoDownloadMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_max_download_storage_limit_title, { it.downloads.maxDownloadStorageGb }, Int::toString),
    DiffField(Res.string.ss_download_storage_location_title, { it.downloads.downloadStorageLocation }, ::identity),
    DiffField(Res.string.ss_max_cache_size_title, { it.networkOffline.maxCacheSizeMb }, Int::toString),
    DiffField(Res.string.ss_auto_delete_cache_title, { it.networkOffline.autoDeleteCache }, Boolean::onOff),
    DiffField(Res.string.ss_offline_mode_title, { it.networkOffline.manualOfflineEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_auto_offline_title, { it.networkOffline.autoOfflineEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_bandwidth_cap_title, { it.networkOffline.manualBandwidthCap }, Long::toString),
    DiffField(Res.string.ss_metered_network_behavior_title, { it.networkOffline.meteredNetworkBehavior }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_adaptive_bitrate_title, { it.networkOffline.adaptiveBitrateEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_data_saver_title, { it.networkOffline.dataSaverEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_verbose_logging_title, { it.networkOffline.verboseNetworkLogging }, Boolean::onOff),
    DiffField(Res.string.ss_network_timeout_title, { it.networkOffline.networkTimeoutPreset }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_cellular_download_warning_title, { it.downloads.cellularDownloadSizeWarningMb }, Int::toString),
    DiffField(Res.string.ss_download_schedule_title, { it.downloads.downloadScheduleEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_download_schedule_start_title, { it.downloads.downloadScheduleWindow.startHour }, Int::toString),
    DiffField(Res.string.ss_download_schedule_end_title, { it.downloads.downloadScheduleWindow.endHour }, Int::toString),
    DiffField(Res.string.ss_download_schedule_wifi_only_title, { it.downloads.downloadScheduleWindow.wifiOnly }, Boolean::onOff),
)

private val homeDiscoveryDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.HOME_DISCOVERY,
) {
    spec("ss_home_mode_title", { it.homeDiscovery.homeMode }, { v -> v.enumDisplay() })
    spec("ss_hero_section_title", { it.homeDiscovery.homeHeroEnabled }, Boolean::onOff)
    spec("ss_home_hero_style_title", { it.homeDiscovery.homeHeroStyle }, enumLabel = ::homeHeroStyleLabelRes)
    spec("ss_home_hero_slideshow_interval_title", { it.homeDiscovery.homeHeroSlideshowIntervalSec }, Int::toString)
    spec("ss_hide_top_header_title", { it.homeDiscovery.hideTopHeaderOnScroll }, Boolean::onOff)
    hand(DiffField(Res.string.diff_enabled_home_sections, { it.homeDiscovery.enabledHomeSectionTypes }, { it.size.sections() }))
    hand(DiffField(Res.string.diff_home_section_order, { it.homeDiscovery.homeSectionOrder }, { it.size.sections() }))
    hand(DiffField(Res.string.diff_library_home_overrides, { it.homeDiscovery.libraryHomeSectionOverrides.size }, Int::toString))
    hand(DiffField(Res.string.ss_library_view_mode_title, { it.library.libraryViewMode }, { v -> v.enumDisplay() }))
    spec("ss_nav_labels_title", { it.navigation.navBarShowLabels }, Boolean::onOff)
    spec("settings_nav_hide_on_scroll", { it.navigation.hideBottomNavOnScroll }, Boolean::onOff)
    hand(DiffField(Res.string.diff_nav_item_order, { it.navigation.navItemOrder.size }, Int::toString))
    hand(DiffField(Res.string.diff_hidden_nav_items, { it.navigation.hiddenNavItems.size }, Int::toString))
    spec("ss_show_unwatched_badge_title", { it.homeDiscovery.showUnwatchedBadge }, Boolean::onOff)
    spec("ss_hide_watched_items_title", { it.homeDiscovery.hideWatchedItems }, Boolean::onOff)
    spec("ss_show_watched_checkmark_title", { it.homeDiscovery.showWatchedCheckmark }, Boolean::onOff)
    spec("ss_show_external_ratings_title", { it.homeDiscovery.showExternalRatings }, Boolean::onOff)
    spec("ss_merge_continue_next_up_title", { it.homeDiscovery.mergeContinueWatchingAndNextUp }, Boolean::onOff)
    spec("ss_next_up_max_days_title", { it.homeDiscovery.nextUpMaxDays }, Int::toString)
    spec("ss_next_up_rewatching_title", { it.homeDiscovery.nextUpRewatching }, Boolean::onOff)
    hand(DiffField(Res.string.diff_next_up_excluded_series, { it.homeDiscovery.nextUpExcludedSeriesIds.size }, Int::toString))
    hand(DiffField(Res.string.diff_hidden_cw_items, { it.homeDiscovery.hiddenCwItemIds.size }, Int::toString))
    spec("ss_pinned_home_sections_title", { it.homeDiscovery.pinnedHomeSections.size }, Int::toString)
    spec("ss_home_layout_presets_title", { it.homeDiscovery.homeLayoutPresets.size }, Int::toString)
    spec("ss_continue_watching_click_title", { it.homeDiscovery.continueWatchingClickBehavior }, { v -> v.enumDisplay() })
    hand(DiffField(Res.string.diff_library_sort_overrides, { it.library.defaultLibrarySortOrders.size }, Int::toString))
    hand(DiffField(Res.string.diff_library_view_overrides, { it.library.libraryViewModes.size }, Int::toString))
    hand(DiffField(Res.string.diff_library_filter_overrides, { it.library.libraryFilters.size }, Int::toString))
    hand(DiffField(Res.string.ss_hide_episode_thumbnails_title, { it.library.hideEpisodeThumbnails }, Boolean::onOff))
    hand(DiffField(Res.string.diff_episodes_descending, { it.library.episodesDescending }, Boolean::onOff))
    hand(DiffField(Res.string.ss_skip_specials_title, { it.library.skipSpecials }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_missing_episodes, { it.library.showMissingEpisodes }, Boolean::onOff))
    hand(DiffField(Res.string.settings_prefer_logos, { it.library.preferLogos }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_tagline, { it.library.showTagline }, Boolean::onOff))
    hand(DiffField(Res.string.settings_play_theme_music, { it.library.playThemeMusic }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_episode_stream_badges, { it.library.showEpisodeStreamBadges }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_external_links, { it.library.showExternalLinks }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_reviews, { it.library.showReviews }, Boolean::onOff))
    hand(DiffField(Res.string.settings_prefer_local_trailers, { it.library.preferLocalTrailers }, Boolean::onOff))
    hand(DiffField(Res.string.settings_trailer_preview_on_focus, { it.library.trailerPreviewOnFocus }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_promoted_actions_landscape, { it.library.showPromotedActionsLandscape }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_download_progress, { it.library.showDownloadProgress }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_upcoming_strip, { it.library.showUpcomingStrip }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_more_in_collection, { it.library.showMoreInCollection }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_filmography_roles, { it.library.showFilmographyRoles }, Boolean::onOff))
    hand(DiffField(Res.string.settings_show_frequent_costars, { it.library.showFrequentCostars }, Boolean::onOff))
    spec("ss_clock_home_title", { it.homeDiscovery.showClockOnHome }, Boolean::onOff)
    spec("ss_settings_in_home_search_title", { it.homeDiscovery.showSettingsInHomeSearch }, Boolean::onOff)
    // One row per user-facing home-screen toggle; internal stamps and
    // not-yet-surfaced slices stay off the registry (internal bookkeeping
    // keys are intentionally omitted).
    spec("settings_exclude_active_sessions", { it.homeDiscovery.excludeActiveSessions }, Boolean::onOff)
    spec("settings_home_hero_video", { it.homeDiscovery.homeHeroVideoEnabled }, Boolean::onOff)
    spec("settings_home_hero_video_tv", { it.homeDiscovery.homeHeroVideoTvEnabled }, Boolean::onOff)
    spec("settings_home_focus_backdrop", { it.homeDiscovery.homeFocusBackdrop }, Boolean::onOff)
    spec("settings_home_stats_card", { it.homeDiscovery.homeStatsCard }, Boolean::onOff)
    spec("settings_home_genre_rail", { it.homeDiscovery.homeGenreRail }, Boolean::onOff)
    spec("settings_home_presence_chips", { it.homeDiscovery.homePresenceChips }, Boolean::onOff)
    spec("settings_new_section_announcements", { it.homeDiscovery.homeNewSectionAnnouncements }, Boolean::onOff)
    spec("settings_home_layout_autopush", { it.homeDiscovery.homeLayoutAutopush }, Boolean::onOff)
    spec("settings_show_quality_badges", { it.homeDiscovery.showQualityBadges }, Boolean::onOff)
    spec("settings_show_new_badges", { it.homeDiscovery.showNewBadges }, Boolean::onOff)
    spec("settings_home_trickplay_preview", { it.homeDiscovery.homeTrickplayPreview }, Boolean::onOff)
    spec("settings_anti_spoiler_home", { it.homeDiscovery.antiSpoilerHome }, Boolean::onOff)
    // HOME_DISCOVERY rows not covered by the rows above.
    spec("ss_home_backdrop_title", { it.homeDiscovery.homeBackdropEnabled }, Boolean::onOff)
    spec("ss_discover_rows_title", { it.homeDiscovery.discoverRows.size }, Int::toString)
    spec("ss_classic_rows_title", { it.homeDiscovery.classicRows }, Boolean::onOff)
    spec("ss_hidden_libraries_title", { it.homeDiscovery.myMediaHiddenLibraryIds.size }, Int::toString)
}

private val audioCacheDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_audio_caching_enabled_title, { it.audioCache.audioCachingEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_audio_cache_size_title, { it.audioCache.audioCacheSizeMb }, Int::toString),
    DiffField(Res.string.ss_audio_prefetch_lookahead_title, { it.audioCache.audioPrefetchLookahead }, Int::toString),
    DiffField(Res.string.settings_audio_prefetch_backfill, { it.audioCache.audioPrefetchBackfill }, Int::toString),
    DiffField(Res.string.ss_audio_cache_network_policy_title, { it.audioCache.audioCacheNetworkPolicy }, { v -> v.enumDisplay() }),
    DiffField(Res.string.diff_audio_cache_cellular_cap, { it.audioCache.audioCacheCellularMonthlyCapMb }, Int::toString),
)

private val securityDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_pin_lock_title, { it.security.pinLockEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_pin_set, { it.security.pinHash }, ::pinSet),
    DiffField(Res.string.ss_biometric_lock_title, { it.security.biometricLockEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_pin_for_player_lock_title, { it.security.usePinForPlayerLock }, Boolean::onOff),
    DiffField(Res.string.ss_auto_lock_timer_title, { it.security.autoLockTimerMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_remote_control_enabled_title, { it.security.remoteControlEnabled }, Boolean::onOff),
)

private val notificationsDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.NOTIFICATIONS,
) {
    spec("ss_notifications_enable_title", { it.notification.notificationPreferences.enabled }, Boolean::onOff)
    spec("ss_notification_check_frequency_title", { it.notification.notificationPreferences.checkFrequency }, enumLabel = CheckFrequency::labelResource)
    spec("ss_quiet_hours_title", { it.notification.notificationPreferences.quietHoursEnabled }, Boolean::onOff)
    spec("ss_quiet_start_title", { it.notification.notificationPreferences.quietHoursStart }, ::minutesSuffix)
    spec("ss_quiet_end_title", { it.notification.notificationPreferences.quietHoursEnd }, ::minutesSuffix)
    spec("ss_notification_sound_title", { it.notification.notificationPreferences.soundEnabled }, Boolean::onOff)
    spec("ss_notification_vibrate_title", { it.notification.notificationPreferences.vibrateEnabled }, Boolean::onOff)
    spec("ss_notification_lights_title", { it.notification.notificationPreferences.lightsEnabled }, Boolean::onOff)
    spec("ss_new_episodes_title", { it.notification.notificationPreferences.newEpisodesEnabled }, Boolean::onOff)
    spec("ss_max_per_check_title", { it.notification.notificationPreferences.maxPerCheck }, Int::toString)
    spec("ss_notification_libraries_title", { it.notification.notificationPreferences.libraryConfigs.size }, Int::toString)
    spec("ss_series_notifications_title", { it.notification.notificationPreferences.seriesConfigs.size }, Int::toString)
}

private val screensaverDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.SCREENSAVER,
) {
    spec("settings_categories", { it.screensaver.dreamImageCategories.size }, Int::toString)
    spec("settings_transition_style", { it.screensaver.dreamTransitionStyle }, { v -> v.enumDisplay() })
    spec("settings_ken_burns", { it.screensaver.dreamKenBurnsEnabled }, Boolean::onOff)
    spec("settings_show_title", { it.screensaver.dreamShowTitle }, Boolean::onOff)
    spec("settings_slideshow_interval", { it.screensaver.dreamSlideshowIntervalMs }, Long::millisToSeconds)
    // The SCREENSAVER category's remaining rows — the parental cap, the dim
    // pair, the desktop idle-ambient/Discord/shell-hook rows (empty command
    // = that hook disabled, so the command rows render Off instead of a
    // blank).
    spec("settings_dream_max_parental_rating", { it.screensaver.dreamMaxParentalRating }, { v -> v?.toString() ?: "None" })
    spec("settings_dream_dim_after", { it.screensaver.dreamDimAfterMs }, Long::millisToSeconds)
    spec("settings_dream_dim_percent", { it.screensaver.dreamDimPercent }, Int::toString)
    spec("settings_idle_ambient_enabled", { it.screensaver.idleAmbientEnabled }, Boolean::onOff)
    spec("settings_idle_ambient_timeout", { it.screensaver.idleAmbientTimeoutMin }, Long::toString)
    spec("settings_discord_presence_enabled", { it.screensaver.discordPresenceEnabled }, Boolean::onOff)
    spec("settings_hooks_enabled", { it.screensaver.hooksEnabled }, Boolean::onOff)
    spec("settings_hooks_play_cmd", { it.screensaver.hooksPlayCmd }, { v -> v.ifEmpty { "Off" } })
    spec("settings_hooks_stop_cmd", { it.screensaver.hooksStopCmd }, { v -> v.ifEmpty { "Off" } })
    spec("settings_hooks_ended_cmd", { it.screensaver.hooksEndedCmd }, { v -> v.ifEmpty { "Off" } })
    spec("settings_hooks_idle_cmd", { it.screensaver.hooksIdleCmd }, { v -> v.ifEmpty { "Off" } })
    spec("settings_hooks_idle_ended_cmd", { it.screensaver.hooksIdleEndedCmd }, { v -> v.ifEmpty { "Off" } })
}

private val newsletterDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.NEWSLETTER,
) {
    spec("ss_newsletter_enabled_title", { it.notification.newsletterEnabled }, Boolean::onOff)
    spec("ss_newsletter_delivery_day_title", { it.notification.newsletterDayOfWeek }, Int::toString)
    spec("settings_newsletter_config", { it.notification.enabledNewsletterSections.size }, Int::toString)
    hand(DiffField(Res.string.diff_newsletter_section_order, { it.notification.newsletterSectionOrder.size }, Int::toString))
    hand(DiffField(Res.string.diff_newsletter_last_viewed, { it.notification.newsletterLastViewedMs }, ::lastViewed))
}

private val syncplayCastingDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.SYNCPLAY_CASTING,
) {
    spec("ss_syncplay_join_behavior_title", { it.syncPlayCast.syncPlayJoinBehavior }, { v -> v.enumDisplay() })
    spec("ss_syncplay_tolerance_title", { it.syncPlayCast.syncPlayToleranceMs }, ::milliseconds)
    spec("ss_syncplay_auto_accept_invites_title", { it.syncPlayCast.syncPlayAutoAcceptInvites }, Boolean::onOff)
    spec("ss_casting_strategy_title", { it.syncPlayCast.defaultCastingStrategy }, { v -> v.enumDisplay() })
    spec("ss_background_casting_title", { it.syncPlayCast.backgroundCastingEnabled }, Boolean::onOff)
    spec("ss_preferred_renderer_title", { it.syncPlayCast.preferredRenderer }, ::autoFallback)
    spec("ss_dvr_pre_padding_title", { it.syncPlayCast.dvrPrePaddingMinutes }, Int::toString)
    spec("ss_dvr_post_padding_title", { it.syncPlayCast.dvrPostPaddingMinutes }, Int::toString)
    spec("ss_dvr_recording_quality_title", { it.syncPlayCast.dvrRecordingQuality }, ::identity)
    // The live-stream option rides the playback slice (the knob's store) but
    // resets with SYNCPLAY_CASTING.
    spec("ss_live_stream_option_title", { it.playback.liveStreamOption }, { v -> v.enumDisplay() })
}

// The player-engines category: no spec row carries a search face for it
// (the engine stores have no spec tier) — fully hand-declared.
private val playerEnginesDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_mpv_video_output_title, { it.engine.mpvConfig.videoOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_scaler_title, { it.engine.mpvConfig.scaler }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_debanding_title, { it.engine.mpvConfig.deband }, Boolean::onOff),
    DiffField(Res.string.ss_mpv_interpolation_title, { it.engine.mpvConfig.interpolation }, Boolean::onOff),
    DiffField(Res.string.ss_mpv_audio_output_title, { it.engine.mpvConfig.audioOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_audio_fallback_title, { it.engine.mpvConfig.audioFallback?.enumDisplay() }, ::autoFallback),
    DiffField(Res.string.ss_mpv_buffer_size_title, { it.engine.mpvConfig.demuxerMaxBytes }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_skip_loop_filter_title, { it.engine.mpvConfig.skipLoopFilter }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_frame_drop_title, { it.engine.mpvConfig.frameDrop }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_hwdec_override_title, { it.engine.mpvConfig.hwdecOverride?.enumDisplay() }, ::autoFallback),
    DiffField(Res.string.ss_vlc_audio_output_title, { it.engine.libVlcConfig.audioOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_vlc_audio_time_stretch_title, { it.engine.libVlcConfig.audioTimeStretch }, Boolean::onOff),
    DiffField(Res.string.ss_vlc_network_caching_title, { it.engine.libVlcConfig.networkCaching }, { ms -> "$ms ms" }),
    DiffField(Res.string.ss_vlc_video_output_title, { it.engine.libVlcConfig.videoOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_vlc_skip_loop_filter_title, { it.engine.libVlcConfig.skipLoopFilter }, Int::toString),
    DiffField(Res.string.diff_vlc_skip_frame, { it.engine.libVlcConfig.skipFrame }, Int::toString),
    DiffField(Res.string.ss_vlc_decoder_threads_title, { it.engine.libVlcConfig.decoderThreads }, Int::toString),
    DiffField(Res.string.ss_vlc_drop_late_frames_title, { it.engine.libVlcConfig.dropLateFrames }, Boolean::onOff),
    DiffField(Res.string.ss_vlc_skip_frames_title, { it.engine.libVlcConfig.skipFrames }, Boolean::onOff),
    DiffField(Res.string.ss_exo_video_scaling_title, { it.engine.exoPlayerConfig.videoScalingMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_exo_frame_rate_strategy_title, { it.engine.exoPlayerConfig.frameRateStrategy }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_exo_preferred_codecs_title, { it.engine.exoPlayerConfig.preferredVideoMimeTypes.size }, Int::toString),
    DiffField(Res.string.ss_exo_skip_silence_title, { it.engine.exoPlayerConfig.skipSilence }, Boolean::onOff),
    DiffField(Res.string.ss_exo_audio_offload_title, { it.engine.exoPlayerConfig.audioOffloadMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_exo_back_buffer_title, { it.engine.exoPlayerConfig.backBufferDurationMs }, Int::toString),
    DiffField(Res.string.ss_exo_decoder_fallback_title, { it.engine.exoPlayerConfig.enableDecoderFallback }, Boolean::onOff),
)

// The experimental category: its three search faces are the members of the
// ONE enabled_experimental_features set key, surfaced by the single count row
// below (each member ledgered as an omission in its diffRows builder) —
// per-member rows would triple-count a single reset key.
private val experimentalDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.EXPERIMENTAL,
) {
    hand(DiffField(Res.string.diff_experimental_enabled_features, { it.experimental.enabledExperimentalFeatures.size }, Int::toString))
    hand(DiffField(Res.string.diff_show_advanced_settings, { it.appearance.showAdvancedSettings }, Boolean::onOff))
}

private val miscAppDiffFields: List<DiffField<*>> = diffRows(
    PreferenceResetCategory.MISC_APP,
) {
    spec("ss_haptics_enabled_title", { it.appearance.hapticsEnabled }, Boolean::onOff)
    hand(DiffField(Res.string.diff_self_update_check, { it.experimental.selfUpdateCheckEnabled }, Boolean::onOff))
    hand(DiffField(Res.string.diff_self_update_download, { it.experimental.selfUpdateDownloadEnabled }, Boolean::onOff))
    hand(DiffField(Res.string.diff_update_dismiss_period, { it.experimental.updateDismissPeriod }, { v -> v.enumDisplay() }))
    spec("ss_app_language_title", { it.experimental.appLanguage }, ::systemFallback)
    hand(DiffField(Res.string.ss_user_data_sync_title, { it.playback.userDataSyncEnabled }, Boolean::onOff))
    spec("ss_show_share_media_title", { it.experimental.showShareMediaOption }, Boolean::onOff)
    spec("ss_hide_search_history_title", { it.experimental.hideSearchHistory }, Boolean::onOff)
    spec("ss_android_tv_watch_next_title", { it.playback.androidTvWatchNextEnabled }, Boolean::onOff)
    hand(DiffField(Res.string.ss_audio_description_title, { it.experimental.preferAudioDescription }, Boolean::onOff))
}

// ---------------------------------------------------------------------------
// Diff-row formatting primitives (label text resolves via [PreferenceDiffSnapshot]).
// ---------------------------------------------------------------------------

private fun <T> identity(value: T): String = value.toString()
private fun hour(hour: Int): String = "$hour:00"
private fun milliseconds(ms: Long): String = "$ms ms"
private fun minutesSuffix(minutes: Int): String = "$minutes min"
private fun decibels(gain: Int): String = "$gain dB"
private fun speedMultiplier(speed: Float): String = speed.fmt1() + "x"
private fun pinSet(pinHash: String?): String = if (pinHash != null) "Yes" else "No"
private fun systemFallback(value: String?): String = value ?: "System"
private fun autoFallback(value: String?): String = value ?: "Auto"
private fun lastViewed(ms: Long): String = if (ms == 0L) "Never" else "${ms}ms"

// ---------------------------------------------------------------------------
// App runtime fields — surfaced only in import preview ("everything").
// Not part of `PreferenceResetCategory`; factory reset intentionally omits them.
// Kept in this file (not a separate module) because `core:datastore` cannot
// depend on `feature:settings` for the presentation registry — the registry
// already lives here and `AppRuntimeState` is a pure model type (no UI dep).
// The call site is the import-preview screen itself (which passes the
// localized none-label), so these five rows keep literal labels until that
// seam moves label resolution screen-side too.
// ---------------------------------------------------------------------------

/**
 * User-facing diff for the `AppRuntimeState` extras carried in a v2 backup.
 * Mirrors the [PreferenceField] shape so import-preview can reuse
 * `PreferenceDiffCategoryItem` / `PreferenceFieldRow`.
 *
 * @param noneLabel localized label for empty/absent values (pass
 * `stringResource(R.string.settings_unknown)` from the composable caller so
 * this pure function does not need a @Composable context and can be used
 * inside `remember`).
 */
fun appRuntimeFields(
    current: AppRuntimeState,
    incoming: AppRuntimeState,
    noneLabel: String = "None",
): List<PreferenceField> = listOf(
    PreferenceField(
        "Favorite Channels",
        current.favoriteChannels.sorted().joinToString(", ").ifEmpty { noneLabel },
        incoming.favoriteChannels.sorted().joinToString(", ").ifEmpty { noneLabel },
    ),
    PreferenceField(
        "Last Live-TV Channel",
        current.liveTvLastChannelId ?: noneLabel,
        incoming.liveTvLastChannelId ?: noneLabel,
    ),
    PreferenceField(
        "Watch Later Playlist",
        current.watchLaterPlaylistId ?: noneLabel,
        incoming.watchLaterPlaylistId ?: noneLabel,
    ),
    PreferenceField(
        "Onboarding Completed",
        current.onboardingCompleted.onOff(),
        incoming.onboardingCompleted.onOff(),
    ),
    PreferenceField(
        "Recent DLNA Devices",
        if (current.recentDlnaDevices.isEmpty()) noneLabel else "${current.recentDlnaDevices.size} devices",
        if (incoming.recentDlnaDevices.isEmpty()) noneLabel else "${incoming.recentDlnaDevices.size} devices",
    ),
)

/**
 * Resolves the diff rows' label resources once per snapshot generation. The
 * ViewModels call this when building a [PreferenceDiffSnapshot]; the map key
 * fallback (`toString`) exists only for a resource-system failure and is never
 * hit in production.
 */
suspend fun resolveDiffLabels(resources: List<StringResource>): (StringResource) -> String {
    val resolved = resources.distinct().associateWith { res ->
        runCatchingRethrowingCancellation { getString(res) }.getOrElse { res.toString() }
    }
    return { res -> resolved[res] ?: res.toString() }
}

// ---------------------------------------------------------------------------
// External backup slices — the Room-backed / allowlisted-config
// cards the import preview renders beside the category cards. Like the
// app-runtime fields above, they live here because the presentation registry
// is this file and the slice KEYS are stable wire contracts owned by
// core:datastore (no UI dep).
// ---------------------------------------------------------------------------

/** Card presentation for one external slice: its wire [ExternalSliceCardView.key], icon and human label. */
@Immutable
data class ExternalSliceCardView(
    val key: String,
    val icon: ImageVector,
    val nameRes: StringResource,
)

/**
 * The four external slices in card order. A backup from an app version
 * without them simply carries none of these keys — the preview renders no
 * cards (unknown/missing slice keys are ignored, never a crash).
 */
val ExternalSliceCardViews: List<ExternalSliceCardView> = listOf(
    ExternalSliceCardView(BackupSliceKey.INTEGRATIONS, Tabler.Outline.Plug, Res.string.settings_import_slice_integrations),
    ExternalSliceCardView(BackupSliceKey.ITEM_PREFS, Tabler.Outline.AdjustmentsHorizontal, Res.string.settings_import_slice_item_prefs),
    ExternalSliceCardView(BackupSliceKey.PLAYLISTS, Tabler.Outline.Playlist, Res.string.settings_import_slice_playlists),
    ExternalSliceCardView(BackupSliceKey.WIDGET, Tabler.Outline.LayoutGrid, Res.string.settings_import_slice_widget),
)

/** Every label resource the external-slice cards need (VM-side one-shot resolution workload). */
val ExternalSliceLabelResources: List<StringResource> =
    (ExternalSliceCardViews.map { it.nameRes } + listOf(
        Res.string.settings_import_slice_entry_absent,
        Res.string.settings_import_slice_entry_removed,
    )).distinct()

/** One rendered external-slice card: its identity, icon/label, and the entry diff rows. */
@Immutable
data class ExternalSliceDiff(
    val view: ExternalSliceCardView,
    val changed: List<PreferenceField>,
    val total: Int,
)

/**
 * Builds the external-slice cards for one staged backup: a card only for a
 * slice the backup actually carries (absent = the exporting device had
 * nothing — importing must not wipe, so nothing to preview), with one
 * [PreferenceField] per entry key. Entry labels are the raw keys (item ids /
 * allowlist names); values are primitives verbatim and non-primitives as
 * compact JSON — the diff string comparison is then exactly element equality.
 * [currentElement] null means the live domain has nothing to back up (every
 * incoming entry is new); an entry missing from one side renders the
 * localized absent/removed label.
 */
fun buildExternalSliceDiffs(
    currentSlices: Map<String, kotlinx.serialization.json.JsonElement?>,
    incomingSlices: Map<String, kotlinx.serialization.json.JsonElement?>,
    resolve: (StringResource) -> String,
): List<ExternalSliceDiff> = ExternalSliceCardViews.mapNotNull { view ->
    val incoming = incomingSlices[view.key] as? kotlinx.serialization.json.JsonObject
        ?: return@mapNotNull null
    val current = currentSlices[view.key] as? kotlinx.serialization.json.JsonObject
    val absentLabel = resolve(Res.string.settings_import_slice_entry_absent)
    val removedLabel = resolve(Res.string.settings_import_slice_entry_removed)
    val entryKeys = (current?.keys ?: emptySet()) + incoming.keys
    val fields = entryKeys.sorted().map { entryKey ->
        val incomingValue = incoming[entryKey]
        val currentValue = current?.get(entryKey)
        PreferenceField(
            label = entryKey,
            currentValue = currentValue.renderExternalEntry(removedLabel),
            factoryValue = incomingValue.renderExternalEntry(absentLabel),
        )
    }
    ExternalSliceDiff(view, fields.filter { it.changed }, fields.size)
}

/** Entry text: primitives verbatim, non-primitives as compact JSON, absent sides as the localized label. */
private fun kotlinx.serialization.json.JsonElement?.renderExternalEntry(absentLabel: String): String = when (this) {
    null -> absentLabel
    is kotlinx.serialization.json.JsonPrimitive -> content
    else -> toString()
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * Ordered presentation registry for every [PreferenceResetCategory]. The UI
 * iterates this list; each entry renders its icon, name, and
 * current-vs-baseline rows for the [PreferenceDiffSnapshot] pair the
 * ViewModel exposes.
 */
val PreferenceCategoryViews: List<PreferenceCategoryView> = listOf(
    PreferenceCategoryView(PreferenceResetCategory.APPEARANCE, Tabler.Outline.Palette, Res.string.factory_reset_cat_appearance, appearanceDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.PLAYBACK, Tabler.Outline.PlayerPlay, Res.string.factory_reset_cat_playback, playbackDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.AUDIO, Tabler.Outline.Volume, Res.string.factory_reset_cat_audio, audioDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SUBTITLES_LANGUAGE, Tabler.Outline.Subtitles, Res.string.factory_reset_cat_subtitles_language, subtitlesLanguageDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.DOWNLOADS_NETWORK, Tabler.Outline.Download, Res.string.factory_reset_cat_downloads_network, downloadsNetworkDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.HOME_DISCOVERY, Tabler.Outline.Home, Res.string.factory_reset_cat_home_discovery, homeDiscoveryDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.AUDIO_CACHE, Tabler.Outline.Music, Res.string.factory_reset_cat_audio_cache, audioCacheDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SECURITY, Tabler.Outline.ShieldLock, Res.string.factory_reset_cat_security, securityDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.NOTIFICATIONS, Tabler.Outline.Bell, Res.string.factory_reset_cat_notifications, notificationsDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SCREENSAVER, Tabler.Outline.Moon, Res.string.factory_reset_cat_screensaver, screensaverDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.NEWSLETTER, Tabler.Outline.PlayerTrackNext, Res.string.factory_reset_cat_newsletter, newsletterDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SYNCPLAY_CASTING, Tabler.Outline.ScreenShare, Res.string.factory_reset_cat_syncplay_casting, syncplayCastingDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.PLAYER_ENGINES, Tabler.Outline.Cpu, Res.string.factory_reset_cat_player_engines, playerEnginesDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.EXPERIMENTAL, Tabler.Outline.Flask, Res.string.factory_reset_cat_experimental, experimentalDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.MISC_APP, Tabler.Outline.Settings, Res.string.factory_reset_cat_misc_app, miscAppDiffFields),
)
