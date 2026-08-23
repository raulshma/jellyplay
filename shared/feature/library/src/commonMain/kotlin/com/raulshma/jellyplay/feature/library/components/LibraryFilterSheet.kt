package com.raulshma.jellyplay.feature.library.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayNamePlural
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.SortOption
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_apply_filters
import com.raulshma.jellyplay.feature.library.generated.resources.library_collapse
import com.raulshma.jellyplay.feature.library.generated.resources.library_expand
import com.raulshma.jellyplay.feature.library.generated.resources.library_filter_resumable
import com.raulshma.jellyplay.feature.library.generated.resources.library_filters
import com.raulshma.jellyplay.feature.library.generated.resources.library_genres
import com.raulshma.jellyplay.feature.library.generated.resources.library_media_type
import com.raulshma.jellyplay.feature.library.generated.resources.library_minimum_rating
import com.raulshma.jellyplay.feature.library.generated.resources.library_reset
import com.raulshma.jellyplay.feature.library.generated.resources.library_sort_by
import com.raulshma.jellyplay.feature.library.generated.resources.library_status
import com.raulshma.jellyplay.feature.library.generated.resources.library_tags
import com.raulshma.jellyplay.feature.library.generated.resources.library_year_range

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun LibraryFilterSheet(
    currentFilters: LibraryFilters,
    genres: List<Genre>,
    availableTags: List<String> = emptyList(),
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaTypes by remember { mutableStateOf(currentFilters.mediaTypes) }
    var selectedGenres by remember { mutableStateOf(currentFilters.genres) }
    var selectedYears by remember { mutableStateOf(currentFilters.years.toSet()) }
    var selectedSort by remember { mutableStateOf(currentFilters.sortBy) }
    var selectedPlayedStatus by remember { mutableStateOf(currentFilters.playedStatus) }
    var selectedTags by remember { mutableStateOf(currentFilters.tags.toSet()) }
    var selectedMinRating by remember { mutableFloatStateOf(currentFilters.minRating) }
    // Resumable filter mirrors LibraryFilters.isResumable: tri-state surfaced as
    // a single toggle (null/false = off, true = only items with a resume
    // position). Initialized from the persisted filter blob.
    var selectedIsResumable by remember { mutableStateOf(currentFilters.isResumable == true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLight = LocalIsLightTheme.current

    val filterContent = @Composable {
        val contentColor = MaterialTheme.colorScheme.onSurface
        val contentColorMedium = MaterialTheme.colorScheme.onSurfaceVariant
        val glassBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.library_filters),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)
                val resetInteractionSource = remember { MutableInteractionSource() }
                val isResetPressed by resetInteractionSource.collectIsPressedAsState()
                val resetScale by animateFloatAsState(
                    targetValue = if (isResetPressed) 0.95f else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "resetPressedScale"
                )
                val resetShape = ShapeCache.smooth12
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = resetScale * resetFocusState.scale
                            scaleY = resetScale * resetFocusState.scale
                        }
                        .clip(resetShape)
                        .background(glassBg)
                        .then(resetFocusState.focusModifier)
                        .tvFocusIndicator(resetFocusState, resetShape)
                        .clickable(
                            interactionSource = resetInteractionSource,
                            indication = null,
                            onClick = {
                                selectedMediaTypes = emptyList()
                                selectedGenres = emptyList()
                                selectedYears = emptySet()
                                selectedSort = SortOption.YEAR_DESC
                                selectedPlayedStatus = PlayedStatus.ALL
                                selectedTags = emptySet()
                                selectedMinRating = 0f
                                selectedIsResumable = false
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(Res.string.library_reset),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColorMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Sort By (ButtonGroup) ──
            SectionLabel(stringResource(Res.string.library_sort_by))
            ButtonGroup(
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                SortOption.entries.forEach { option ->
                    customItem({
                        ToggleButton(
                            checked = option == selectedSort,
                            onCheckedChange = {
                                if (it) selectedSort = option
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = glassBg,
                                contentColor = contentColor,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (option == selectedSort) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }) {}
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Media Type (FlowRow with expressive chips) ──
            SectionLabel(stringResource(Res.string.library_media_type))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaType.entries.filter { it != MediaType.UNKNOWN }.forEach { mediaType ->
                    GlassFilterChip(
                        label = mediaType.mediaTypeDisplayNamePlural(),
                        selected = mediaType in selectedMediaTypes,
                        onClick = {
                            selectedMediaTypes = if (mediaType in selectedMediaTypes) {
                                  selectedMediaTypes - mediaType
                            } else {
                                  selectedMediaTypes + mediaType
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Status (ButtonGroup) ──
            SectionLabel(stringResource(Res.string.library_status))
            ButtonGroup(
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PlayedStatus.entries.forEach { status ->
                    customItem({
                        ToggleButton(
                            checked = status == selectedPlayedStatus,
                            onCheckedChange = {
                                if (it) selectedPlayedStatus = status
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = glassBg,
                                contentColor = contentColor,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = status.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (status == selectedPlayedStatus) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }) {}
                }
            }

            // ── Resumable (In Progress) toggle ──
            // A single chip that restricts the grid to items with a playback
            // position (Jellyfin ItemFilter.IsResumable). Pairs naturally with
            // the "In Progress" sort but is independently composable with any
            // status / sort selection.
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassFilterChip(
                    label = stringResource(Res.string.library_filter_resumable),
                    selected = selectedIsResumable,
                    onClick = { selectedIsResumable = !selectedIsResumable },
                )
            }

            // ── Genres ──
            if (genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(stringResource(Res.string.library_genres))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.take(24).forEach { genre ->
                        GlassFilterChip(
                            label = genre.name,
                            selected = genre.name in selectedGenres,
                            onClick = {
                                selectedGenres = if (genre.name in selectedGenres) {
                                      selectedGenres - genre.name
                                } else {
                                      selectedGenres + genre.name
                                }
                            },
                        )
                    }
                }
            }

            // ── Year Range (collapsed by default) ──
            Spacer(modifier = Modifier.height(20.dp))
            CollapsibleSection(title = stringResource(Res.string.library_year_range)) {
                val presets = remember { com.raulshma.jellyplay.core.ui.components.yearRangePresets() }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassFilterChip(
                        label = "Any",
                        selected = selectedYears.isEmpty(),
                        onClick = { selectedYears = emptySet() },
                    )
                    presets.forEach { preset ->
                        val selection = com.raulshma.jellyplay.core.ui.components.yearPresetSelection(
                            preset,
                            selectedYears,
                        )
                        GlassFilterChip(
                            label = preset.label,
                            selected = selection == com.raulshma.jellyplay.core.ui.components.YearPresetSelection.Full,
                            onClick = {
                                selectedYears = com.raulshma.jellyplay.core.ui.components.toggleYearPreset(
                                      preset,
                                      selectedYears,
                                )
                            },
                        )
                    }
                }

                CustomYearRangeSelector(
                    current = selectedYears,
                    onRangeChange = { range -> selectedYears = range.toSet() },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ── Tags (collapsed by default) ──
            if (availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                CollapsibleSection(title = stringResource(Res.string.library_tags)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableTags.take(20).forEach { tag ->
                            GlassFilterChip(
                                label = tag,
                                selected = tag in selectedTags,
                                onClick = {
                                    selectedTags = if (tag in selectedTags) {
                                          selectedTags - tag
                                    } else {
                                          selectedTags + tag
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Minimum Rating (collapsed by default) ──
            Spacer(modifier = Modifier.height(20.dp))
            CollapsibleSection(title = stringResource(Res.string.library_minimum_rating)) {
                val ratingOptions = listOf(0f, 3f, 3.5f, 4f, 4.5f)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ratingOptions.forEach { rating ->
                        GlassFilterChip(
                            label = if (rating == 0f) "Any" else "${rating}+",
                            selected = selectedMinRating == rating,
                            onClick = { selectedMinRating = rating },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Apply button ──
            val applyFocusState = rememberTvFocusState(focusedScale = 1.05f)
            val applyInteractionSource = remember { MutableInteractionSource() }
            val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
            val applyScale by animateFloatAsState(
                targetValue = if (isApplyPressed) 0.95f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "applyPressedScale"
            )
            val applyShape = ShapeCache.smooth16
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = applyScale * applyFocusState.scale
                        scaleY = applyScale * applyFocusState.scale
                    }
                    .clip(applyShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .then(applyFocusState.focusModifier)
                    .tvFocusIndicator(applyFocusState, applyShape)
                    .clickable(
                        interactionSource = applyInteractionSource,
                        indication = null,
                        onClick = {
                            onApply(
                                LibraryFilters(
                                    mediaTypes = selectedMediaTypes,
                                    genres = selectedGenres,
                                    years = selectedYears.toList(),
                                    sortBy = selectedSort,
                                    playedStatus = selectedPlayedStatus,
                                    tags = selectedTags.toList(),
                                    minRating = selectedMinRating,
                                    isResumable = selectedIsResumable.takeIf { it },
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(Res.string.library_apply_filters),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    // TvSafeSheet owns both variants: the TV dialog grabs initial focus on a focusGroup content
    // node (the hand-rolled Dialog split opened with orphaned focus, so the first D-pad presses
    // did nothing or escaped the dialog), and the mobile bottom sheet keeps the drag handle +
    // inset handling this sheet used to replicate by hand.
    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.library_filters),
        sheetState = sheetState,
    ) {
        filterContent()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * A section whose header can be tapped to expand/collapse its content. Used for
 * the less-frequently-used filter sections (Year, Tags, Rating) to reduce visual
 * clutter — progressive disclosure. `startExpanded` controls the
 * initial state.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    startExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val headerFocusState = rememberTvFocusState()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(headerFocusState.focusModifier)
                .tvFocusIndicator(headerFocusState, MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Tabler.Outline.ChevronUp else Tabler.Outline.ChevronDown,
                contentDescription = stringResource(if (expanded) Res.string.library_collapse else Res.string.library_expand),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

