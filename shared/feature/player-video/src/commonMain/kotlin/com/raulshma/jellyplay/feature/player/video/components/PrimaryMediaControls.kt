package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.harness.harnessClickTarget
import com.raulshma.jellyplay.core.ui.player.PlayerIconButton
import com.raulshma.jellyplay.feature.player.video.PlayerSheet
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_ratio_label
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_chapters
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_episodes
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_info
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_syncplay
import org.jetbrains.compose.resources.stringResource

// ── Section split: the bottom bar's transport row ─────────────────────────
// The primary control cluster shared by the portrait (single scrolling row)
// and landscape/TV (scrolling left + fixed right) layouts. Moved verbatim
// from PlayerControls.kt as a composition-only section split (the
// VideoPlayerScreenOverlays.kt precedent): the declaration is unchanged
// except `private` → `internal` where the root file calls the symbol. See
// the section-host map on PlayerControls.kt.

/**
 * The primary media controls that appear in the bottom control bar: quality,
 * speed, audio, subtitles, chapters, episodes, SyncPlay, aspect ratio and info.
 * Extracted so the portrait (single scrolling row) and landscape/TV (scrolling
 * left + fixed right) layouts can host the same set without duplicating it.
 */
@Composable
internal fun PrimaryMediaControls(
    supportsLiveQualitySwitch: Boolean,
    streamingQuality: StreamingQuality,
    playbackSpeed: Float,
    // Pure sheet openers funnel through this (see PlayerControls' KDoc);
    // onSubtitleClick stays separate because it flags the hub's tab state.
    openSheet: (PlayerSheet) -> Unit,
    onSubtitleClick: () -> Unit,
    chapters: List<ChapterInfo>,
    hasEpisodes: Boolean,
    episodeBrowserEnabled: Boolean,
    isInSyncPlaySession: Boolean,
    currentAspectRatio: AspectRatio,
) {
    if (supportsLiveQualitySwitch) {
        PlayerQualityButton(
            quality = streamingQuality,
            onClick = { openSheet(PlayerSheet.Quality) },
        )
    }
    PlayerSpeedButton(speed = playbackSpeed, onClick = { openSheet(PlayerSheet.Speed) })
    PlayerIconButton(
        icon = Tabler.Outline.Music,
        contentDescription = stringResource(Res.string.player_video_audio),
        onClick = { openSheet(PlayerSheet.Audio) },
    )
    PlayerIconButton(
        icon = Tabler.Outline.Subtitles,
        contentDescription = stringResource(Res.string.player_video_subtitles),
        onClick = onSubtitleClick,
        // e2e: click-reach target (harness-gated no-op) — see HarnessClickBridge.
        modifier = Modifier.harnessClickTarget("player-subtitles-trigger"),
    )
    if (chapters.isNotEmpty()) {
        PlayerIconButton(
            icon = Tabler.Outline.List,
            contentDescription = stringResource(Res.string.player_video_chapters),
            onClick = { openSheet(PlayerSheet.Chapter) },
        )
    }
    if (hasEpisodes && episodeBrowserEnabled) {
        PlayerIconButton(
            icon = Tabler.Outline.ListNumbers,
            contentDescription = stringResource(Res.string.player_video_episodes),
            onClick = { openSheet(PlayerSheet.Episodes) },
        )
    }
    if (isInSyncPlaySession) {
        PlayerIconButton(
            icon = Tabler.Outline.Users,
            contentDescription = stringResource(Res.string.player_video_syncplay),
            onClick = { openSheet(PlayerSheet.SyncPlay) },
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    PlayerIconButton(
        icon = Tabler.Outline.AspectRatio,
        contentDescription = stringResource(Res.string.player_video_aspect_ratio_label),
        onClick = { openSheet(PlayerSheet.AspectRatio) },
        tint = if (currentAspectRatio != AspectRatio.FIT) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
    PlayerIconButton(
        icon = Tabler.Outline.InfoCircle,
        contentDescription = stringResource(Res.string.player_video_info),
        onClick = { openSheet(PlayerSheet.PlaybackInfo) },
    )
}
