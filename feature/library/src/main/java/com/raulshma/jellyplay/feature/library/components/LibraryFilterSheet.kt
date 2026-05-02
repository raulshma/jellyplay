package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaType
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Text(
                text = "Sort By",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortOption.entries.forEach { option ->
                    FilterChip(
                        selected = option == selectedSort,
                        onClick = { selectedSort = option },
                        label = { Text(option.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Media Type",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaType.entries.filter { it != MediaType.UNKNOWN }.forEach { mediaType ->
                    FilterChip(
                        selected = mediaType in selectedMediaTypes,
                        onClick = {
                            selectedMediaTypes = if (mediaType in selectedMediaTypes) {
                                selectedMediaTypes - mediaType
                            } else {
                                selectedMediaTypes + mediaType
                            }
                        },
                        label = { Text(mediaType.displayName()) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Status",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayedStatus.entries.forEach { status ->
                    FilterChip(
                        selected = status == selectedPlayedStatus,
                        onClick = { selectedPlayedStatus = status },
                        label = { Text(status.displayName) },
                    )
                }
            }

            if (genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Genres",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.take(20).forEach { genre ->
                        FilterChip(
                            selected = genre.name in selectedGenres,
                            onClick = {
                                selectedGenres = if (genre.name in selectedGenres) {
                                    selectedGenres - genre.name
                                } else {
                                    selectedGenres + genre.name
                                }
                            },
                            label = { Text(genre.name) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    selectedMediaTypes = emptyList()
                    selectedGenres = emptyList()
                    selectedYears = emptyList()
                    selectedSort = SortOption.SORT_NAME
                    selectedPlayedStatus = PlayedStatus.ALL
                }) {
                    Text("Clear All")
                }
                Button(onClick = {
                    onApply(
                        LibraryFilters(
                            mediaTypes = selectedMediaTypes,
                            genres = selectedGenres,
                            years = selectedYears,
                            sortBy = selectedSort,
                            playedStatus = selectedPlayedStatus,
                        )
                    )
                }) {
                    Text("Apply")
                }
            }
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
