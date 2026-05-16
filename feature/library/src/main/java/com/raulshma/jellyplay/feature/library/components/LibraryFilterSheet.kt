package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.background
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
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
                    color = Color.White,
                )
                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smooth12)
                        .background(Color.White.copy(alpha = 0.12f))
                        .tvFocusable().clickable {
                            selectedMediaTypes = emptyList()
                            selectedGenres = emptyList()
                            selectedYears = emptyList()
                            selectedSort = SortOption.SORT_NAME
                            selectedPlayedStatus = PlayedStatus.ALL
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
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
            Button(
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = ShapeCache.smooth16,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    "Apply Filters",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
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
        color = Color.White.copy(alpha = 0.5f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * Glass filter chip matching the MediaDetailScreen genre pill style.
 * Selected = solid white surface. Unselected = translucent glass.
 */
@Composable
private fun GlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth16)
            .background(
                if (selected) Color.White
                else Color.White.copy(alpha = 0.12f)
            )
            .tvFocusable().clickable(onClick = onClick)
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
                    tint = Color.Black,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.Black else Color.White,
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
