package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.Folders
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_empty
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_empty_subtitle
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_restore_all
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_restore_all_cd
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_restore_all_message
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_restore_all_title
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_restore_cd
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_title
import com.raulshma.jellyplay.feature.home.generated.resources.my_media_hidden_unknown

/**
 * Settings → Home → "Hidden libraries": the management screen for the
 * libraries hidden from the home My Media tiles row . Rows show the
 * library name with a per-row restore; the top bar carries Restore-all
 * behind a confirmation. Empty state when nothing is hidden. The screen is
 * reachable from TV settings, so every interactive surface is D-pad focusable
 * (the same TvGrabInitialFocus + tvFocusRestorer + tvFocusIndicator chassis
 * the other settings drill-ins use — NextUpExcludedScreen's anatomy).
 */
@Composable
fun MyMediaHiddenScreen(
    onBack: () -> Unit,
    viewModel: MyMediaHiddenViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()
    val adaptiveInfo = LocalAdaptiveInfo.current

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "my_media_hidden_init",
    )

    val scrollState = rememberLazyListState()

    var showRestoreAllDialog by remember { mutableStateOf(false) }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.my_media_hidden_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            if (state.libraries.isNotEmpty()) {
                IconButton(
                    onClick = { showRestoreAllDialog = true },
                    modifier = Modifier.focusIndicator(CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.Eye,
                        contentDescription = stringResource(Res.string.my_media_hidden_restore_all_cd),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { innerPadding ->
        CenteredBringIntoView {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .tvFocusRestorer()
                    .focusRequester(focusRequester),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                    bottom = adaptiveInfo.bottomPadding(isTv),
                ),
            ) {
                if (state.libraries.isEmpty()) {
                    item {
                        MyMediaHiddenEmptyState(
                            loading = state.loading,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(
                        count = state.libraries.size,
                        key = { index -> state.libraries[index].id },
                    ) { index ->
                        MyMediaHiddenRow(
                            library = state.libraries[index],
                            index = index,
                            count = state.libraries.size,
                            onRestore = { viewModel.restore(state.libraries[index].id) },
                        )
                    }
                }
            }
        }
    }

    if (showRestoreAllDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.my_media_hidden_restore_all_title),
            message = stringResource(Res.string.my_media_hidden_restore_all_message, state.libraries.size),
            confirmText = stringResource(Res.string.my_media_hidden_restore_all),
            onConfirm = {
                viewModel.restoreAll()
                showRestoreAllDialog = false
            },
            onDismiss = { showRestoreAllDialog = false },
        )
    }
}

/** The "nothing hidden" placeholder, shown while the hidden set is empty. */
@Composable
private fun MyMediaHiddenEmptyState(
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.my_media_hidden_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!loading) {
            Text(
                text = stringResource(Res.string.my_media_hidden_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * One hidden-library row: the library icon, the name (the id-only placeholder
 * when the folder read never matched), and the restore action. The row itself
 * is not clickable (the restore button is the only action — the hidden list
 * has no drill-in), so D-pad focus lands on the restore button and the row
 * paints its TV focus ring from the same focus state.
 */
@Composable
private fun MyMediaHiddenRow(
    library: MyMediaHiddenLibrary,
    index: Int,
    count: Int,
    onRestore: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val displayName = library.folder?.name ?: stringResource(Res.string.my_media_hidden_unknown)

    ListItem(
        headlineContent = {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        leadingContent = {
            Icon(
                Tabler.Outline.Folders,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            val restoreFocusState = rememberTvFocusState(focusedScale = 1.12f)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(ShapeCache.smooth8)
                    .then(restoreFocusState.focusModifier)
                    .tvFocusIndicator(restoreFocusState, ShapeCache.smooth8)
                    .clickable(onClick = onRestore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Eye,
                    contentDescription = stringResource(Res.string.my_media_hidden_restore_cd, displayName),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape),
    )
}
