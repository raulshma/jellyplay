package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaSourceType
import com.raulshma.jellyplay.core.ui.animation.pressScale
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_version_grouping_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_version_placeholder_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_version_select
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One resolved row of the detail screen's version picker — the pure output of
 * [buildVersionPickerOptions], unit-testable without composing anything.
 */
internal data class VersionPickerOption(
    /** The media source id handed back through the play dispatch. */
    val sourceId: String,
    /** Server version name, else the derived quality/container fallback. */
    val label: String,
    /** Derived quality pill ("4K HDR10") shown when the name doesn't already carry it. */
    val qualityBadge: String? = null,
    /** Non-null when the source is a Grouping/Placeholder stub rather than a file. */
    val typeBadge: StringResource? = null,
    val isSelected: Boolean,
)

/**
 * Pure selection-state derivation for the version picker: one option per
 * media source, the user's pick (or nothing) marked as selected. Grouping /
 * Placeholder sources carry a type badge so a merged item's stub rows are
 * distinguishable from real files. Pure → directly unit-testable.
 */
internal fun buildVersionPickerOptions(
    detail: MediaDetail,
    selectedSourceId: String?,
): List<VersionPickerOption> = detail.mediaSources.map { source ->
    VersionPickerOption(
        sourceId = source.id,
        label = source.versionLabel(),
        qualityBadge = source.name.takeIf { it.isNotBlank() }?.let { source.qualityLabel() },
        typeBadge = when (source.type) {
            MediaSourceType.DEFAULT -> null
            MediaSourceType.GROUPING -> Res.string.detail_version_grouping_badge
            MediaSourceType.PLACEHOLDER -> Res.string.detail_version_placeholder_badge
        },
        isSelected = source.id == selectedSourceId,
    )
}

/**
 * The detail screen's version picker (multi-version items): a `TvSafeSheet`
 * listing every [MediaSource] with its derived quality badge, the currently
 * chosen version marked, mirroring the stream picker's row styling so the two
 * pickers read as siblings. Selection only updates the pending pick — the
 * Play button dispatches it through the existing `onPlayClick` id argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VersionPickerSheet(
    detail: MediaDetail,
    selectedSourceId: String?,
    onSelect: (sourceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(detail, selectedSourceId) {
        buildVersionPickerOptions(detail, selectedSourceId)
    }
    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.detail_version_select),
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { it.sourceId }, contentType = { "versionOption" }) { option ->
                val optionInteractionSource = remember { MutableInteractionSource() }
                val optionFocusState = rememberTvFocusState(focusedScale = 1.03f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCache.smooth12)
                        .background(
                            if (option.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        .pressScale(
                            interactionSource = optionInteractionSource,
                            defaultScale = 0.97f,
                            spec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        )
                        .then(optionFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(optionFocusState, ShapeCache.smooth12))
                        .clickable(
                            interactionSource = optionInteractionSource,
                            indication = null,
                        ) {
                            onSelect(option.sourceId)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (option.typeBadge != null) {
                        Text(
                            text = stringResource(option.typeBadge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    } else if (option.qualityBadge != null) {
                        Text(
                            text = option.qualityBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (option.isSelected) {
                        Icon(
                            imageVector = Tabler.Outline.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
