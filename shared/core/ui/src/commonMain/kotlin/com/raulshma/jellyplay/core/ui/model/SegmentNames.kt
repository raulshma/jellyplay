package com.raulshma.jellyplay.core.ui.model

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_behavior_auto_skip
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_behavior_auto_skip_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_behavior_ignore
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_behavior_ignore_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_behavior_show_button
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_behavior_show_button_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_commercial
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_commercial_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_intro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_intro_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_outro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_outro_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_preview
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_preview_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_recap
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_recap_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_skip_commercial
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_skip_intro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_skip_outro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_skip_preview
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_skip_recap
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_skip_unknown
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_unknown
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_unknown_desc
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable display labels for [MediaSegmentType] and [SegmentBehavior].
 *
 * The model enums themselves have no resource access (core:model cannot depend on
 * resources), so display strings are resolved here at the UI layer. These
 * mappings live in `core:ui` so every feature screen (player, settings, …) shares
 * one source of truth and translators get a single set of `core_segment_*` keys.
 *
 * The `@StringRes Int` halves stay in the legacy `:core:ui` shim until every
 * consumer has migrated off resource ids (plan §Phase X).
 */

// region MediaSegmentType

/** Localized display name for this segment type. */
@Composable
fun MediaSegmentType.localizedDisplayName(): String = stringResource(
    when (this) {
        MediaSegmentType.INTRO -> Res.string.core_segment_intro
        MediaSegmentType.OUTRO -> Res.string.core_segment_outro
        MediaSegmentType.PREVIEW -> Res.string.core_segment_preview
        MediaSegmentType.RECAP -> Res.string.core_segment_recap
        MediaSegmentType.COMMERCIAL -> Res.string.core_segment_commercial
        MediaSegmentType.UNKNOWN -> Res.string.core_segment_unknown
    },
)

/** Localized description for this segment type. */
@Composable
fun MediaSegmentType.localizedDescription(): String = stringResource(
    when (this) {
        MediaSegmentType.INTRO -> Res.string.core_segment_intro_desc
        MediaSegmentType.OUTRO -> Res.string.core_segment_outro_desc
        MediaSegmentType.PREVIEW -> Res.string.core_segment_preview_desc
        MediaSegmentType.RECAP -> Res.string.core_segment_recap_desc
        MediaSegmentType.COMMERCIAL -> Res.string.core_segment_commercial_desc
        MediaSegmentType.UNKNOWN -> Res.string.core_segment_unknown_desc
    },
)

/** Localized skip-button label for this segment type. */
@Composable
fun MediaSegmentType.localizedSkipLabel(): String = stringResource(
    when (this) {
        MediaSegmentType.INTRO -> Res.string.core_segment_skip_intro
        MediaSegmentType.OUTRO -> Res.string.core_segment_skip_outro
        MediaSegmentType.PREVIEW -> Res.string.core_segment_skip_preview
        MediaSegmentType.RECAP -> Res.string.core_segment_skip_recap
        MediaSegmentType.COMMERCIAL -> Res.string.core_segment_skip_commercial
        MediaSegmentType.UNKNOWN -> Res.string.core_segment_skip_unknown
    },
)

// endregion

// region SegmentBehavior

/** Localized display name for this segment behavior. */
@Composable
fun SegmentBehavior.localizedDisplayName(): String = stringResource(
    when (this) {
        SegmentBehavior.SHOW_BUTTON -> Res.string.core_segment_behavior_show_button
        SegmentBehavior.AUTO_SKIP -> Res.string.core_segment_behavior_auto_skip
        SegmentBehavior.IGNORE -> Res.string.core_segment_behavior_ignore
    },
)

/** Localized description for this segment behavior. */
@Composable
fun SegmentBehavior.localizedDescription(): String = stringResource(
    when (this) {
        SegmentBehavior.SHOW_BUTTON -> Res.string.core_segment_behavior_show_button_desc
        SegmentBehavior.AUTO_SKIP -> Res.string.core_segment_behavior_auto_skip_desc
        SegmentBehavior.IGNORE -> Res.string.core_segment_behavior_ignore_desc
    },
)

// endregion
