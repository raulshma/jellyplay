package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.data.playback.NowPlayingSurface
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * The shells' audio sources ARE the shared [NowPlayingSurface] (core:data
 * commonMain — AudioPlaybackManager on Android, DesktopAudioQueueManager on
 * desktop): the former per-shell private adapters that forwarded the same
 * four StateFlow members died with the surface, this module's core:data
 * dependency (the RealtimeSessionController precedent) making the type
 * nameable here directly.
 */

/**
 * The music home cards' push lambdas as one value — the audio bundle of
 * [ShellHostHooks] (ShellHostHooks.audio), the cohesive group the music
 * home consumes. A data class so it plugs straight into the
 * [rememberShellHost] factory's structural remember-key contract; its
 * construction (via this file's helper) is remembered, so the identity is
 * stable per composition anyway.
 */
data class ShellAudioClicks(
    val onNowPlayingClick: () -> Unit,
    val onAmbientClick: () -> Unit,
)

/**
 * The ONE construction site for the shell hooks' audio bundle
 * ([ShellAudioClicks] — ShellHostHooks.audio)
 * — the remember-key discipline both shells used to hand-copy beside the
 * [rememberShellHost] factory now lives HERE, so a third shell gets the pair
 * for one adapter plus one call.
 *
 * REMEMBER-KEY DISCIPLINE (owned here, the same contract the rememberShellHost
 * factory's KDoc states): every parameter is a remember key, so the pair
 * rebuilds exactly when the navigator or the audio source's identity changes —
 * and never otherwise. Callers MUST pass an identity-stable [NowPlayingSurface]
 * (the app-scoped manager — see the interface's stability contract); a
 * fresh-per-recomposition instance compares unequal every time and would
 * rebuild both shells' section graphs on every recomposition.
 *
 * Click-time reads, deliberately NOT captured values: the lambdas read the
 * source's StateFlows when clicked ("flows read lazily, never captured
 * values"), so a track change neither goes stale (keyed on the navigator
 * alone) nor rebuilds the hooks (keyed on the item).
 *
 * DECLARED NORMALIZATION: a blank album-art URL arrives as a null
 * [Route.Ambient.imageUrl] — desktop's long-standing `.ifEmpty { null }`
 * fold, promoted to shared behavior when the pair moved here. Android used
 * to forward the raw value (an empty string); AmbientScreen renders both as
 * "no artwork" (rememberArtworkColors treats the blank as absent), so this
 * is a declared tightening, not a behavior fork.
 *
 * @param navigator remember key only — the push target for both cards; keyed
 *   so a navigator identity change refreshes the pair's captures.
 * @param audioSource the shell's [NowPlayingSurface] — its manager (see the
 *   interface's stability contract).
 */
@Composable
fun rememberShellAudioClicks(
    navigator: Navigator,
    audioSource: NowPlayingSurface,
): ShellAudioClicks = remember(navigator, audioSource) {
    ShellAudioClicks(
        onNowPlayingClick = {
            audioSource.currentPlayingItemId.value?.let { itemId ->
                navigator.navigate(Route.AudioPlayer(itemId))
            }
        },
        onAmbientClick = {
            navigator.navigate(
                Route.Ambient(
                    imageUrl = audioSource.albumArtUrl.value.ifEmpty { null },
                    title = audioSource.title.value,
                    artist = audioSource.artist.value,
                ),
            )
        },
    )
}
