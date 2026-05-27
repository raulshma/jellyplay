package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
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
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(collectionDetail?.item?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
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
                            enter = fadeIn(tween(600, easing = AlphaEasing)) + slideInVertically(
                                initialOffsetY = { -it / 6 },
                                animationSpec = tween(600, easing = FancyTransitionEasing),
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
                        enter = fadeIn(tween(400, delayMillis = 200, easing = AlphaEasing)),
                    ) {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = LocalTvMode.current
                        val contentPad = adaptiveInfo.contentPadding(isTv)
                        val gridMin = adaptiveInfo.gridMinSize(isTv)
                        val spacing = adaptiveInfo.itemSpacing(isTv)

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(gridMin),
                            contentPadding = PaddingValues(
                                horizontal = contentPad,
                                vertical = 8.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(items, key = { _, it -> it.id }, contentType = { _, _ -> "mediaItem" }) { index, item ->
                                val itemVisible = remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { itemVisible.value = true }
                                AnimatedVisibility(
                                    visible = itemVisible.value,
                                    enter = fadeIn(
                                        animationSpec = tween(300, delayMillis = (index % 12) * 40, easing = AlphaEasing)
                                    ) + slideInVertically(
                                        initialOffsetY = { it / 8 },
                                        animationSpec = tween(300, delayMillis = (index % 12) * 40, easing = FancyTransitionEasing),
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
                                        sharedElementKey = "poster_${item.id}",
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
