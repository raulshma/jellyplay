package com.raulshma.jellyplay.feature.subtitle.tester.preview

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.raulshma.jellyplay.feature.player.video.engine.AndroidSurfaceProvider
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * Owns the container into which the active engine's surface [View] is attached.
 * On engine switch the old surface is removed and the new one attached.
 */
class PreviewEngineHost(
    private val context: Context,
    private val onSurfaceReady: (View) -> Unit,
) {
    val container: FrameLayout = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    }

    fun attach(engine: MediaEngine) {
        detach()
        val surface = (engine as? AndroidSurfaceProvider)?.createSurfaceView(context) ?: return
        // Force MATCH_PARENT: engines whose surface view does not set its own
        // layout params (e.g. libVLC's VLCVideoLayout) would otherwise measure
        // to wrap/zero content and render smaller than the preview tile. ExoPlayer
        // and mpv set MATCH_PARENT internally, so this is a no-op for them.
        container.addView(
            surface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        onSurfaceReady(surface)
    }

    fun detach() {
        container.removeAllViews()
    }
}
