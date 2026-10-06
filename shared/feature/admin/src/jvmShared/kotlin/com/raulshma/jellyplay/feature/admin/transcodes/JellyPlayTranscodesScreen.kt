package com.raulshma.jellyplay.feature.admin.transcodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerStop
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Transform
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_refresh
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown_error
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_cancel_body
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_cancel_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_empty
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_paused
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_play_direct
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_play_transcode
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_unavailable_body
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_unavailable_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_unknown

/**
 * The admin transcodes monitor (Route.JellyPlayTranscodes): the companion
 * plugin's active transcodes with live-ish refresh + cancel. Admin access is
 * enforced by the wrapping AdminRouteContainer; the plugin gate lives in the
 * [JellyPlayTranscodesViewModel], so the screen renders three states —
 * loading (gate unresolved), gated-off (plugin/feature absent), and the list.
 *
 * Polling rides screen visibility: START starts the ViewModel's 5s loop, stop
 * /dispose stops it (the settings screen's session-auto-refresh pattern).
 */
@Composable
fun JellyPlayTranscodesScreen(
    onBack: () -> Unit,
    viewModel: JellyPlayTranscodesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    LifecycleStartEffect(Unit) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    // Cancel-confirmation dialog. The pending row is held in the VM so it
    // survives recomposition; dismissed on confirm/cancel/away-tap.
    viewModel.cancelConfirmation.item?.let { row ->
        val user = row.userName.ifBlank { stringResource(Res.string.jellyplay_tr_unknown) }
        val item = row.itemName.ifBlank { stringResource(Res.string.jellyplay_tr_unknown) }
        val device = row.deviceName.ifBlank { stringResource(Res.string.jellyplay_tr_unknown) }
        ConfirmDialog(
            title = stringResource(Res.string.jellyplay_tr_cancel_title),
            message = stringResource(Res.string.jellyplay_tr_cancel_body, user, item, device),
            confirmText = stringResource(Res.string.jellyplay_tr_cancel),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            confirmLoading = state.isCancelling,
            onConfirm = { viewModel.cancelTranscode() },
            onDismiss = { viewModel.dismissCancelDialog() },
        )
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.jellyplay_tr_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .focusIndicator(CircleShape)
                    .clickable(onClick = { viewModel.refresh() }),
            ) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = stringResource(Res.string.admin_refresh),
                    modifier = Modifier.padding(12.dp).size(20.dp),
                )
            }
        },
    ) {
        when {
            state.isLoading -> {
                ScreenLoadingState(modifier = Modifier.fillMaxSize())
            }
            state.gate == TranscodesGate.Unavailable -> {
                GatedOffState(modifier = Modifier.fillMaxSize())
            }
            state.error != null -> {
                ErrorScreen(
                    message = state.error ?: stringResource(Res.string.admin_unknown_error),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.rows.isEmpty() -> {
                EmptyTranscodesState(modifier = Modifier.fillMaxSize())
            }
            else -> {
                TranscodesList(
                    state = state,
                    contentPadding = PaddingValues(
                        start = adaptiveInfo.contentPadding(false),
                        end = adaptiveInfo.contentPadding(false),
                        top = 8.dp,
                        bottom = adaptiveInfo.bottomPadding(isTv),
                    ),
                    onCancel = viewModel::showCancelDialog,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TranscodesList(
    state: TranscodesState,
    contentPadding: PaddingValues,
    onCancel: (TranscodeRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.focusGroup(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.rows, key = { it.sessionId }) { row ->
            TranscodeRowCard(row = row, onCancel = onCancel)
        }
    }
}

@Composable
private fun TranscodeRowCard(
    row: TranscodeRow,
    onCancel: (TranscodeRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(Res.string.jellyplay_tr_unknown)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.itemName.ifBlank { unknown },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.isPaused) {
                        Spacer(Modifier.width(8.dp))
                        PausedBadge()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${row.userName.ifBlank { unknown }} · ${row.deviceName.ifBlank { unknown }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                TranscodeMetaRow(row = row)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .focusIndicator(CircleShape)
                    .clickable { onCancel(row) },
            ) {
                Icon(
                    Tabler.Outline.PlayerStop,
                    contentDescription = stringResource(Res.string.jellyplay_tr_cancel),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TranscodeMetaRow(row: TranscodeRow) {
    val parts = buildList {
        add(
            when (row.playMethod) {
                TranscodePlayMethod.DIRECT -> stringResource(Res.string.jellyplay_tr_play_direct)
                TranscodePlayMethod.TRANSCODE -> stringResource(Res.string.jellyplay_tr_play_transcode)
                TranscodePlayMethod.OTHER -> row.playMethodRaw
                    ?: stringResource(Res.string.jellyplay_tr_unknown)
            },
        )
        // Codecs are only meaningful while the server is re-encoding — a
        // direct play has nothing to show for them.
        if (row.isTranscoding) {
            listOfNotNull(row.videoCodec, row.audioCodec)
                .takeIf { it.isNotEmpty() }
                ?.let { codecs -> add(codecs.joinToString(" / ")) }
            if (row.transcodeReasons.isNotEmpty()) {
                add(row.transcodeReasons.transcodeReasonsLabel())
            }
        }
        row.bitrateLabel?.let { add(it) }
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The paused chip: tinted pill so a held stream reads at a glance. */
@Composable
private fun PausedBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.jellyplay_tr_paused),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The gated-off state: plugin absent or the `transcodes` feature key missing. */
@Composable
private fun GatedOffState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Tabler.Outline.Transform,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.jellyplay_tr_unavailable_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.jellyplay_tr_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTranscodesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Tabler.Outline.Transform,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.jellyplay_tr_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
