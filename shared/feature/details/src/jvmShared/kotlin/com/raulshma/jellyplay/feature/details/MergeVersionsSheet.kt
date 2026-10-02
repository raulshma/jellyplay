package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Stack2
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cancel
import com.raulshma.jellyplay.feature.details.generated.resources.detail_merge_confirm
import com.raulshma.jellyplay.feature.details.generated.resources.detail_merge_count_selected
import com.raulshma.jellyplay.feature.details.generated.resources.detail_merge_no_candidates
import com.raulshma.jellyplay.feature.details.generated.resources.detail_merge_searching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_merge_versions_subtitle
import com.raulshma.jellyplay.feature.details.generated.resources.detail_merge_versions_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "Merge versions…" multi-select sheet (jellyfin-web parity): lists the
 * same-named candidate items found in the library (the split-apart versions),
 * lets the admin tick the ones to fold into the CURRENT item — which is the
 * implicit merge target and is NOT listed — and confirms with the count in
 * the button. Reuses the episode multi-select pattern from the series
 * delete sheet: checkbox rows + a footer Cancel/confirm pair.
 *
 * RequiresElevation server-side; the entry is admin-gated upstream.
 */
@Composable
internal fun MergeVersionsSheet(
    candidates: List<MediaItem>,
    isLoading: Boolean,
    isMerging: Boolean,
    onMerge: (candidateIds: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember(candidates) { mutableStateOf(emptySet<String>()) }
    val canConfirm = selectedIds.isNotEmpty() && !isMerging

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Cap the sheet body so the Cancel/Merge actions stay visible
            // without scrolling. Matches the series delete sheet.
            .heightIn(max = 560.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(Res.string.detail_merge_versions_title),
            subtitle = stringResource(Res.string.detail_merge_versions_subtitle),
            icon = Tabler.Outline.Stack2,
            onClose = onDismiss,
        )

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Text(
                    text = stringResource(Res.string.detail_merge_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            candidates.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.detail_merge_no_candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(candidates, key = { it.id }, contentType = { "mergeCandidate" }) { candidate ->
                        val isSelected = candidate.id in selectedIds
                        val rowFocusState = rememberTvFocusState(focusedScale = 1.02f)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeCache.smooth12)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .then(rowFocusState.focusModifier)
                                .then(Modifier.tvFocusIndicator(rowFocusState, ShapeCache.smooth12))
                                .clickable {
                                    selectedIds = if (isSelected) {
                                        selectedIds - candidate.id
                                    } else {
                                        selectedIds + candidate.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                candidate.year?.let { year ->
                                    Text(
                                        text = year.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Footer actions ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedIds.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        Res.plurals.detail_merge_count_selected,
                        selectedIds.size,
                        selectedIds.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(
                onClick = onDismiss,
                shape = ShapeCache.smoothPill,
            ) {
                Text(stringResource(Res.string.detail_cancel))
            }
            Button(
                onClick = { onMerge(selectedIds) },
                enabled = canConfirm,
                shape = ShapeCache.smoothPill,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Tabler.Outline.Stack2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.detail_merge_confirm))
            }
        }
    }
}
