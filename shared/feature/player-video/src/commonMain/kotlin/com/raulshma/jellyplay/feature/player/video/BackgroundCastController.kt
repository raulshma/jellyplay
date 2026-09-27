package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * Owns the background-cast media-session swap, extracted from
 * [VideoPlayerViewModel] (the [SubtitlePreviewController] shape): handing
 * the media session from the local engine to the cast receiver when
 * playback continues headless in the background, and rebuilding the local
 * player session on return. Real cross-controller flow — the cast slice's
 * own transport lives on the VM's [CastManager] handle; this controller
 * only choreographs WHO owns the media session.
 *
 * Like every migrated controller it never references the ui state bag: the
 * engine and the current item id arrive through the narrow constructor
 * lambdas below.
 */
internal class BackgroundCastController(
    private val castManager: CastManager,
    private val mediaSessionController: MediaSessionController,
    private val getEngine: () -> MediaEngine?,
    private val getCurrentItemId: () -> String?,
) {

    /**
     * Background-cast orchestration: swaps the media-session owner between the
     * cast player and the local engine.
     */
    fun detachForBackgroundCast() {
        castManager.markBackgroundCasting(true)
        castManager.softRelease()

        // The cast receiver's player is resolved behind the androidMain seam;
        // the controller no-ops when no cast session is active.
        mediaSessionController.createForBackgroundCast("jellyplay_cast_bg")
    }

    fun reattachFromBackgroundCast() {
        if (!castManager.isBackgroundCasting) return
        castManager.markBackgroundCasting(false)

        val engine = getEngine()
        if (engine != null) {
            val itemId = getCurrentItemId() ?: return
            // The controller narrows the engine to its media3 player via
            // asMedia3Player and no-ops when the engine hosts none.
            mediaSessionController.createForPlayer(engine, "jellyplay_video_$itemId", itemId)
        }
    }
}
