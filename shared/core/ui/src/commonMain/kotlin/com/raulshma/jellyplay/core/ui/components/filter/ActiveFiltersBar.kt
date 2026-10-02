package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ActiveFilterTag
import com.raulshma.jellyplay.core.model.LibraryFilterDimension
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_clear_all
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_has_subtitles
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_has_trailer
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_in_progress
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_played_all
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_played_played
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_played_unplayed
import com.raulshma.jellyplay.core.ui.generated.resources.core_filter_rating_plus
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_downloaded
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayName
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import org.jetbrains.compose.resources.stringResource

/**
 * The shared active-filter dismiss-tag bar (Library + Search): one
 * [GlassDismissTag] per [ActiveFilterTag] followed by the clear-all chip,
 * in a wrapping row. The entries come from [LibraryFilters.activeTags] —
 * the canonical fold on the filter model both screens read.
 *
 * DELIBERATE UNIFICATIONS (the two hand-rolled bars had drifted; each is
 * declared here so the delta is a decision, not an accident):
 *  - The clear-all chip is Library's animated variant — press-scale +
 *    shape-morph + TV focus glow — adopted for Search too, whose chip was a
 *    static shape. The richer variant wins for both.
 *  - Labels resolve through ONE table, [ActiveFilterTag.filterTagLabel].
 *    That upgrades Library's raw `MediaType.name` / English-only
 *    `PlayedStatus.displayName` tag labels to the localized vocabulary Search
 *    already used (same visible copy in English; translatable now).
 *  - The clear-all copy is the shared `core_clear_all` ("Clear all") — the
 *    exact text both screens showed, as one resource replacing the per-feature
 *    `library_clear_all` / `search_clear_all`.
 *  - The chip's clickable gains `Role.Button` (Search had it, Library didn't).
 *
 * The bar renders exactly the entries it is given — dimension subset and
 * order are the caller's choice via [LibraryFilters.activeTags]' `dimensions`
 * parameter — so each screen's bar keeps its own shape, and a dimension only
 * one screen surfaces just works. Dismiss/clear semantics live in the tags'
 * `clear()` writes; this composable is presentation only.
 *
 * Not animated in/out here: both screens keep their identical
 * `AnimatedVisibility(visible = hasActiveFilters)` wrapper (and since
 * `hasActiveFilters` also counts the sort dimension, a sort-only filter state
 * still shows this bar with just the clear-all chip — as before).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFiltersBar(
    tags: List<ActiveFilterTag>,
    onTagDismiss: (ActiveFilterTag) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            GlassDismissTag(
                label = tag.filterTagLabel(),
                onDismiss = { onTagDismiss(tag) },
            )
        }
        ClearAllFiltersChip(onClick = onClearAll)
    }
}

/**
 * The ONE label-resolution table for active-filter entries — the single place
 * a [LibraryFilterDimension] maps to its localized label, shared by every
 * [ActiveFiltersBar] caller. Genre/year/tag values are raw user data and
 * render verbatim (as before on both screens); everything else resolves to a
 * core:ui resource.
 */
@Composable
fun ActiveFilterTag.filterTagLabel(): String = when (dimension) {
    LibraryFilterDimension.MEDIA_TYPES -> MediaType.valueOf(value).mediaTypeDisplayName()
    LibraryFilterDimension.GENRES,
    LibraryFilterDimension.YEARS,
    LibraryFilterDimension.TAGS,
    -> value
    LibraryFilterDimension.MIN_RATING -> stringResource(Res.string.core_filter_rating_plus, value)
    LibraryFilterDimension.PLAYED_STATUS -> PlayedStatus.valueOf(value).filterStatusLabel()
    LibraryFilterDimension.IS_RESUMABLE -> stringResource(Res.string.core_filter_in_progress)
    LibraryFilterDimension.IS_DOWNLOADED -> stringResource(Res.string.core_ui_downloaded)
    LibraryFilterDimension.HAS_SUBTITLES -> stringResource(Res.string.core_filter_has_subtitles)
    LibraryFilterDimension.HAS_TRAILER -> stringResource(Res.string.core_filter_has_trailer)
}

/** Localized played-status label for the shared bar (only non-ALL is emitted). */
@Composable
private fun PlayedStatus.filterStatusLabel(): String = when (this) {
    PlayedStatus.ALL -> stringResource(Res.string.core_filter_played_all)
    PlayedStatus.PLAYED -> stringResource(Res.string.core_filter_played_played)
    PlayedStatus.UNPLAYED -> stringResource(Res.string.core_filter_played_unplayed)
}

/**
 * The clear-all chip: Library's animated press-scale + shape-morph glass chip
 * (see [ActiveFiltersBar]'s unification notes), rendered after the tags.
 */
@Composable
private fun ClearAllFiltersChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "clearAllPressedScale",
    )
    val shapeMorphProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "clearAllShapeMorph",
    )
    // Derived boolean so the shape swap invalidates the composition only when
    // the morph crosses 0.5f, not on every animation frame (the pattern the
    // shared GlassDismissTag uses).
    val morphed by remember { derivedStateOf { shapeMorphProgress > 0.5f } }
    val shape = if (morphed) ShapeCache.smooth12 else ShapeCache.smooth8

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale * focusState.scale
                scaleY = scale * focusState.scale
            }
            .clip(shape)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(Res.string.core_clear_all),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
