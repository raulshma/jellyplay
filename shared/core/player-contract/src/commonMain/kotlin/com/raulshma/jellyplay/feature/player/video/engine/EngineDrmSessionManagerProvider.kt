package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Supplies a configured DRM session manager for the current media item, or
 * `null` when the item is not DRM-protected. The returned object is
 * type-erased: on Android it is a Media3 `DrmSessionManager` (e.g. Widevine
 * ClearKey / default DRM) — [EngineConfig.drmSessionManagerProvider] is the
 * only injection point and ExoPlayerEngine casts before use.
 *
 * **Why this exists (forward-looking).** JellyPlay currently relies on the
 * `X-Emby-Token` header for access control and ships no DRM. When DRM content
 * is introduced it must be configured at the call site (e.g. resolved from a
 * Jellyfin DRM licence endpoint in `PlayerSessionManager`) and injected here —
 * never hard-coded into `ExoPlayerEngine`. Keeping the engine free of any
 * concrete DRM scheme means:
 *  - the engine stays unit-testable without a DRM framework on the classpath;
 *  - additional schemes (PlayReady, ClearKey, …) can be added by supplying a
 *    different provider, with zero engine changes;
 *  - engines without DRM support (`MpvPlayerEngine`, `LibVlcPlayerEngine`,
 *    `NoOpEngine`, and the desktop mpv engine) simply ignore a non-null
 *    provider.
 *
 * Wire-up: pass an instance via [EngineConfig.drmSessionManagerProvider];
 * ExoPlayerEngine.load installs it on its
 * `androidx.media3.exoplayer.source.DefaultMediaSourceFactory`.
 *
 * Phase V2 note: this used to be an Android-only fun-interface returning a
 * media3 `DrmSessionManager?`; the return type was erased to `Any?` when the
 * contract moved to commonMain so non-Android targets can compile it.
 */
fun interface EngineDrmSessionManagerProvider {
    /**
     * Returns the DRM session manager to attach (a Media3 `DrmSessionManager`
     * on Android), or `null` if the item is clear.
     */
    fun provide(): Any?
}
