package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.flow.StateFlow

/**
 * Each shell's audio core narrowed to what the music home cards' click
 * lambdas read at CLICK time. A tiny adapter (the HomePlayOnRedirect
 * idiom): the shells' managers are platform-typed (Android's
 * AudioPlaybackManager vs desktop's DesktopAudioQueueManager — final
 * classes in :shared:core:data this module cannot name), but both expose
 * these same four StateFlow members, so each shell adapts its own with a
 * private forwarder.
 *
 * Implementations MUST be identity-stable per composition (remember the
 * adapter on its manager): the adapter is a remember key of
 * [rememberShellAudioClicks], and a fresh-per-recomposition instance would
 * rebuild the pair — and through it both shells' section graphs — on every
 * recomposition (the discipline the rememberShellHost factory's KDoc
 * states, owned here for this slot).
 */
interface ShellAudioSource {
    /** The playing item's id, or null when nothing is playing. */
    val currentPlayingItemId: StateFlow<String?>

    /** The current track's album-art URL — "" when there is none. */
    val albumArtUrl: StateFlow<String>

    /** The current track's title. */
    val title: StateFlow<String>

    /** The current track's artist. */
    val artist: StateFlow<String>
}

/**
 * The music home cards' push lambdas as one value — a dumb pair (the
 * ShellHostHooks constructor-arg idiom), returned as a holder because the
 * hooks take the two callbacks as separate fields.
 */
class ShellAudioClicks(
    val onNowPlayingClick: () -> Unit,
    val onAmbientClick: () -> Unit,
)

/**
 * The ONE construction site for the shell hooks' now-playing/ambient click
 * lambdas ([ShellHostHooks.onNowPlayingClick] / [ShellHostHooks.onAmbientClick])
 * — the remember-key discipline both shells used to hand-copy beside the
 * [rememberShellHost] factory now lives HERE, so a third shell gets the pair
 * for one adapter plus one call.
 *
 * REMEMBER-KEY DISCIPLINE (owned here, the same contract the rememberShellHost
 * factory's KDoc states): every parameter is a remember key, so the pair
 * rebuilds exactly when the navigator or the audio source's identity changes —
 * and never otherwise. Callers MUST pass a remembered/stable [ShellAudioSource]
 * (remember the adapter on its manager); a fresh-per-recomposition instance
 * compares unequal every time and would rebuild both shells' section graphs
 * on every recomposition.
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
 * @param audioSource the shell's [ShellAudioSource] adapter (see the
 *   interface's stability contract).
 */
@Composable
fun rememberShellAudioClicks(
    navigator: Navigator,
    audioSource: ShellAudioSource,
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
