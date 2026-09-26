package com.raulshma.jellyplay.feature.player.video

/**
 * Android adapter over the Hilt-owned legacy `core:data` CastManager
 * singleton (seam): exposes the member set the commonMain
 * [VideoPlayerViewModel] calls. The discovery/connect surface stays on the
 * legacy class — the screen reaches it through the `androidCastManager`
 * ViewModel extension. The legacy class's media3-typed
 * `castPlayerForSession` is consumed by the androidMain
 * `VideoMediaSessionFactory` wiring (the background-cast detach path), not
 * by this seam.
 */
internal class AndroidCastManager(
    val delegate: com.raulshma.jellyplay.core.data.cast.CastManager,
) : CastManager {

    override fun acquireConsumer() = delegate.acquireConsumer()

    override fun releaseConsumer() = delegate.releaseConsumer()

    override fun markBackgroundCasting(casting: Boolean) = delegate.markBackgroundCasting(casting)

    override val isBackgroundCasting: Boolean get() = delegate.isBackgroundCasting

    override fun softRelease() = delegate.softRelease()
}
