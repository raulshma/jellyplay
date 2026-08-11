package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Subtitles
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Manifest-backed local subtitle selector for the unified media-detail screen.
 *
 * Distinct from the remote [MediaInfoSection]: a local origin has no full
 * [MediaSource] / audio-stream inventory, so this renders ONLY the downloaded
 * external subtitle list ([LocalSubtitleOption]) plus an "Off" entry. Selection
 * persists via [DetailViewModel.selectLocalSubtitle].
 *
 * Gated by `capabilities.localSubtitleSelection` at the call site. Rendered as
 * a single info pill (current selection / Off) that opens a [TvSafeSheet] with
 * the full list — matches the remote picker's pill + sheet affordance so the
 * two feel consistent.
 *
 * The chosen index is persisted via [DetailViewModel.selectLocalSubtitle] into
 * `mediaStreamSelections[itemId]`; the player resolves the side-loaded subtitle
 * by its `offline:${index}` id through `TrackSelectionPolicy.resolveByOfflineSubtitleId`
 * (and `onPlayClick` also threads it through `Route.VideoPlayer.subtitleStreamIndex`
 * as belt-and-suspenders for a local source).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun LocalSubtitlePicker(
    subtitles: List<LocalSubtitleOption>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (subtitles.isEmpty()) return
    var sheetOpen by remember { mutableStateOf(false) }
    val current = subtitles.firstOrNull { it.index == selectedIndex }
    val offLabel = stringResource(R.string.detail_local_subtitle_off)
    val label = current?.displayLabel() ?: offLabel

    QuickInfoPill(
        icon = Tabler.Outline.Subtitles,
        text = label,
        showTrailingIndicator = true,
        onClick = { sheetOpen = true },
        modifier = modifier.fillMaxWidth(),
    )

    if (sheetOpen) {
        TvSafeSheet(
            onDismissRequest = { sheetOpen = false },
            title = stringResource(R.string.detail_local_subtitles),
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // "Off" entry — deselects (index = null).
                item(key = "local_sub_off", contentType = "localSubOption") {
                    LocalSubtitleOptionRow(
                        label = offLabel,
                        isSelected = selectedIndex == null,
                        isDefault = false,
                        forcedBadge = false,
                        onClick = {
                            onSelect(null)
                            sheetOpen = false
                        },
                    )
                }
                items(subtitles, key = { "local_sub_${it.index}" }, contentType = { "localSubOption" }) { option ->
                    LocalSubtitleOptionRow(
                        label = option.displayLabel(),
                        isSelected = option.index == selectedIndex,
                        isDefault = option.isDefault,
                        forcedBadge = option.isForced,
                        onClick = {
                            onSelect(option.index)
                            sheetOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun LocalSubtitleOptionRow(
    label: String,
    isSelected: Boolean,
    isDefault: Boolean,
    forcedBadge: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "localSubOptionScale",
    )
    val focusState = rememberTvFocusState(focusedScale = 1.03f)
    val forcedLabel = stringResource(R.string.detail_local_subtitle_forced_badge)
    val defaultLabel = stringResource(R.string.detail_local_subtitle_default_badge)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isDefault) {
            Text(
                text = defaultLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (forcedBadge) {
            Text(
                text = forcedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Tabler.Outline.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
