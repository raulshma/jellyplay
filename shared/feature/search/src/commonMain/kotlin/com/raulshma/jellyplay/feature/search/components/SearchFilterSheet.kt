package com.raulshma.jellyplay.feature.search.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.search.generated.resources.Res
import com.raulshma.jellyplay.feature.search.generated.resources.search_apply_filters
import com.raulshma.jellyplay.feature.search.generated.resources.search_clear_all
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_any
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_genres
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_media_type
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_minimum_rating
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_rating_plus
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_tags
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_year_range
import com.raulshma.jellyplay.feature.search.generated.resources.search_filters_title
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayNamePlural
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.yearPresetSelection
import com.raulshma.jellyplay.core.ui.components.yearRangePresets
import com.raulshma.jellyplay.core.ui.components.YearPresetSelection
import com.raulshma.jellyplay.core.ui.components.toggleYearPreset
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.LibraryFilters
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterSheet(
    currentFilters: LibraryFilters,
    genres: List<Genre>,
    availableTags: List<String> = emptyList(),
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaTypes by remember { mutableStateOf(currentFilters.mediaTypes) }
    var selectedGenres by remember { mutableStateOf(currentFilters.genres) }
    var selectedYears by remember { mutableStateOf(currentFilters.years.toSet()) }
    var selectedTags by remember { mutableStateOf(currentFilters.tags.toSet()) }
    var selectedMinRating by remember { mutableFloatStateOf(currentFilters.minRating) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTv = LocalTvMode.current

    val isLight = LocalIsLightTheme.current
    // Sheet container matches the app/screen background: colorScheme.surface
    // (pure #000 in OLED) rather than the old light=Low / dark=High split.
    val sheetContainerColor = MaterialTheme.colorScheme.surface
    val glassBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val contentColorMedium = MaterialTheme.colorScheme.onSurfaceVariant

    val showOnTv = isTv
    if (showOnTv) {
        TvSafeSheet(onDismissRequest = onDismiss) {
            SearchFilterSheetBody(
                contentColor = contentColor,
                contentColorMedium = contentColorMedium,
                glassBg = glassBg,
                selectedMediaTypes = selectedMediaTypes,
                onSelectMediaTypes = { selectedMediaTypes = it },
                selectedGenres = selectedGenres,
                onSelectGenres = { selectedGenres = it },
                selectedYears = selectedYears,
                onSelectYears = { selectedYears = it },
                selectedTags = selectedTags,
                onSelectTags = { selectedTags = it },
                selectedMinRating = selectedMinRating,
                onSelectMinRating = { selectedMinRating = it },
                genres = genres,
                availableTags = availableTags,
                currentSortBy = currentFilters.sortBy,
                currentPlayedStatus = currentFilters.playedStatus,
                onApply = onApply,
            )
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        tonalElevation = 0.dp,
        shape = ShapeCache.smoothTop28,
        dragHandle = { com.raulshma.jellyplay.core.ui.components.SheetDragHandle() },
    ) {
        SearchFilterSheetBody(
            contentColor = contentColor,
            contentColorMedium = contentColorMedium,
            glassBg = glassBg,
            selectedMediaTypes = selectedMediaTypes,
            onSelectMediaTypes = { selectedMediaTypes = it },
            selectedGenres = selectedGenres,
            onSelectGenres = { selectedGenres = it },
            selectedYears = selectedYears,
            onSelectYears = { selectedYears = it },
            selectedTags = selectedTags,
            onSelectTags = { selectedTags = it },
            selectedMinRating = selectedMinRating,
            onSelectMinRating = { selectedMinRating = it },
            genres = genres,
            availableTags = availableTags,
            currentSortBy = currentFilters.sortBy,
            currentPlayedStatus = currentFilters.playedStatus,
            onApply = onApply,
        )
    }
}

@Composable
private fun ColumnScope.SearchFilterSheetBody(
    contentColor: Color,
    contentColorMedium: Color,
    glassBg: Color,
    selectedMediaTypes: List<MediaType>,
    onSelectMediaTypes: (List<MediaType>) -> Unit,
    selectedGenres: List<String>,
    onSelectGenres: (List<String>) -> Unit,
    selectedYears: Set<Int>,
    onSelectYears: (Set<Int>) -> Unit,
    selectedTags: Set<String>,
    onSelectTags: (Set<String>) -> Unit,
    selectedMinRating: Float,
    onSelectMinRating: (Float) -> Unit,
    genres: List<Genre>,
    availableTags: List<String>,
    currentSortBy: SortOption,
    currentPlayedStatus: PlayedStatus,
    onApply: (LibraryFilters) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.search_filters_title),
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
                    label = "searchResetPressedScale",
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
                                onSelectMediaTypes(emptyList())
                                onSelectGenres(emptyList())
                                onSelectYears(emptySet())
                                onSelectTags(emptySet())
                                onSelectMinRating(0f)
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(Res.string.search_clear_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColorMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(stringResource(Res.string.search_filter_media_type))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaType.entries.filter { it != MediaType.UNKNOWN }.forEach { mediaType ->
                    GlassFilterChip(
                        label = mediaType.mediaTypeDisplayNamePlural(),
                        selected = mediaType in selectedMediaTypes,
                        onClick = {
                            onSelectMediaTypes(if (mediaType in selectedMediaTypes) {
                                selectedMediaTypes - mediaType
                            } else {
                                selectedMediaTypes + mediaType
                            })
                        },
                    )
                }
            }

            if (genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(stringResource(Res.string.search_filter_genres))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.take(20).forEach { genre ->
                        GlassFilterChip(
                            label = genre.name,
                            selected = genre.name in selectedGenres,
                            onClick = {
                                onSelectGenres(if (genre.name in selectedGenres) {
                                    selectedGenres - genre.name
                                } else {
                                    selectedGenres + genre.name
                                })
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(Res.string.search_filter_year_range))
            val presets = remember { yearRangePresets() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassFilterChip(
                    label = stringResource(Res.string.search_filter_any),
                    selected = selectedYears.isEmpty(),
                    onClick = { onSelectYears(emptySet()) },
                )
                presets.forEach { preset ->
                    val selection = yearPresetSelection(preset, selectedYears)
                    GlassFilterChip(
                        label = preset.label,
                        selected = selection == YearPresetSelection.Full,
                        onClick = {
                            onSelectYears(toggleYearPreset(preset, selectedYears))
                        },
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(stringResource(Res.string.search_filter_tags))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableTags.take(20).forEach { tag ->
                        GlassFilterChip(
                            label = tag,
                            selected = tag in selectedTags,
                            onClick = {
                                onSelectTags(if (tag in selectedTags) {
                                    selectedTags - tag
                                } else {
                                    selectedTags + tag
                                })
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(Res.string.search_filter_minimum_rating))
            val ratingOptions = listOf(0f, 3f, 3.5f, 4f, 4.5f)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ratingOptions.forEach { rating ->
                    GlassFilterChip(
                        label = if (rating == 0f) stringResource(Res.string.search_filter_any) else stringResource(Res.string.search_filter_rating_plus, rating),
                        selected = selectedMinRating == rating,
                        onClick = { onSelectMinRating(rating) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val applyFocusState = rememberTvFocusState(focusedScale = 1.05f)
            val applyInteractionSource = remember { MutableInteractionSource() }
            val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
            val applyScale by animateFloatAsState(
                targetValue = if (isApplyPressed) 0.95f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "searchApplyPressedScale",
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
                                    tags = selectedTags.toList(),
                                    minRating = selectedMinRating,
                                    // Preserve the sort/played-status chosen via the
                                    // dedicated chips so applying this multi-dimension
                                    // sheet doesn't silently reset them to defaults.
                                    sortBy = currentSortBy,
                                    playedStatus = currentPlayedStatus,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.search_apply_filters),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
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
