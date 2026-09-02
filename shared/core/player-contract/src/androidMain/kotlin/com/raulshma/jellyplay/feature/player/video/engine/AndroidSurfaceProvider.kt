package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Android surface-binding half of the [MediaEngine] contract, split out of the
 * common interface because `View`/`ViewGroup` cannot cross into commonMain
 * (plan §Phase V2). Every Android engine implements this alongside
 * [MediaEngine]; the Android player surfaces reach it via
 * `engine as? AndroidSurfaceProvider` (VideoPlayerScreen, PreviewEngineHost).
 *
 * The desktop engine exposes no View surface at all — mpv embeds into the OS
 * window handle directly — which is exactly why these members are not on the
 * common contract.
 */
interface AndroidSurfaceProvider {

    /**
     * Creates the engine's native video surface (`PlayerView` / `MPVView` /
     * `VLCVideoLayout`). Callers attach it to the Compose tree via
     * `AndroidView`; the engine keeps no reference beyond what its native
     * backend needs.
     */
    fun createSurfaceView(context: Context): View

    /**
     * Optionally reparents the engine's native subtitle `View`(s) into an
     * app-supplied [host] pinned outside the pinch/crop transform (ExoPlayer's
     * zoom-safe NATIVE_PINNED strategy). Pass `null` to revert to default
     * in-frame parenting. Engines without a reparentable subtitle view no-op.
     */
    fun setExternalSubtitleHost(host: ViewGroup?) {}
}
