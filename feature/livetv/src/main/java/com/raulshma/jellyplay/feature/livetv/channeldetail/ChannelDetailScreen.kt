package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.feature.livetv.R

@Composable
fun ChannelDetailScreen(
    channelId: String,
    channelName: String,
    onPlayChannel: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(channelId) {
        viewModel.loadChannel(channelId, channelName)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColor = rememberScreenBackgroundColor()

    // Bespoke screen (no JellyPlayScreenScaffold) — own the remote Back path explicitly so it
    // does not depend solely on the nav-host system pop.
    BackHandler(enabled = true, onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        // Backdrop follows the currently-airing program.
        val backdropProgram = state.currentProgram
        val backdropUrl = backdropProgram?.let { viewModel.getProgramBackdropUrl(it) }
            ?: state.channelLogoUrl
        ChannelDetailBackdrop(
            backdropUrl = backdropUrl,
            blurHash = state.channelBlurHash,
            backgroundColor = backgroundColor,
            backdropHeightDp = 420.dp,
        )

        // Back button.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .focusIndicator(),
        ) {
            Icon(
                imageVector = Tabler.Outline.ArrowLeft,
                contentDescription = stringResource(android.R.string.cancel),
                tint = Color.White,
            )
        }

        when {
            state.isLoading && state.programs.isEmpty() && state.currentProgram == null -> {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(64.dp),
                )
            }
            state.error != null && state.programs.isEmpty() && state.currentProgram == null -> {
                ErrorScreen(
                    message = state.error ?: stringResource(R.string.livetv_channel_load_failed),
                    onRetry = { viewModel.loadChannel(channelId, channelName) },
                )
            }
            else -> {
                ChannelDetailContent(
                    state = state,
                    onPlayChannel = onPlayChannel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 300.dp),
                )
            }
        }
    }
}
