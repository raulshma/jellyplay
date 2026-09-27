package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.reArmPipTransport
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * Owns the video player's PiP-facing surface, extracted from
 * [VideoPlayerViewModel] (the [SubtitlePreviewController] shape): the PiP
 * transport registration (the remote-action → engine dispatch map behind the
 * window's play/pause/skip/next buttons) and the aspect-ratio / source-rect
 * pushes the PiP window needs to match the content it overlays.
 *
 * The transport action mapping is the whole decision content — the assignment
 * mechanics and the why-re-arm lifecycle rationale (Activity-scoped VM,
 * `reset()` on release, init never re-runs) stay in core:data's
 * [reArmPipTransport]; the VM re-arms through [registerPipTransport] from its
 * init block AND on every load via its `rearmTransports` session hook (the
 * load-bearing pattern documented on the VM's release path: per-item release
 * keeps the transport, the full reset clears it).
 *
 * The dispatch targets arrive as constructor lambdas so the routing stays
 * exactly where it lives on the VM: PLAY/PAUSE through the shared
 * `routedPlay` funnel (SyncPlay → cast → local, A1), the skip steps through
 * `seekByStep` (the clamped step funnel, C3), NEXT through the
 * episode-continuation controller. This class never references the ui state
 * bag — like every migrated controller, its interface is commands plus these
 * narrow seams.
 *
 * Declared delta vs the former inline VM bodies (behavior otherwise
 * verbatim): the two transport-diagnostic log lines tag as
 * `PipTransportController` instead of `VideoPlayerViewModel` — the tag
 * follows the owning file (the SyncPlayBridge precedent); the messages are
 * unchanged.
 */
internal class PipTransportController(
    private val pipController: PipController,
    private val getEngine: () -> MediaEngine?,
    /** The shared transport routing funnel (A1): SyncPlay → cast → local. */
    private val routedPlay: (play: Boolean) -> Unit,
    /** The shared clamped skip-step funnel (C3). */
    private val seekByStep: (direction: Int) -> Unit,
    /** The episode-continuation controller's next-episode choreography. */
    private val playNextEpisode: () -> Unit,
) {

    /**
     * Arms the PiP transport bridge so the Activity can dispatch PiP remote-action
     * intents (play/pause/skip/next) to the active engine. The assignment mechanics
     * and the why-re-arm lifecycle rationale (Activity-scoped VM, reset() on release,
     * init never re-runs) live in [reArmPipTransport]; this body owns only the VOD
     * action mapping. Idempotent and safe to call repeatedly (init + every load).
     */
    fun registerPipTransport() {
        reArmPipTransport(pipController) { action ->
            val engine = getEngine()
            if (engine == null) {
                // PiP bypasses the MediaSession entirely (broadcast -> PipTransport
                // -> engine), so this silently no-ops when no engine is bound.
                // Log so a stale transport is diagnosable instead of dead-buttons.
                Log.w(TAG, "PiP action $action dropped: no active player engine")
                return@reArmPipTransport
            }
            Log.d(TAG, "PiP action $action -> engine")
            when (action) {
                // PLAY/PAUSE route through the shared funnel (A1): same
                // SyncPlay -> cast -> local order as the screen's transport,
                // previously raw engine.play()/pause() that bypassed routing
                // while in a SyncPlay group or casting.
                PipAction.PLAY -> routedPlay(true)
                PipAction.PAUSE -> routedPlay(false)
                // Skip steps route through the shared funnel (C3): same clamp
                // math and same SyncPlay/cast/local routing as the screen's
                // skip buttons, previously computed inline with only a 0-floor.
                PipAction.SKIP_FORWARD -> seekByStep(+1)
                PipAction.SKIP_BACKWARD -> seekByStep(-1)
                PipAction.NEXT -> playNextEpisode()
            }
        }
    }

    /**
     * Pushes the server-reported video stream dimensions into [PipController]
     * as a `width to height` pair so the PiP window matches the content
     * (16:9, 4:3, 21:9, …) instead of always letterboxing to 16:9 (the
     * androidMain adapter maps the pair onto android.util.Rational). Falls
     * back to `null` (→ 16:9 in the Activity) when the stream or its
     * dimensions are unknown.
     */
    fun updatePipAspectRatio(streams: List<MediaStream>) {
        val video = streams.firstOrNull { it.type == StreamType.VIDEO }
        val w = video?.width
        val h = video?.height
        pipController.setPipAspectRatio(
            if (w != null && h != null && h != 0) w to h else null
        )
    }

    /**
     * Forwards the video surface's window bounds to [PipController] as the PiP
     * source-rect hint. Thin wrapper so the screen does not reach through the
     * ViewModel into the controller. (Four window-bounds ints rather than a
     * Rect — the seam crosses into platform code on desktop too.)
     */
    fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int) {
        pipController.updatePipSourceRect(left, top, right, bottom)
    }

    private companion object {
        const val TAG = "PipTransportController"
    }
}
