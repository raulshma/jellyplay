package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import androidx.compose.foundation.lazy.grid.GridCells

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = (state as? PersonDetailUiState.Success)?.name ?: ""
    val isLoading = state is PersonDetailUiState.Loading

    JellyPlayScreenScaffold(
        title = title,
        onBack = onBack,
        topBarStyle = TopBarStyle.Collapsing,
        actions = {
            if (isLoading) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp)
                )
            }
        },
    ) { padding ->
        when (state) {
            is PersonDetailUiState.Error -> {
                val message = (state as PersonDetailUiState.Error).message
                ErrorScreen(
                    message = message,
                    onRetry = { viewModel.loadPerson(personId) },
                    modifier = Modifier.padding(padding),
                )
            }
            PersonDetailUiState.Loading -> {
                DelayedLoadingScreen(modifier = Modifier.padding(padding))
            }
            is PersonDetailUiState.Success -> {
                val filmography = (state as PersonDetailUiState.Success).filmography
                val adaptiveInfo = LocalAdaptiveInfo.current
                val isTv = LocalTvMode.current
                val contentPad = adaptiveInfo.contentPadding(isTv)
                val gridMin = adaptiveInfo.gridMinSize(isTv)
                val spacing = adaptiveInfo.itemSpacing(isTv)

                TvFocusableGrid(
                    itemCount = filmography.size,
                    key = { filmography[it].id },
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
                    contentType = { "mediaItem" },
                ) { index, itemModifier ->
                    val item = filmography[index]
                    val visible = remember { mutableStateOf(false) }
                    val clickHandler = remember(item.id) { { onItemClick(item.id) } }
                    LaunchedEffect(Unit) { visible.value = true }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                        ) + slideInVertically(
                            initialOffsetY = { it / 8 },
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        ),
                    ) {
                        PosterCard(
                            item = item,
                            imageUrl = viewModel.getImageUrl(item.id),
                            onClick = clickHandler,
                            modifier = itemModifier,
                        )
                    }
                }
            }
        }
    }
}
