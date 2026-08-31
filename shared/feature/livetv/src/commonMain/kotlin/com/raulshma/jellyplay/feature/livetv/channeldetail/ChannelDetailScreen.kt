package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_action_cancel
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_channel_load_failed
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_canceled
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_success
import com.raulshma.jellyplay.feature.livetv.rememberLiveTvMessenger

@Composable
fun ChannelDetailScreen(
    channelId: String,
    channelName: String,
    onPlayChannel: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(channelId) {
        viewModel.loadChannel(channelId, channelName)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColorState = rememberScreenBackgroundColorState()

    // One-shot record/cancel feedback (screen-forward seam): resolve the texts
    // here, forward each emitted message through the messenger actual.
    val messenger = rememberLiveTvMessenger()
    val successText = stringResource(Res.string.livetv_record_success)
    val canceledText = stringResource(Res.string.livetv_record_canceled)
    LaunchedEffect(messenger) {
        viewModel.messages.collect { message ->
            when (message) {
                LiveTvUserMessage.RecordSuccess -> messenger?.info(successText)
                LiveTvUserMessage.RecordCanceled -> messenger?.info(canceledText)
                is LiveTvUserMessage.Raw -> messenger?.error(message.text)
            }
        }
    }

    // Bespoke screen (no JellyPlayScreenScaffold) — own the remote Back path explicitly so it
    // does not depend solely on the nav-host system pop.
    JellyPlayBackHandler(enabled = true, onBack = onBack)

    // While loading, the raw spinner leaves nothing focusable — hold focus on the back
    // button so it doesn't fall to the drawer rail. Once content composes,
    // ChannelDetailContent's own grab takes over.
    val isLoading = state.isLoading && state.programs.isEmpty() && state.currentProgram == null
    val backFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(
        focusRequester = if (isLoading) backFocusRequester else null,
        debugKey = "channel_detail_back",
    )

    Box(Modifier.fillMaxSize()) {
        // Backdrop follows the currently-airing program.
        val backdropProgram = state.currentProgram
        val backdropUrl = backdropProgram?.let { viewModel.getProgramBackdropUrl(it) }
            ?: state.channelLogoUrl
        ChannelDetailBackdrop(
            backdropUrl = backdropUrl,
            blurHash = state.channelBlurHash,
            backgroundColorState = backgroundColorState,
            backdropHeightDp = 420.dp,
        )

        // Back button — inset for the status bar so it stays clear of the
        // notch/gesture area in portrait and the cutout in landscape.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .focusRequester(backFocusRequester)
                .focusIndicator(),
        ) {
            Icon(
                imageVector = Tabler.Outline.ArrowLeft,
                // contentDescription "Cancel": the legacy screen borrowed the
                // platform android.R string; compose-resources has no android.R
                // — livetv_action_cancel carries the same text in every locale.
                contentDescription = stringResource(Res.string.livetv_action_cancel),
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
                    message = state.error ?: stringResource(Res.string.livetv_channel_load_failed),
                    onRetry = { viewModel.loadChannel(channelId, channelName) },
                )
            }
            else -> {
                ChannelDetailContent(
                    state = state,
                    onPlayChannel = onPlayChannel,
                    onRecord = { program -> viewModel.recordProgram(program) },
                    onRecordSeries = { program -> viewModel.recordSeries(program) },
                    onCancelTimer = { program -> viewModel.cancelTimer(program) },
                    onCancelSeries = { program -> viewModel.cancelSeries(program) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 300.dp)
                        // Keep the program list clear of the gesture nav bar and
                        // of landscape display cutouts. Status bar is already
                        // respected at the top via the top padding above.
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                        ),
                )
            }
        }
    }
}
