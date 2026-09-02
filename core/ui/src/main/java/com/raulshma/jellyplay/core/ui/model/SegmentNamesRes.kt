package com.raulshma.jellyplay.core.ui.model

import androidx.annotation.StringRes
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.R

/**
 * `@StringRes Int` halves of the segment/behavior label tables. The
 * `@Composable` halves live in `shared/core/ui` over Compose Resources; these
 * id-based variants stay for legacy consumers and die at cutover (plan §Phase X).
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

// endregion
