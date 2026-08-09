package com.raulshma.jellyplay.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.search.R

/**
 * Single-select sort picker for the search screen. Mirrors the Library
 * [com.raulshma.jellyplay.feature.library.components.SortFilterSheet] using
 * search-local string resources + the same [GlassFilterChip] idiom. Tapping an
 * option applies it immediately (via [onApply]) and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchSortSheet(
    current: SortOption,
    onApply: (SortOption) -> Unit,
    onDismiss: () -> Unit,
) {
    SearchSelectionSheet(title = stringResource(R.string.search_sort_by), onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortOption.entries.forEach { option ->
                GlassFilterChip(
                    label = option.displayName,
                    selected = option == current,
                    onClick = {
                        onApply(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Single-select played-status picker for the search screen. Mirrors the Library
 * [com.raulshma.jellyplay.feature.library.components.StatusFilterSheet] using
 * search-local string resources so the labels are localized (rather than the
 * English-only [PlayedStatus.displayName]). Tapping an option applies it
 * immediately (via [onApply]) and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchStatusSheet(
    current: PlayedStatus,
    onApply: (PlayedStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    SearchSelectionSheet(title = stringResource(R.string.search_filter_status), onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayedStatus.entries.forEach { status ->
                GlassFilterChip(
                    label = status.playedStatusLabel(),
                    selected = status == current,
                    onClick = {
                        onApply(status)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Resolves a [PlayedStatus] to its search-localized display label. Kept here so
 * both the active-filter chip and the [SearchStatusSheet] share one mapping
 * (parity with how Library renders status via the enum's displayName, but using
 * translatable resources because [PlayedStatus.displayName] is English-only).
 */
@Composable
fun PlayedStatus.playedStatusLabel(): String = when (this) {
    PlayedStatus.ALL -> stringResource(R.string.search_filter_played_all)
    PlayedStatus.PLAYED -> stringResource(R.string.search_filter_played_played)
    PlayedStatus.UNPLAYED -> stringResource(R.string.search_filter_played_unplayed)
}

/**
 * Shared sheet chrome for the sort/status pickers — mirrors the
 * [SearchFilterSheet]'s TV/phone split (TvSafeSheet on Android TV,
 * ModalBottomSheet elsewhere) so the two pickers stay visually consistent with
 * the rest of the search filter UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSelectionSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isTv = LocalTvMode.current
    // Sheet container matches the app/screen background: colorScheme.surface
    // (pure #000 in OLED) rather than the old light=Low / dark=High split.
    val sheetContainerColor = MaterialTheme.colorScheme.surface

    if (isTv) {
        TvSafeSheet(onDismissRequest = onDismiss) {
            SheetBody(title = title, content = content)
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        tonalElevation = 0.dp,
        shape = ShapeCache.smoothTop28,
        dragHandle = { com.raulshma.jellyplay.core.ui.components.SheetDragHandle() },
    ) {
        SheetBody(title = title, content = content)
    }
}

@Composable
private fun SheetBody(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        content()
    }
}
