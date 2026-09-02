package com.raulshma.jellyplay.feature.player.video

import android.graphics.Rect
import android.util.Rational
import com.raulshma.jellyplay.core.data.playback.PipAction as LegacyPipAction
import com.raulshma.jellyplay.core.data.playback.PipController as LegacyPipController
import com.raulshma.jellyplay.core.data.playback.PipTransport as LegacyPipTransport
import kotlinx.coroutines.flow.StateFlow

/**
 * Android adapter over the Hilt-owned legacy `core:data` PipController
 * singleton (wave 8C seam): the same instance the app's PlayerActivity
 * injects, so VM writes and Activity reads observe one state. Maps the
 * commonMain [PipTransport] wrapper onto the legacy fun-interface and
 * `(width, height)` aspect pairs onto [Rational]; the source-rect hint is
 * rebuilt from the four ints the common interface carries (the legacy
 * `android.graphics.Rect` must not leak into common code).
 */
internal class AndroidPipController(
    private val delegate: LegacyPipController,
) : PipController {

    override val isInPipMode: StateFlow<Boolean> get() = delegate.isInPipMode

    override val pipDismissed: StateFlow<Boolean> get() = delegate.pipDismissed

    override var pipTransport: PipTransport?
        get() = delegate.pipTransport?.let { legacy ->
            PipTransport { action -> legacy.handle(action.toLegacy()) }
        }
        set(value) {
            delegate.pipTransport = value?.let { common ->
                LegacyPipTransport { action -> common.handle(action.toCommon()) }
            }
        }

    override var pipHasNext: Boolean
        get() = delegate.pipHasNext
        set(value) { delegate.pipHasNext = value }

    override fun setPlaying(playing: Boolean) = delegate.setPlaying(playing)

    override fun setControlsLocked(locked: Boolean) = delegate.setControlsLocked(locked)

    override fun requestAutoEnterPip(shouldEnter: Boolean) = delegate.requestAutoEnterPip(shouldEnter)

    override fun requestAutoExitPip() = delegate.requestAutoExitPip()
    override fun consumeAutoExitPip() = delegate.consumeAutoExitPip()

    override fun clearPipDismissed() = delegate.clearPipDismissed()

    override fun setPipAspectRatio(aspect: Pair<Int, Int>?) {
        delegate.setPipAspectRatio(aspect?.let { Rational(it.first, it.second) })
    }

    override fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int) {
        delegate.updatePipSourceRect(Rect(left, top, right, bottom))
    }

    override fun reset() = delegate.reset()

    private fun PipAction.toLegacy(): LegacyPipAction = when (this) {
        PipAction.PLAY -> LegacyPipAction.PLAY
        PipAction.PAUSE -> LegacyPipAction.PAUSE
        PipAction.SKIP_FORWARD -> LegacyPipAction.SKIP_FORWARD
        PipAction.SKIP_BACKWARD -> LegacyPipAction.SKIP_BACKWARD
        PipAction.NEXT -> LegacyPipAction.NEXT
    }

    private fun LegacyPipAction.toCommon(): PipAction = when (this) {
        LegacyPipAction.PLAY -> PipAction.PLAY
        LegacyPipAction.PAUSE -> PipAction.PAUSE
        LegacyPipAction.SKIP_FORWARD -> PipAction.SKIP_FORWARD
        LegacyPipAction.SKIP_BACKWARD -> PipAction.SKIP_BACKWARD
        LegacyPipAction.NEXT -> PipAction.NEXT
    }
}
