package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.feature.newsletter.R
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Mail

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewsletterScreen(
    onBack: () -> Unit,
    onItemClick: (MediaItem) -> Unit = {},
    onPlayClick: (String, String?, Long) -> Unit = { _, _, _ -> },
    onViewAllFreshPicks: () -> Unit = {},
    viewModel: NewsletterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColor = rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Newsletter",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(NewsletterUiEvent.PullToRefresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val hasAnyData = state.recentlyAdded.isNotEmpty() ||
                state.activityDigest.isNotEmpty() ||
                state.libraryStats != null ||
                state.continueWatching.isNotEmpty() ||
                state.nextUp.isNotEmpty() ||
                state.curatedPicks.isNotEmpty()

            when {
                state.isLoading && !hasAnyData -> {
                    ScreenLoadingState(
                        message = stringResource(R.string.newsletter_preparing_digest),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.error != null && !hasAnyData -> {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Mail,
                        title = stringResource(R.string.newsletter_could_not_load),
                        description = state.error,
                        actionLabel = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_retry),
                        onAction = { viewModel.onEvent(NewsletterUiEvent.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    NewsletterContent(
                        state = state,
                        viewModel = viewModel,
                        onItemClick = onItemClick,
                        onPlayClick = onPlayClick,
                        onViewAllFreshPicks = onViewAllFreshPicks,
                    )
                }
            }
        }
    }
}
