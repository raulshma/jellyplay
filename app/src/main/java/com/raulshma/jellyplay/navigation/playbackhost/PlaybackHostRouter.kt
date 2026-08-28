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
     * Compose inside the nav shell (audio player).
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
        // app when EXTERNAL is preferred; every other engine choice mounts in
        // the dedicated activity below (wave 19C — live PiP restored by
        // hosting live playback in PlayerActivity, whose full PiP apparatus
        // MainActivity can't offer since 55cd569f8 removed its
        // supportsPictureInPicture).
        route is Route.LiveTvChannelPlayer && preferredPlayer == PlayerType.EXTERNAL ->
            HostDecision.ExternalPlayer(route.channelId, null, 0L)

        // Known gap (deliberately NOT handled here — decision item recorded
        // in docs/architecture/plans/10-playback-host-seam.md, "PIN-lock"):
        // PlayerActivity performs no PIN/biometric check. With a lock enabled,
        // a media-notification tap opens PlayerActivity directly via its
        // class-name PendingIntent and reaches full playback without
        // challenge — MainActivity's lock gate never runs. If/when the gate
        // is enforced, it plugs in right here: refuse DedicatedActivity while
        // locked (fall back to routing through MainActivity's gate).
        route is Route.VideoPlayer ->
            HostDecision.DedicatedActivity(
                PlayerActivityArgs.Video(
                    itemId = route.itemId,
                    mediaSourceId = route.mediaSourceId,
                    startPositionTicks = route.startPositionTicks,
                    subtitleStreamIndex = route.subtitleStreamIndex,
                    audioStreamIndex = route.audioStreamIndex,
                ),
            )

        // Wave 19C (live PiP): Live TV moved out of the nav shell — the
        // dedicated PlayerActivity hosts LivePlayerScreen, whose PiP apparatus
        // (auto-enter on home, remote actions, aspect-shaped window) serves
        // live through the live VM's PipController seam. LivePlayerScreen
        // keeps its engine lifecycle across channel zaps inside that host
        // (the reused engine survives a zap; a route swap re-tunes via
        // PlayerActivity.onNewIntent).
        route is Route.LiveTvChannelPlayer ->
            HostDecision.DedicatedActivity(
                PlayerActivityArgs.Live(
                    channelId = route.channelId,
                    channelName = route.channelName,
                    subtitleStreamIndex = route.subtitleStreamIndex,
                    audioStreamIndex = route.audioStreamIndex,
                ),
            )

        // Audio always composes in-nav (never external, never the dedicated
        // activity — the audio host owns its own mini-player/background flow).
        route is Route.AudioPlayer -> HostDecision.InNav

        else -> HostDecision.NotPlayback
    }
}
