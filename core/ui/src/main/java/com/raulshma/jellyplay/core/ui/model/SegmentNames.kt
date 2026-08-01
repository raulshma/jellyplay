package com.raulshma.jellyplay.core.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.R

/**
 * Localizable display labels for [MediaSegmentType] and [SegmentBehavior].
 *
 * The model enums themselves have no resource access (core:model cannot depend on
 * Android resources), so display strings are resolved here at the UI layer. These
 * mappings live in `core:ui` so every feature screen (player, settings, …) shares
 * one source of truth and translators get a single set of `core_segment_*` keys.
 *
 * Prefer the `@Composable` helpers inside composable scope; use the `Res`
 * variants when you need a `@StringRes Int`.
 */

// region MediaSegmentType

/** Display-name resource for this segment type (e.g. "Intro", "Outro"). */
@StringRes
fun MediaSegmentType.displayNameRes(): Int = when (this) {
    MediaSegmentType.INTRO -> R.string.core_segment_intro
    MediaSegmentType.OUTRO -> R.string.core_segment_outro
    MediaSegmentType.PREVIEW -> R.string.core_segment_preview
    MediaSegmentType.RECAP -> R.string.core_segment_recap
    MediaSegmentType.COMMERCIAL -> R.string.core_segment_commercial
    MediaSegmentType.UNKNOWN -> R.string.core_segment_unknown
}

/** Description resource for this segment type. */
@StringRes
fun MediaSegmentType.descriptionRes(): Int = when (this) {
    MediaSegmentType.INTRO -> R.string.core_segment_intro_desc
    MediaSegmentType.OUTRO -> R.string.core_segment_outro_desc
    MediaSegmentType.PREVIEW -> R.string.core_segment_preview_desc
    MediaSegmentType.RECAP -> R.string.core_segment_recap_desc
    MediaSegmentType.COMMERCIAL -> R.string.core_segment_commercial_desc
    MediaSegmentType.UNKNOWN -> R.string.core_segment_unknown_desc
}

/** Skip-button label resource for this segment type (e.g. "Skip Intro"). */
@StringRes
fun MediaSegmentType.skipLabelRes(): Int = when (this) {
    MediaSegmentType.INTRO -> R.string.core_segment_skip_intro
    MediaSegmentType.OUTRO -> R.string.core_segment_skip_outro
    MediaSegmentType.PREVIEW -> R.string.core_segment_skip_preview
    MediaSegmentType.RECAP -> R.string.core_segment_skip_recap
    MediaSegmentType.COMMERCIAL -> R.string.core_segment_skip_commercial
    MediaSegmentType.UNKNOWN -> R.string.core_segment_skip_unknown
}

/** Localized display name for this segment type. */
@Composable
fun MediaSegmentType.localizedDisplayName(): String = stringResource(displayNameRes())

/** Localized description for this segment type. */
@Composable
fun MediaSegmentType.localizedDescription(): String = stringResource(descriptionRes())

/** Localized skip-button label for this segment type. */
@Composable
fun MediaSegmentType.localizedSkipLabel(): String = stringResource(skipLabelRes())

// endregion

// region SegmentBehavior

/** Display-name resource for this segment behavior (e.g. "Show Button"). */
@StringRes
fun SegmentBehavior.displayNameRes(): Int = when (this) {
    SegmentBehavior.SHOW_BUTTON -> R.string.core_segment_behavior_show_button
    SegmentBehavior.AUTO_SKIP -> R.string.core_segment_behavior_auto_skip
    SegmentBehavior.IGNORE -> R.string.core_segment_behavior_ignore
}

/** Description resource for this segment behavior. */
@StringRes
fun SegmentBehavior.descriptionRes(): Int = when (this) {
    SegmentBehavior.SHOW_BUTTON -> R.string.core_segment_behavior_show_button_desc
    SegmentBehavior.AUTO_SKIP -> R.string.core_segment_behavior_auto_skip_desc
    SegmentBehavior.IGNORE -> R.string.core_segment_behavior_ignore_desc
}

/** Localized display name for this segment behavior. */
@Composable
fun SegmentBehavior.localizedDisplayName(): String = stringResource(displayNameRes())

/** Localized description for this segment behavior. */
@Composable
fun SegmentBehavior.localizedDescription(): String = stringResource(descriptionRes())

// endregion
