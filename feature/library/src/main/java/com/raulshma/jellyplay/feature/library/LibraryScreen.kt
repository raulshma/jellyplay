package com.raulshma.jellyplay.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.image.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onItemClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders
    val mediaItems by viewModel.items
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val selectedFolder by viewModel.selectedFolder

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Library") })
        },
    ) { padding ->
        if (isLoading && mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null && mediaItems.isEmpty()) {
            ErrorScreen(
                message = error!!,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (folders.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFolder == null,
                                onClick = { viewModel.selectFolder(null) },
                                label = { Text("All") },
                            )
                        }
                        items(folders.size) { index ->
                            val folder = folders[index]
                            FilterChip(
                                selected = selectedFolder?.id == folder.id,
                                onClick = { viewModel.selectFolder(folder) },
                                label = { Text(folder.name) },
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            imageUrl = viewModel.getImageUrl(item.id),
                            onClick = { onItemClick(item.id) },
                            showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                            progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                                (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                            } else 0f,
                        )
                    }
                }
            }
        }
    }
}
