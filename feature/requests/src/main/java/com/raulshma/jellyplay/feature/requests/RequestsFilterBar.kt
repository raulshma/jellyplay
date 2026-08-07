package com.raulshma.jellyplay.feature.requests

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestFilter
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSort
import com.raulshma.jellyplay.core.ui.animation.horizontalFadingEdges
import com.raulshma.jellyplay.core.ui.components.SearchField
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.requests.R

@Composable
fun RequestsFilterBar(
    filters: RequestsFilterState,
    isAdmin: Boolean,
    onFilterChange: (SeerrRequestFilter) -> Unit,
    onMediaTypeChange: (String?) -> Unit,
    onSortChange: (SeerrRequestSort) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onMyRequestsToggle: () -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val currentFilter = filters.filter
    val currentMediaType = filters.mediaType
    val currentSort = filters.sort
    val currentSortDirection = filters.sortDirection
    val showMyRequestsOnly = filters.showMyRequestsOnly
    val searchQuery = filters.searchQuery
    val colorScheme = MaterialTheme.colorScheme
    val filterScrollState = rememberScrollState()
    val optionsScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Free-text search. Debounced at the VM layer so each keystroke
        // doesn't fire a request.
        SearchField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.requests_search_placeholder),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdges(filterScrollState)
                .horizontalScroll(filterScrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeerrRequestFilter.entries.forEach { filter ->
                val isSelected = currentFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChange(filter) },
                    modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    SeerrRequestFilter.ALL -> R.string.requests_filter_all
                                    SeerrRequestFilter.PENDING -> R.string.requests_filter_pending
                                    SeerrRequestFilter.APPROVED -> R.string.requests_filter_approved
                                    SeerrRequestFilter.PROCESSING -> R.string.requests_filter_processing
                                    SeerrRequestFilter.AVAILABLE -> R.string.requests_filter_available
                                    SeerrRequestFilter.UNAVAILABLE -> R.string.requests_filter_unavailable
                                    SeerrRequestFilter.FAILED -> R.string.requests_filter_failed
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = colorScheme.primary,
                        containerColor = colorScheme.onSurface.copy(alpha = 0.06f),
                        labelColor = colorScheme.onSurfaceVariant,
                    ),
                    border = null,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdges(optionsScrollState)
                .horizontalScroll(optionsScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(null, "movie", "tv").forEach { type ->
                val label = when (type) {
                    null -> stringResource(R.string.requests_media_all)
                    "movie" -> stringResource(R.string.requests_media_movies)
                    else -> stringResource(R.string.requests_media_tv)
                }
                val isSelected = currentMediaType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onMediaTypeChange(type) },
                    modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.secondaryContainer,
                        selectedLabelColor = colorScheme.onSecondaryContainer,
                        containerColor = colorScheme.onSurface.copy(alpha = 0.06f),
                        labelColor = colorScheme.onSurfaceVariant,
                    ),
                    border = null,
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.5f))
            )

            listOf(
                SeerrRequestSort.ADDED to stringResource(R.string.requests_sort_recent),
                SeerrRequestSort.MODIFIED to stringResource(R.string.requests_sort_modified),
            ).forEach { (sort, label) ->
                val isSelected = currentSort == sort
                FilterChip(
                    selected = isSelected,
                    onClick = { onSortChange(sort) },
                    modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                    label = {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.tertiaryContainer,
                        selectedLabelColor = colorScheme.onTertiaryContainer,
                        containerColor = colorScheme.onSurface.copy(alpha = 0.06f),
                        labelColor = colorScheme.onSurfaceVariant,
                    ),
                    border = null,
                )
            }

            androidx.compose.material3.IconButton(
                onClick = onSortDirectionToggle,
                modifier = Modifier
                    .size(32.dp)
                    .focusIndicator(CircleShape),
            ) {
                Icon(
                    if (currentSortDirection == "desc") Tabler.Outline.ArrowDown else Tabler.Outline.ArrowUp,
                    contentDescription = stringResource(R.string.requests_cd_sort_direction),
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }

            if (isAdmin) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(ShapeCache.smooth8)
                        .focusIndicator()
                        .clickable { onMyRequestsToggle() }
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.requests_filter_mine),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showMyRequestsOnly) colorScheme.primary else colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = showMyRequestsOnly,
                        onCheckedChange = { onMyRequestsToggle() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colorScheme.primary,
                        ),
                        modifier = Modifier.height(24.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}
