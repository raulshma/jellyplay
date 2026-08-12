package com.raulshma.jellyplay.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.ConfirmAction
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.feature.editor.components.ImagesTab
import com.raulshma.jellyplay.feature.editor.components.MetadataTab
import com.raulshma.jellyplay.feature.editor.components.SubtitlesTab
import kotlinx.coroutines.launch
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(itemId) {
        viewModel.loadEditorData(itemId)
    }

    // Unsaved-changes guard: when the editor is dirty, intercept system back
    // (including the TV remote Back button) and confirm before discarding.
    // `rememberSaveable` survives config changes; reset whenever a different
    // item is loaded.
    var showDiscardDialog by rememberSaveable(itemId) { mutableStateOf(false) }
    BackHandler(enabled = uiState.isDirty && !showDiscardDialog) {
        showDiscardDialog = true
    }
    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            message = stringResource(R.string.editor_discard_message),
            confirmText = stringResource(R.string.editor_discard_action),
            onConfirm = { onBack() },
            onDismiss = { showDiscardDialog = false },
            dismissText = stringResource(R.string.editor_keep_editing),
            tone = ConfirmTone.DESTRUCTIVE,
            // The third "Save" choice only makes sense when the user can still
            // save (dirty + not mid-save); otherwise this collapses to a plain
            // Discard / Keep editing pair.
            secondaryAction = if (!uiState.isSaving) {
                ConfirmAction(
                    text = stringResource(R.string.editor_save),
                    tone = ConfirmTone.PRIMARY,
                    onClick = { viewModel.saveMetadata() },
                )
            } else {
                null
            },
        )
    }

    // TV focus-on-launch: focus the tab row once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (uiState.mediaDetail == null) 0 else 1,
        tag = "editor_init",
    )

    JellyPlayScreenScaffold(
        title = uiState.mediaDetail?.item?.name ?: stringResource(R.string.editor_default_title),
        onBack = {
            // Route the toolbar back arrow through the same dirty-check as
            // system back so neither path silently discards unsaved edits.
            if (uiState.isDirty) showDiscardDialog = true else onBack()
        },
        actions = {
            val saveFocusState = rememberTvFocusState()
            FilledTonalButton(
                onClick = { viewModel.saveMetadata() },
                enabled = uiState.isAdmin && uiState.isDirty && !uiState.isSaving,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .then(saveFocusState.focusModifier)
                    .tvFocusIndicator(saveFocusState, ShapeCache.smooth12),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Icon(
                        Tabler.Outline.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.isSaving) stringResource(R.string.editor_saving) else stringResource(R.string.editor_save))
            }
        },
    ) {
        val pagerState = rememberPagerState(pageCount = { 3 })
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer()
                .focusGroup()
                .focusRequester(contentFocusRequester),
        ) {
            // Upload / save / delete / refresh failures all funnel into
            // uiState.error; render it inline + dismissible so the user sees
            // the failure instead of a silently re-enabled button. Matches the
            // ErrorBanner convention used by sibling admin screens.
            uiState.error?.let { errorMessage ->
                EditorErrorBanner(
                    message = errorMessage,
                    onDismiss = { viewModel.clearError() },
                )
            }

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.editor_tab_metadata)) },
                    icon = { Icon(Tabler.Outline.Edit, contentDescription = null) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.editor_tab_images)) },
                    icon = { Icon(Tabler.Outline.Photo, contentDescription = null) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.editor_tab_subtitles)) },
                    icon = { Icon(Tabler.Outline.Subtitles, contentDescription = null) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> MetadataTab(viewModel = viewModel)
                    1 -> ImagesTab(viewModel = viewModel)
                    2 -> SubtitlesTab(viewModel = viewModel)
                }
            }
        }
    }
}

/** Inline error banner for failed upload / save / delete / refresh operations. */
@Composable
private fun EditorErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Tabler.Outline.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_dismiss)) }
        }
    }
}
