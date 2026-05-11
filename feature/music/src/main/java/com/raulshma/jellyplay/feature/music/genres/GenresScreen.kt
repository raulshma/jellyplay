package com.raulshma.jellyplay.feature.music.genres

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    onItemClick: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: GenresViewModel = hiltViewModel(),
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { headerVisible = true }

    val backgroundColor = Color.Black.copy(alpha = 0.95f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(500)) + slideInVertically(
                    tween(500),
                    initialOffsetY = { -40 },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "Genres",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                        status = headerStatus,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        LoadingScreen()
                    }
                    error != null -> {
                        ErrorScreen(
                            message = error!!,
                            onRetry = { viewModel.refresh() },
                        )
                    }
                    genres.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No genres found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                    else -> {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = isTvDevice()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(adaptiveInfo.gridMinSize(isTv)),
                            contentPadding = PaddingValues(
                                start = adaptiveInfo.contentPadding(isTv),
                                end = adaptiveInfo.contentPadding(isTv),
                                top = 8.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(genres, key = { it.id }, contentType = { "genre" }) { genre ->
                                GenreChip(
                                    name = genre.name,
                                    onClick = { onItemClick(genre.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
