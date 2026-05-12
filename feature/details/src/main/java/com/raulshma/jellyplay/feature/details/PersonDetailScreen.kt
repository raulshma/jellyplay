package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.tv.isTvDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    personId: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            viewModel.error != null -> {
                ErrorScreen(
                    message = viewModel.error ?: "Unknown error",
                    onRetry = { viewModel.loadPerson(personId) },
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                val adaptiveInfo = LocalAdaptiveInfo.current
                val isTv = isTvDevice()
                val contentPad = adaptiveInfo.contentPadding(isTv)
                val gridMin = adaptiveInfo.gridMinSize(isTv)
                val spacing = adaptiveInfo.itemSpacing(isTv)

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(gridMin),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = padding.calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    itemsIndexed(items = viewModel.filmography, key = { _, it -> it.id }, contentType = { _, _ -> "mediaItem" }) { index, item ->
                        val visible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible.value = true }
                        AnimatedVisibility(
                            visible = visible.value,
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
                            )
                        }
                    }
                }
            }
        }
    }
}
