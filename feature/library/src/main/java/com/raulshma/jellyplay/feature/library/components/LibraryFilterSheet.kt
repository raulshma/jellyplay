package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.background
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.feature.library.LibraryFilters
import com.raulshma.jellyplay.feature.library.PlayedStatus
import com.raulshma.jellyplay.feature.library.SortOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    currentFilters: LibraryFilters,
    genres: List<Genre>,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaTypes by remember { mutableStateOf(currentFilters.mediaTypes) }
    var selectedGenres by remember { mutableStateOf(currentFilters.genres) }
    var selectedYears by remember { mutableStateOf(currentFilters.years) }
    var selectedSort by remember { mutableStateOf(currentFilters.sortBy) }
    var selectedPlayedStatus by remember { mutableStateOf(currentFilters.playedStatus) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dark cinematic sheet matching the detail screen's palette
    val isLight = MaterialTheme.colorScheme.background.let { bg ->
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f
    }
    val sheetContainerColor = if (isLight) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        val contentColor = if (isLight) MaterialTheme.colorScheme.onSurface else Color.White
        val contentColorMedium = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.7f)
        val contentColorFaint = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
        val glassBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
        val glassBgSolid = if (isLight) MaterialTheme.colorScheme.surfaceContainerHighest else Color.White
        val glassBgSolidContent = if (isLight) MaterialTheme.colorScheme.onSurface else Color.Black

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)
                val resetInteractionSource = remember { MutableInteractionSource() }
                val isResetPressed by resetInteractionSource.collectIsPressedAsState()
                val resetScale by animateFloatAsState(
                    targetValue = if (isResetPressed) 0.95f else 1f,
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
                                selectedYears = emptyList()
                                selectedSort = SortOption.SORT_NAME
                                selectedPlayedStatus = PlayedStatus.ALL
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColorMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Sort By ──
            SectionLabel("Sort By")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortOption.entries.forEach { option ->
                    GlassFilterChip(
                        label = option.displayName,
                        selected = option == selectedSort,
                        onClick = { selectedSort = option },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Media Type ──
            SectionLabel("Media Type")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaType.entries.filter { it != MediaType.UNKNOWN }.forEach { mediaType ->
                    GlassFilterChip(
                        label = mediaType.displayName(),
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

            // ── Status ──
            SectionLabel("Status")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayedStatus.entries.forEach { status ->
                    GlassFilterChip(
                        label = status.displayName,
                        selected = status == selectedPlayedStatus,
                        onClick = { selectedPlayedStatus = status },
                    )
                }
            }

            // ── Genres ──
            if (genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Genres")
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

            Spacer(modifier = Modifier.height(32.dp))

            // ── Apply button ──
            val applyFocusState = rememberTvFocusState(focusedScale = 1.05f)
            val applyInteractionSource = remember { MutableInteractionSource() }
            val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
            val applyScale by animateFloatAsState(
                targetValue = if (isApplyPressed) 0.95f else 1f,
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
                                    years = selectedYears,
                                    sortBy = selectedSort,
                                    playedStatus = selectedPlayedStatus,
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Apply Filters",
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
    val isLight = MaterialTheme.colorScheme.background.let { bg ->
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * Glass filter chip matching the MediaDetailScreen genre pill style.
 * Theme-aware: adapts colors for both light and dark themes.
 */
@Composable
private fun GlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "chipPressedScale"
    )
    val scale = baseScale * focusState.scale

    val isLight = MaterialTheme.colorScheme.background.let { bg ->
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f
    }
    val bgColor = when {
        selected -> if (isLight) MaterialTheme.colorScheme.primary else Color.White
        else -> if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    }
    val textColor = when {
        selected -> if (isLight) Color.White else Color.Black
        else -> if (isLight) MaterialTheme.colorScheme.onSurface else Color.White
    }
    val checkTint = if (isLight) Color.White else Color.Black
    val chipShape = ShapeCache.smooth16

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(chipShape)
            .background(bgColor)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, chipShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = checkTint,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun MediaType.displayName(): String = when (this) {
    MediaType.MOVIE -> "Movies"
    MediaType.SERIES -> "TV Shows"
    MediaType.EPISODE -> "Episodes"
    MediaType.MUSIC -> "Music"
    MediaType.AUDIO -> "Audio"
    MediaType.ALBUM -> "Albums"
    MediaType.ARTIST -> "Artists"
    MediaType.COLLECTION -> "Collections"
    MediaType.LIVE_TV -> "Live TV"
    MediaType.CHANNEL -> "Channels"
    MediaType.UNKNOWN -> "Unknown"
}
