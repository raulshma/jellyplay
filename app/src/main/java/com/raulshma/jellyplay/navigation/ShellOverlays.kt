package com.raulshma.jellyplay.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.data.playback.ShellAudioSurface
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.WhatsNewTargets
import com.raulshma.jellyplay.shell.AppUpdateCoordinator
import com.raulshma.jellyplay.shell.ShellSurface
import com.raulshma.jellyplay.shell.WhatsNewCoordinator
import com.raulshma.jellyplay.update.UpdateState
import com.raulshma.jellyplay.whatsnew.WhatsNewSheet
import com.raulshma.jellyplay.whatsnew.WhatsNewState

/**
 * Collects [WhatsNewCoordinator.state] and shows the [WhatsNewSheet] while a
 * release presentation is active — but only on the shell's MAIN surface
 * ([ShellSurface.Main], the same gate the self-update sheet applies via
 * AppUpdateDecision.shouldShowOverlay on the store line; compared directly
 * here because that decision object is store-flavor-only while this file is
 * src/main), and only once the update sheet is Idle: an available self-update
 * outranks the What's New presentation, so the two root overlays can never
 * stack. The coordinator itself stays oblivious to that ordering; this is
 * its single render site. Reads the flavor-bound
 * [AppUpdateCoordinator]'s [AppUpdateCoordinator.updateState] only — the
 * fdroid noop is permanently Idle, so the What's New sheet is never gated by
 * it there.
 *
 * Why the surface gate (the formerly implicit part): [surface] is derived at
 * the JellyPlayApp call site by the pure `shellSurface` fold, and without
 * this gate a returning SIGNED-OUT user with an unseen upgrade got the sheet
 * popped over the signed-out auth host — WhatsNewDecision.launchPhase keys
 * on seen-version + onboarding-completed, not the session phase, so
 * SHOW_IF_CONTENT fires regardless of where the shell is. Mid-onboarding was
 * already protected, but only incidentally: a fresh install stamps silently
 * (seen == null && !onboardingCompleted → STAMP_SILENTLY) — a policy-input
 * coincidence, not a composition guarantee, which this gate now makes
 * structural. Gating has no side effect: the gated sheet is never composed,
 * so its dismiss path (which stamps the version seen) can't fire, and the
 * presentation survives to main.
 */
@Composable
internal fun WhatsNewSheetOverlay(
    whatsNew: WhatsNewCoordinator,
    update: AppUpdateCoordinator,
    surface: ShellSurface,
    onNavigate: (Route) -> Unit,
) {
    val state by whatsNew.state.collectAsStateWithLifecycle()
    val updateState by update.updateState.collectAsStateWithLifecycle()
    val presentation = state as? WhatsNewState.Show
    if (presentation != null && surface == ShellSurface.Main && updateState is UpdateState.Idle) {
        WhatsNewSheet(
            release = presentation.release,
            onNavigateToEntry = { entry ->
                // Dismiss FIRST so the deep link also consumes the prompt —
                // the coordinator stamps the version seen on every exit path.
                whatsNew.dismiss()
                WhatsNewTargets
                    .resolve(entry.target, entry.highlightSettingId)
                    ?.let(onNavigate)
            },
            onDismiss = { whatsNew.dismiss() },
        )
    }
}

/**
 * Single audio mini-player host for every form-factor shell (TV overlay,
 * phone NavigationRail, phone compact floating-nav). Owns the playback-flow
 * collects and the [MiniPlayer] transport wiring shared by all three shells;
 * [title] stays a parameter because [TvContent] hoists its
 * own title collect to feed the drawer's Now Playing row. Per-shell placement —
 * alignment, bottom padding and the compact shell's scroll-coupled offset —
 * arrives via [modifier]; the `showMiniPlayer` gate stays at the call sites.
 */
@Composable
internal fun AppMiniPlayerHost(
    audioPlaybackManager: ShellAudioSurface,
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
