package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue

/**
 * Subtitle-sync preview seam (wave 8C): the member set the commonMain
 * [VideoPlayerViewModel][com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel]
 * calls. The androidMain class formerly named `SubtitlePreviewRepository` was
 * renamed [AndroidSubtitlePreviewRepository][com.raulshma.jellyplay.feature.player.video.subtitle.AndroidSubtitlePreviewRepository]
 * and implements this interface (OkHttp fetch + media3 subtitle parsing stay
 * there). The jvmMain actual is a no-op (no desktop AV-sync sheet host yet).
 */
interface SubtitlePreviewRepository {

    /**
     * Resolves [source] to bytes and parses it into cues; null when the
     * source cannot be read or is not text-parseable. [headers] carry auth
     * for HTTP(s) sources.
     */
    suspend fun loadCues(
        source: SubtitleSource,
        headers: Map<String, String> = emptyMap(),
    ): List<TimedCue>?

    /** Drops the memoized cue cache (whole cache, or just [url]'s entry). */
    fun clearCache(url: String? = null)
}
