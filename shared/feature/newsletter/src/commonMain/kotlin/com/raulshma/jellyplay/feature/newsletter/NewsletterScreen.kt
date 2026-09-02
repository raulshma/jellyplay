package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_retry
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.newsletter.generated.resources.Res
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_could_not_load
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_preparing_digest
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_admin_only
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_confirm_body
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_confirm_title
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_now
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_test
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_title
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Mail
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewsletterScreen(
    onBack: () -> Unit,
    onItemClick: (MediaItem) -> Unit = {},
    onPlayClick: (String, String?, Long) -> Unit = { _, _, _ -> },
    onViewAllFreshPicks: () -> Unit = {},
    viewModel: NewsletterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColorState = rememberScreenBackgroundColorState()

    // TV focus-on-launch: focus the first newsletter section once data arrives so D-pad input
    // lands on content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    val hasAnyData = state.recentlyAdded.isNotEmpty() ||
        state.activityDigest.isNotEmpty() ||
        state.libraryStats != null ||
        state.continueWatching.isNotEmpty() ||
        state.nextUp.isNotEmpty() ||
        state.curatedPicks.isNotEmpty()
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading && !hasAnyData) 0 else 1,
        tag = "newsletter_init",
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val sendResultText = state.sendResult?.asText()
    LaunchedEffect(sendResultText) {
        if (sendResultText != null) {
            snackbarHostState.showSnackbar(sendResultText)
            viewModel.onEvent(NewsletterUiEvent.DismissSendResult)
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.newsletter_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            if (state.isAdmin) {
                NewsletterAdminActions(viewModel = viewModel, isSending = state.isSending)
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.onEvent(NewsletterUiEvent.PullToRefresh) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                when {
                    state.isLoading && !hasAnyData -> {
                        ScreenLoadingState(
                            message = stringResource(Res.string.newsletter_preparing_digest),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    state.error != null && !hasAnyData -> {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Mail,
                            title = stringResource(Res.string.newsletter_could_not_load),
                            description = state.error,
                            actionLabel = stringResource(CoreUiRes.string.core_retry),
                            onAction = { viewModel.onEvent(NewsletterUiEvent.Refresh) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.error != null) {
                                AppendErrorFooter(
                                    message = state.error!!,
                                    onRetry = { viewModel.onEvent(NewsletterUiEvent.Refresh) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                )
                            }
                            NewsletterContent(
                                state = state,
                                viewModel = viewModel,
                                onItemClick = onItemClick,
                                onPlayClick = onPlayClick,
                                onViewAllFreshPicks = onViewAllFreshPicks,
                                listFocusRequester = listFocusRequester,
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(paddingValues),
            ) { data -> Snackbar(snackbarData = data) }
        }
    }

    state.pendingSendAction?.let { action ->
        val isSendNow = action == NewsletterSendAction.SEND_NOW
        ConfirmDialog(
            title = stringResource(Res.string.newsletter_send_confirm_title),
            message = stringResource(Res.string.newsletter_send_confirm_body),
            confirmText = stringResource(
                if (isSendNow) Res.string.newsletter_send_now else Res.string.newsletter_send_test
            ),
            dismissText = stringResource(CoreUiRes.string.core_cancel),
            onConfirm = { viewModel.onEvent(NewsletterUiEvent.ConfirmSend) },
            onDismiss = { viewModel.onEvent(NewsletterUiEvent.DismissSendDialog) },
            tone = ConfirmTone.PRIMARY,
        )
    }
}

@Composable
private fun NewsletterAdminActions(
    viewModel: NewsletterViewModel,
    isSending: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }, enabled = !isSending) {
            Icon(
                imageVector = Tabler.Outline.DotsVertical,
                contentDescription = stringResource(Res.string.newsletter_send_admin_only),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.newsletter_send_now)) },
                onClick = {
                    menuExpanded = false
                    viewModel.onEvent(NewsletterUiEvent.SendNow)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.newsletter_send_test)) },
                onClick = {
                    menuExpanded = false
                    viewModel.onEvent(NewsletterUiEvent.SendTest)
                },
            )
        }
    }
}
