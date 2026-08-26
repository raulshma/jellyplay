package com.raulshma.jellyplay.feature.player.video

/**
 * Android adapter over the Hilt-owned legacy `core:data` CastManager
 * singleton (wave 8C seam): exposes the member set the commonMain
 * [VideoPlayerViewModel] calls. The discovery/connect surface stays on the
 * legacy class — the screen reaches it through the `androidCastManager`
 * ViewModel extension. `castPlayerForSession` is widened to the opaque
 * [Any] (media3 `Player?`) so the platform type does not leak into common
 * code.
 */
internal class AndroidCastManager(
    private val delegate: com.raulshma.jellyplay.core.data.cast.CastManager,
) : CastManager {

    override fun acquireConsumer() = delegate.acquireConsumer()

    override fun releaseConsumer() = delegate.releaseConsumer()

    override fun markBackgroundCasting(casting: Boolean) = delegate.markBackgroundCasting(casting)

    override val isBackgroundCasting: Boolean get() = delegate.isBackgroundCasting

    override fun softRelease() = delegate.softRelease()

    override val castPlayerForSession: Any? get() = delegate.castPlayerForSession
}
