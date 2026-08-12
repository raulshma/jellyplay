package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ResolvedPlayback

interface PlaybackRepository {

    suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit>

    suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit>

    suspend fun reportPlaybackStopped(itemId: String, sessionId: String, positionTicks: Long): Result<Unit>

    fun getImageUrl(itemId: String, imageType: String = "Primary", maxWidth: Int? = 400): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = 1280): String

    /** Fetches an item's image bytes via the authenticated API (for offline storage). */
    suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray?

    fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long = 0,
        liveStreamId: String? = null,
    ): String

    /**
     * Queries the server `PlaybackInfo` endpoint. See
     * [PlaybackApiClient.fetchPlaybackInfo] for parameter semantics.
     */
    suspend fun fetchPlaybackInfo(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        maxStreamingBitrateBits: Long?,
        mode: PlaybackMode,
        playerType: PlayerType,
        liveStreamOption: LiveStreamOption? = null,
    ): Result<PlaybackInfoResult>

    /**
     * Resolves a playable [ResolvedPlayback] (URL + PlayMethod + server
     * playSessionId) for [itemId] by consulting the `PlaybackInfo` endpoint
     * under [mode] and then choosing Direct Play / Direct Stream / Transcode
     * per the server's playability decision. Returns `null` when the server
     * offers no playable method for the source.
     *
     * When [liveStreamOption] is non-null it overrides [mode] for live TV
     * items (see [LiveStreamOption]).
     */
    suspend fun resolvePlayback(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        maxStreamingBitrateBits: Long?,
        mode: PlaybackMode,
        playerType: PlayerType,
        liveStreamOption: LiveStreamOption? = null,
    ): ResolvedPlayback?

    fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long = 0,
        maxBitrate: Int? = null,
        useAudioEndpoint: Boolean = false,
        liveStreamId: String? = null,
    ): String

    fun getSubtitleDeliveryUrl(deliveryUrl: String): String

    fun getServerUrl(): String?

    fun getAccessToken(): String?

    fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String

    suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps>

    suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps>

    suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>>

    /**
     * Evicts the cached media segments for [itemId] so the next
     * [getMediaSegments] call performs a fresh server fetch rather than serving
     * the TTL-cached list. Used by a force resync to ensure the segments axis
     * reflects the current server state instead of a recently cached snapshot.
     */
    fun invalidateSegmentsCache(itemId: String)

    suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>>

    suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit>

    /**
     * Searches the server's configured remote subtitle providers for [itemId]
     * in the given [language] (ISO 639-2/3 code, e.g. `"eng"`). Unlike
     * [getRemoteSubtitles], which returns the server's default provider
     * results, this delegates to the language-scoped OpenSubtitles search.
     */
    suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>>

    /**
     * Uploads a subtitle file (Base64-encoded [data]) to the item, making it
     * available as a new embedded subtitle stream. Mirrors the editor's upload
     * path so the player can contribute subtitles without leaving playback.
     */
    suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit>

    /**
     * Returns the list of language cultures the server understands for
     * subtitle upload/search selection (driven by `MetadataEditorInfo`).
     */
    suspend fun getSubtitleCultures(itemId: String): Result<List<CultureInfo>>

    suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray?
}
