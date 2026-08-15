package com.raulshma.jellyplay.navigation.playbackhost

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * The single decision point for *which host* mounts playback for a route.
 * Pure function of `(route, preferred player)` — no `Context`, no `Intent`,
 * no composable — so the full host policy table is unit-testable on the JVM
 * (see `PlaybackHostRouterTest`, which pins every cell of the current table).
 *
 * [PlaybackHostRouter.decide] has exactly one consumer: the `navigateFilter`
 * adapter in `JellyPlayApp.kt`, which executes the decision (launch external
 * player, start PlayerActivity, or let the Navigator push normally).
 */
sealed interface HostDecision {
    /**
     * Hand off to the user's preferred external player app via the
     * ActivityResultLauncher seam in JellyPlayApp.
     */
    data class ExternalPlayer(
        val itemId: String,
        val mediaSourceId: String?,
        val startPositionTicks: Long,
    ) : HostDecision

    /**
     * Mount in the dedicated fullscreen PlayerActivity (system PiP, task
     * choreography). Carries the typed [PlayerActivityArgs] launch contract.
     */
    data class DedicatedActivity(val args: PlayerActivityArgs) : HostDecision

    /**
     * Compose inside the nav shell (audio player, Live TV).
     */
    data object InNav : HostDecision

    /**
     * Not a playback route — the Navigator should push it normally.
     */
    data object NotPlayback : HostDecision
}

object PlaybackHostRouter {

    fun decide(route: NavKey, preferredPlayer: PlayerType): HostDecision = when {
        route is Route.VideoPlayer && preferredPlayer == PlayerType.EXTERNAL ->
            HostDecision.ExternalPlayer(route.itemId, route.mediaSourceId, route.startPositionTicks)

        // Live TV quirk (deliberate, pinned by test): hands off to an external
        // app when EXTERNAL is preferred, but composes in-nav for every other
        // engine choice (see the InNav branch below).
        //
        // TODO(pip): in-nav player PiP (e.g. LiveTV) is broken since the
        //  PlayerActivity migration (55cd569f8) removed
        //  MainActivity:supportsPictureInPicture — a former enterPipMode()
        //  call now always fails, so the stubbed in-nav onEnterPip chain was
        //  deleted. Restore by answering DedicatedActivity here (and mounting
        //  live playback in PlayerActivity), not by reviving the stub.
        route is Route.LiveTvChannelPlayer && preferredPlayer == PlayerType.EXTERNAL ->
            HostDecision.ExternalPlayer(route.channelId, null, 0L)

        // Known gap (tracked follow-up, deliberately NOT handled here):
        // PlayerActivity performs no PIN/biometric check. With a lock enabled,
        // a media-notification tap opens PlayerActivity directly via its
        // class-name PendingIntent and reaches full playback without
        // challenge — MainActivity's lock gate never runs. If/when the gate
        // is enforced, it plugs in right here: refuse DedicatedActivity while
        // locked (fall back to routing through MainActivity's gate).
        route is Route.VideoPlayer ->
            HostDecision.DedicatedActivity(
                PlayerActivityArgs(
                    itemId = route.itemId,
                    mediaSourceId = route.mediaSourceId,
                    startPositionTicks = route.startPositionTicks,
                    subtitleStreamIndex = route.subtitleStreamIndex,
                    audioStreamIndex = route.audioStreamIndex,
                ),
            )

        // Audio always composes in-nav (never external). Live TV *deliberately*
        // composes in-nav for non-EXTERNAL engines: LivePlayerScreen manages
        // its own engine lifecycle (channel zapping keeps the ExoPlayer
        // instance alive across channel swaps) and gains nothing from the
        // dedicated activity today — this is the chosen host, not an omission.
        // Changing the answer for Live TV is a one-line edit here.
        route is Route.AudioPlayer || route is Route.LiveTvChannelPlayer -> HostDecision.InNav

        else -> HostDecision.NotPlayback
    }
}
