package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.FileDescription
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_media_info_no_info
import com.raulshma.jellyplay.feature.details.generated.resources.detail_media_info_no_info_description
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_technical_info
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MediaInfoScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: MediaInfoViewModel = koinViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.load(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColor = rememberScreenBackgroundColor()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current

    // TV focus-on-launch: focus the first info section once content arrives so D-pad input lands
    // on content, not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (uiState is MediaInfoUiState.Success) 1 else 0,
        tag = "media_info_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.detail_option_technical_info),
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        when (val state = uiState) {
            MediaInfoUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    JellyPlayLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is MediaInfoUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is MediaInfoUiState.Success -> {
                val detail = state.detail
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusGroup()
                        .focusRequester(contentFocusRequester)
                        .verticalScroll(scrollState)
                        .padding(
                            start = adaptiveInfo.contentPadding(isTv),
                            end = adaptiveInfo.contentPadding(isTv),
                            bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val currentItem = detail.item
                    Text(
                        text = currentItem.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )

                    detail.mediaSources.forEachIndexed { sourceIndex, source ->
                        MediaSourceInfoSection(
                            source = source,
                            sourceIndex = sourceIndex,
                            totalSources = detail.mediaSources.size,
                        )
                    }

                    if (detail.mediaSources.isEmpty()) {
                        EmptyMediaInfo()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaInfo() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScreenEmptyState(
            icon = Tabler.Outline.FileDescription,
            title = stringResource(Res.string.detail_media_info_no_info),
            description = stringResource(Res.string.detail_media_info_no_info_description),
        )
    }
}
