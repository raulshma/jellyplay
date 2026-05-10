package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.image.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    val collectionDetail = viewModel.collectionDetail
    val items = viewModel.items
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val backdropVisible = remember { mutableStateOf(false) }
    val contentVisible = remember { mutableStateOf(false) }

    LaunchedEffect(collectionDetail) {
        if (collectionDetail != null) {
            backdropVisible.value = true
            contentVisible.value = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collectionDetail?.item?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                LoadingScreen(modifier = Modifier.padding(padding))
            }
            error != null -> {
                ErrorScreen(
                    message = error!!,
                    onRetry = { viewModel.loadCollection(collectionId) },
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    collectionDetail?.let { detail ->
                        AnimatedVisibility(
                            visible = backdropVisible.value,
                            enter = fadeIn(tween(600)) + slideInVertically(
                                initialOffsetY = { -it / 6 },
                                animationSpec = tween(600, easing = FastOutSlowInEasing),
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            ) {
                                MediaImage(
                                    url = viewModel.getBackdropUrl(collectionId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = 0.7f },
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = contentVisible.value,
                        enter = fadeIn(tween(400, delayMillis = 200)),
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(items, key = { _, it -> it.id }, contentType = { _, _ -> "mediaItem" }) { index, item ->
                                val itemVisible = remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { itemVisible.value = true }
                                AnimatedVisibility(
                                    visible = itemVisible.value,
                                    enter = fadeIn(
                                        animationSpec = tween(300, delayMillis = (index % 12) * 40)
                                    ) + slideInVertically(
                                        initialOffsetY = { it / 8 },
                                        animationSpec = tween(300, delayMillis = (index % 12) * 40, easing = FastOutSlowInEasing),
                                    ),
                                ) {
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
        }
    }
}
