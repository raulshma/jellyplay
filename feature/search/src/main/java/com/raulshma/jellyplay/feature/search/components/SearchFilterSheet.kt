package com.raulshma.jellyplay.feature.search.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.feature.search.R
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayNamePlural
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.yearPresetSelection
import com.raulshma.jellyplay.core.ui.components.yearRangePresets
import com.raulshma.jellyplay.core.ui.components.YearPresetSelection
import com.raulshma.jellyplay.core.ui.components.toggleYearPreset
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.search.SearchFilters
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterSheet(
    currentFilters: SearchFilters,
    genres: List<Genre>,
    availableTags: List<String> = emptyList(),
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaTypes by remember { mutableStateOf(currentFilters.mediaTypes) }
    var selectedGenres by remember { mutableStateOf(currentFilters.genres) }
    var selectedYears by remember { mutableStateOf(currentFilters.years.toSet()) }
    var selectedTags by remember { mutableStateOf(currentFilters.tags.toSet()) }
    var selectedMinRating by remember { mutableFloatStateOf(currentFilters.minRating) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLight = LocalIsLightTheme.current
    val sheetContainerColor = if (isLight) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val glassBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val contentColorMedium = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        tonalElevation = 0.dp,
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
                    text = stringResource(R.string.search_filters_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)
                val resetInteractionSource = remember { MutableInteractionSource() }
                val isResetPressed by resetInteractionSource.collectIsPressedAsState()
                val resetScale by animateFloatAsState(
                    targetValue = if (isResetPressed) 0.95f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
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
                                selectedMediaTypes = emptyList()
                                selectedGenres = emptyList()
                                selectedYears = emptySet()
                                selectedTags = emptySet()
                                selectedMinRating = 0f
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.search_clear_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColorMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.search_filter_media_type))
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

            if (genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.search_filter_genres))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.take(20).forEach { genre ->
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

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.search_filter_year_range))
            val presets = remember { yearRangePresets() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassFilterChip(
                    label = stringResource(R.string.search_filter_any),
                    selected = selectedYears.isEmpty(),
                    onClick = { selectedYears = emptySet() },
                )
                presets.forEach { preset ->
                    val selection = yearPresetSelection(preset, selectedYears)
                    GlassFilterChip(
                        label = preset.label,
                        selected = selection == YearPresetSelection.Full,
                        onClick = {
                            selectedYears = toggleYearPreset(preset, selectedYears)
                        },
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.search_filter_tags))
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

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.search_filter_minimum_rating))
            val ratingOptions = listOf(0f, 3f, 3.5f, 4f, 4.5f)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ratingOptions.forEach { rating ->
                    GlassFilterChip(
                        label = if (rating == 0f) stringResource(R.string.search_filter_any) else stringResource(R.string.search_filter_rating_plus, rating),
                        selected = selectedMinRating == rating,
                        onClick = { selectedMinRating = rating },
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val applyFocusState = rememberTvFocusState(focusedScale = 1.05f)
            val applyInteractionSource = remember { MutableInteractionSource() }
            val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
            val applyScale by animateFloatAsState(
                targetValue = if (isApplyPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
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
                                SearchFilters(
                                    mediaTypes = selectedMediaTypes,
                                    genres = selectedGenres,
                                    years = selectedYears.toList(),
                                    tags = selectedTags.toList(),
                                    minRating = selectedMinRating,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.search_apply_filters),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
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
