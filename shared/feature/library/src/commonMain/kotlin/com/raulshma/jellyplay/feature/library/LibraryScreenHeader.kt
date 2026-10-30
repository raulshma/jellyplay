package com.raulshma.jellyplay.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryBrowserState
import com.raulshma.jellyplay.core.model.LibraryFilterDimension
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.ui.components.ActiveFiltersBar
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.core.ui.components.rememberSimpleCollectionStatus
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.feature.library.components.ErrorAwareStatusIndicator
import com.raulshma.jellyplay.feature.library.components.GlassPill
import com.raulshma.jellyplay.feature.library.components.LibraryActionChipRow
import com.raulshma.jellyplay.feature.library.components.LibraryFilterChipRow
import com.raulshma.jellyplay.feature.library.components.groupByLabel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Restore
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_action_group
import com.raulshma.jellyplay.feature.library.generated.resources.library_all
import com.raulshma.jellyplay.feature.library.generated.resources.library_item_count
import com.raulshma.jellyplay.feature.library.generated.resources.library_reset
import com.raulshma.jellyplay.feature.library.generated.resources.library_title

/**
 * This screen's active-filter bar vocabulary: which dimensions render as
 * dismiss tags, and in which order — media types → status → downloaded →
 * genres; search passes its own list. The shared [ActiveFiltersBar] renders
 * whatever it is given, so a dimension only one screen surfaces stays a caller
 * decision. Note the Status sheet's resumable toggle deliberately does NOT add
 * a tag here: an active resumable filter shows the bar with just the clear-all
 * chip.
 */
private val libraryActiveFilterDimensions = listOf(
    LibraryFilterDimension.MEDIA_TYPES,
    LibraryFilterDimension.PLAYED_STATUS,
    LibraryFilterDimension.IS_DOWNLOADED,
    LibraryFilterDimension.GENRES,
)

/**
 * The Library screen's header section host — the stacked rows above the
 * content: the title row (back button / title / status indicator / reset
 * pill), the folder pills, the pinned filter chip row, the labeled action
 * row, the active-filter tags and the item count. Owns the screen's TV header
 * focus chain (the per-row D-pad leaf routing) and every [FocusRequester] the
 * chain needs — the rest of the screen never reaches into them.
 *
 * Composition-shape only: the bodies moved verbatim out of [LibraryScreen]
 * (the deferred section-hosts design), zero behaviour change. Sheet opens
 * ride [openSheetState]; every other user intent rides the view model's
 * event channel.
 */
@Composable
internal fun LibraryHeaderSection(
    viewModel: LibraryViewModel,
    browser: LibraryBrowserState,
    folders: List<LibraryFolder>,
    isLoading: Boolean,
    error: String?,
    itemCount: Int,
    genres: List<Genre>,
    tags: List<String>,
    offlineAutoFilter: Boolean,
    hasActiveFilters: Boolean,
    onBack: (() -> Unit)?,
    openSheetState: MutableState<LibrarySheet?>,
) {
    // Offline mode pins the Downloaded filter on for the chip row's RENDER
    // only — every toggle below writes the raw `browser.filters`, matching
    // the pre-carve behaviour (the pins are display-state, not state).
    val chipRowFilters = if (offlineAutoFilter) browser.filters.copy(isDownloaded = true) else browser.filters
    val inSectionMode = browser.isSection
    val viewMode = browser.viewMode
    val groupBy = browser.groupBy

    val headerStatus = rememberSimpleCollectionStatus(
        isLoading = isLoading,
        hasError = error != null,
    )

    // ── TV header focus chain ──────────────────────────────────────────────────
    // Geometric D-pad search between the stacked header rows is unreliable: the
    // chip rows' focus bounds overlap vertically and the alphabet rail interleaves
    // with them on the right edge, so vertical hops out of each header row are
    // intercepted by that row itself and redirected to a leaf FocusRequester on
    // the neighbouring row's first chip — leaf grants always land on a real
    // focusable, unlike group-entry grants which can park focus invisibly on the
    // group node. Scoping the interception per-row (rather than at the screen
    // root with shared "which row holds focus" state) makes the routing static:
    // a row's handler only ever sees keys pressed while focus is inside that
    // row, so stale tracking can never send a hop to the wrong row.
    val showFolderRow = !inSectionMode && folders.size > 1
    val resetPillFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val firstFolderPillFocus = remember { FocusRequester() }
    val firstFilterChipFocus = remember { FocusRequester() }
    val firstActionChipFocus = remember { FocusRequester() }
    // The title row's focusable anchor: the back button in section mode, the
    // Reset pill otherwise (the title text itself is not focusable).
    val headerEntryLeaf = when {
        onBack != null -> backFocus
        !inSectionMode -> resetPillFocus
        else -> null
    }
    // First row below the title row: the folder pills when shown, else the
    // filter chip row. Null when neither exists.
    val rowBelowTitleLeaf = if (showFolderRow) firstFolderPillFocus else headerEntryLeaf?.let { firstFilterChipFocus }
    // First row above the filter chip row: the folder pills when shown, else
    // the title row. Null when neither exists.
    val rowAboveFilterLeaf = if (showFolderRow) firstFolderPillFocus else headerEntryLeaf

    val backgroundColor = MaterialTheme.colorScheme.background
    val headerGradientBrush = remember(backgroundColor) {
        Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.95f),
                backgroundColor,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerGradientBrush)
            .statusBarsPadding()
            .padding(top = 4.dp),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(
                MaterialTheme.motionScheme.defaultEffectsSpec()
            ) + slideInVertically(
                MaterialTheme.motionScheme.defaultSpatialSpec(),
                initialOffsetY = { -40 },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (onBack != null) 8.dp else 24.dp, end = 8.dp)
                    // Key-intercepted Down from the title row's focusables
                    // drops to the first header row below — the geometric
                    // search can't be trusted between these rows.
                    .ifElse(
                        headerEntryLeaf != null,
                        Modifier.onDpadKey(
                            onDown = { rowBelowTitleLeaf?.tryRequestFocus("lib_header_nav") ?: false },
                        ),
                    )
                    .openDrawerOnLeftExit(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    CircleBgBackButton(
                        onClick = onBack,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(backFocus),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = browser.title ?: stringResource(Res.string.library_title),
                    // Matches MediaDetail's DetailTopBar title treatment
                    // (titleLarge / SemiBold) rather than the old
                    // headlineLarge / Bold — keeps the library header
                    // visually consistent with the detail screen.
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ErrorAwareStatusIndicator(
                    status = headerStatus,
                    errorMessage = error,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (!inSectionMode) {
                    // Reset-all pill — matches the screen's chip/action
                    // language (glass chip, press scale, TV focus glow).
                    ExpressiveChipContainer(
                        onClick = { viewModel.onEvent(LibraryUiEvent.ResetClicked) },
                        containerColor = if (LocalIsLightTheme.current) {
                            Color.Black.copy(alpha = 0.06f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        },
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .focusRequester(resetPillFocus),
                    ) {
                        Icon(
                            Tabler.Outline.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.library_reset),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !inSectionMode && folders.size > 1,
            enter = fadeIn(
                MaterialTheme.motionScheme.defaultEffectsSpec()
            ) + slideInVertically(
                MaterialTheme.motionScheme.defaultSpatialSpec(),
                initialOffsetY = { 40 },
            ),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // Vertical D-pad hops out of this row are redirected
                    // explicitly (key interception, not focus search): the
                    // chip rows' focus bounds overlap vertically and the
                    // alphabet rail interleaves on the right edge, so the
                    // geometric search lands on the wrong row.
                    modifier = Modifier
                        .onDpadKey(
                            onUp = { headerEntryLeaf?.tryRequestFocus("lib_header_nav") ?: false },
                            onDown = { firstFilterChipFocus.tryRequestFocus("lib_header_nav") },
                        )
                        .openDrawerOnLeftExit()
                        .focusGroup()
                        .tvFocusRestorer(),
                ) {
                    item {
                        GlassPill(
                            label = stringResource(Res.string.library_all),
                            selected = browser.folder == null,
                            onClick = { viewModel.onEvent(LibraryUiEvent.SelectFolder(null)) },
                            modifier = Modifier.focusRequester(firstFolderPillFocus),
                        )
                    }
                    items(folders.size, key = { folders[it].id }, contentType = { "folder" }) { index ->
                        val folder = folders[index]
                        val placementSpec = lazyItemPlacementSpec()
                        Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                            GlassPill(
                                label = folder.name,
                                selected = browser.folder?.id == folder.id,
                                onClick = { viewModel.onEvent(LibraryUiEvent.SelectFolder(folder)) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Pinned filter chip row. Each chip opens a
        // dedicated selection sheet (immediate-apply); "All Filters"
        // falls back to the legacy full sheet.
        LibraryFilterChipRow(
            // Offline mode pins the Downloaded filter on: the
            // chip renders selected and Tags stay hidden while the
            // grid is served from the local store. The toggle is a
            // no-op then — the grid is downloads-only until the app
            // is back online, so tapping could only mislead.
            filters = chipRowFilters,
            genres = genres,
            availableTags = tags,
            onOpenSheet = { openSheetState.value = LibrarySheet.Filter(it) },
            onToggleDownloaded = {
                if (!offlineAutoFilter) {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withDownloadedToggled())
                    )
                }
            },
            onToggleHasSubtitles = {
                viewModel.onEvent(LibraryUiEvent.UpdateFilters(browser.filters.withHasSubtitlesToggled()))
            },
            firstChipFocus = firstFilterChipFocus,
            modifier = Modifier
                .onDpadKey(
                    onUp = { rowAboveFilterLeaf?.tryRequestFocus("lib_header_nav") ?: false },
                    onDown = { firstActionChipFocus.tryRequestFocus("lib_header_nav") },
                )
                .openDrawerOnLeftExit(),
        )

        // Labeled action row (View / Size / Group) — replaces the old
        // unlabeled floating toolbar. Sits directly under the filter
        // chip row so all the screen's controls are grouped and
        // discoverable, each carrying an icon + label.
        LibraryActionChipRow(
            viewMode = viewMode,
            groupByLabel = if (groupBy == GroupBy.NONE) {
                stringResource(Res.string.library_action_group)
            } else {
                groupByLabel(groupBy)
            },
            onViewCycle = {
                // Cycle GRID → THUMB → LIST → MASONRY → GRID so each
                // tap advances to the next layout mode (same order as
                // the former floating-toolbar toggle).
                viewModel.onEvent(LibraryUiEvent.SetViewMode(viewMode.next))
            },
            onSizeClick = { openSheetState.value = LibrarySheet.PosterSize },
            onGroupClick = { openSheetState.value = LibrarySheet.GroupBy },
            firstChipFocus = firstActionChipFocus,
            modifier = Modifier
                // Up returns to the filter chip row; Down falls through
                // geometrically into the grid (or the active-filter tags).
                .onDpadKey(onUp = { firstFilterChipFocus.tryRequestFocus("lib_header_nav") })
                .openDrawerOnLeftExit(),
        )

        AnimatedVisibility(
            visible = hasActiveFilters,
            enter = fadeIn(
                MaterialTheme.motionScheme.fastEffectsSpec()
            ) + expandVertically(),
            exit = fadeOut(
                MaterialTheme.motionScheme.fastEffectsSpec()
            ) + shrinkVertically(),
        ) {
            // The shared active-filter bar (core:ui) over the
            // canonical [LibraryFilters.activeTags] fold — the same
            // fold the search screen reads. No vertical D-pad
            // interception here: the tags wrap to several lines, so
            // Up/Down stays geometric between them; exiting upward
            // lands on the action row, Down falls into the grid.
            // Dismiss semantics are unchanged: each tag's clear() is
            // the exact `filters.copy(...)` the hand-rolled lambdas
            // performed, routed through the same UpdateFilters write.
            ActiveFiltersBar(
                tags = browser.filters.activeTags(libraryActiveFilterDimensions),
                onTagDismiss = { tag ->
                    viewModel.onEvent(LibraryUiEvent.UpdateFilters(tag.clear()))
                },
                onClearAll = { viewModel.onEvent(LibraryUiEvent.ClearFilters) },
            )
        }

        AnimatedVisibility(
            visible = itemCount > 0,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Text(
                text = pluralStringResource(Res.plurals.library_item_count, itemCount, itemCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 8.dp,
                ),
            )
        }
    }
}
