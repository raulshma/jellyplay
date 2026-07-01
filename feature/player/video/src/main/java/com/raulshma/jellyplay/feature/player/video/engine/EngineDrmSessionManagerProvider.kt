package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.exoplayer.drm.DrmSessionManager

/**
 * Supplies a configured Media3 [DrmSessionManager] (e.g. Widevine ClearKey /
 * default DRM) for the current media item, or `null` when the item is not
 * DRM-protected.
 *
 * **Why this exists (forward-looking).** JellyPlay currently relies on the
 * `X-Emby-Token` header for access control and ships no DRM. When DRM content
 * is introduced it must be configured at the call site (e.g. resolved from a
 * Jellyfin DRM licence endpoint in `PlayerSessionManager`) and injected here —
 * never hard-coded into [ExoPlayerEngine]. Keeping the engine free of any
 * concrete DRM scheme means:
 *  - the engine stays unit-testable without a DRM framework on the classpath;
 *  - additional schemes (PlayReady, ClearKey, …) can be added by supplying a
 *    different provider, with zero engine changes;
 *  - engines without DRM support ([MpvPlayerEngine], [LibVlcPlayerEngine],
 *    [NoOpEngine]) simply ignore a non-null provider.
 *
 * Wire-up: pass an instance via [EngineConfig.drmSessionManagerProvider];
 * [ExoPlayerEngine.load] installs it on its [androidx.media3.exoplayer.source.DefaultMediaSourceFactory].
 */
fun interface EngineDrmSessionManagerProvider {
    /** Returns the DRM session manager to attach, or `null` if the item is clear. */
    fun provide(): DrmSessionManager?
}
