package com.raulshma.jellyplay.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.ui.components.MiniPlayer
import com.raulshma.jellyplay.shell.UpdateCoordinator
import com.raulshma.jellyplay.update.AppUpdateSheet

/**
 * Collects [UpdateCoordinator.updateState] and shows the [AppUpdateSheet]
 * when an update flow is active. Centralized here so the launch-time
 * auto-check and any manual check (Settings) drive the same single sheet.
 */
@Composable
internal fun UpdateSheetOverlay(update: UpdateCoordinator) {
    val state by update.updateState.collectAsStateWithLifecycle()
    val autoDownloadEnabled by update.selfUpdateDownloadEnabled.collectAsStateWithLifecycle()
    if (state !is com.raulshma.jellyplay.update.UpdateState.Idle) {
        val context = LocalContext.current
        AppUpdateSheet(
            state = state,
            autoDownloadEnabled = autoDownloadEnabled,
            onAutoDownloadToggle = { enabled ->
                update.setSelfUpdateDownloadEnabled(enabled)
                // Turning it ON while an update is already available should also
                // start downloading the shown update immediately.
                val available = state as? com.raulshma.jellyplay.update.UpdateState.UpdateAvailable
                if (enabled && available != null) {
                    update.startUpdateDownload(available.info)
                }
            },
            onDownload = { info -> update.startUpdateDownload(info) },
            onInstall = { intent ->
                runCatching { context.startActivity(intent) }
            },
            onRedownload = { update.redownloadUpdate() },
            onCancel = { update.cancelDownload() },
            onDismiss = { update.dismissUpdate() },
            buildInstallIntent = { update.buildInstallIntent() },
        )
    }
}

/**
 * Single audio mini-player host for every form-factor shell (TV overlay,
 * phone NavigationRail, phone compact floating-nav). Owns the playback-flow
 * collects and the [MiniPlayer] transport wiring that all three shells used
 * to paste verbatim; [title] stays a parameter because [TvContent] hoists its
 * own title collect to feed the drawer's Now Playing row. Per-shell placement —
 * alignment, bottom padding and the compact shell's scroll-coupled offset —
 * arrives via [modifier]; the `showMiniPlayer` gate stays at the call sites.
 */
@Composable
internal fun AppMiniPlayerHost(
    audioPlaybackManager: AudioPlaybackManager,
    title: String,
    onNowPlayingClick: () -> Unit,
    onDismissMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAudioPlaying by audioPlaybackManager.isPlaying.collectAsStateWithLifecycle()
    val audioArtist by audioPlaybackManager.artist.collectAsStateWithLifecycle()
    val audioArtworkUrl by audioPlaybackManager.albumArtUrl.collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        MiniPlayer(
            isVisible = true,
            title = title,
            artist = audioArtist,
            artworkUri = audioArtworkUrl,
            isPlaying = isAudioPlaying,
            onClick = onNowPlayingClick,
            onClose = {
                audioPlaybackManager.stopAndRelease()
                onDismissMiniPlayer()
            },
            onPlayPause = {
                audioPlaybackManager.togglePlayPause()
            },
            onSkipNext = {
                audioPlaybackManager.skipToNext()
            },
        )
    }
}
