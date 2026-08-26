package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.model.TrickplayInfo
import java.io.File

/**
 * Trickplay controller seam (wave 8C): the member set the commonMain
 * [VideoPlayerViewModel][com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel]
 * calls. The androidMain [TrickplayManager] implements it (the Bitmap LRU
 * tile caches stay Android); [getThumbnail] returns the platform bitmap as an
 * opaque [Any] because `android.graphics.Bitmap` must not leak into common
 * code — the androidMain `getTrickplayThumbnail` ViewModel extension narrows
 * it back. Constructed through [VideoPlayerPlatform.createTrickplayController]
 * (the Android actual passes the ActivityManager low-RAM gate the ViewModel
 * used to compute inline); the jvmMain actual is a no-op.
 */
interface TrickplayController {

    /** Server manifest, tiles fetched on demand. */
    fun initialize(itemId: String, trickplayInfo: TrickplayInfo)

    /** Server manifest, tiles cached into [cacheDir] for offline replay. */
    fun initializeWithCache(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File)

    /** Locally bundled trickplay (shipped with the download). */
    fun initializeLocal(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File)

    /** Drops all tiles + state (item switch / teardown). */
    fun clear()

    /** Thumbnail for [positionMs], or null when not cached/available. */
    suspend fun getThumbnail(positionMs: Long): Any?
}

