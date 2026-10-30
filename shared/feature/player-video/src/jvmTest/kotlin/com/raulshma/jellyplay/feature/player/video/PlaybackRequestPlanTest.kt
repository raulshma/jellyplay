package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.LanguageRule
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackTls
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SubtitleTrackMode
import com.raulshma.jellyplay.core.model.TrackSelectionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM pins for [PlaybackRequestPlan] — the engine load request's
 * decision fold extracted from `PlayerSessionManager.initializeEngine`
 * (the TrackResolutionEngineTest pattern: plain data in, plain data out,
 * recording lambdas instead of mocks). Pins the five behaviors the former
 * inline 20-field construction carried with zero coverage:
 *
 *  - the header/token resolution (blank/absent token contributes no header;
 *    the token always rides as authToken);
 *  - the subtitle-disabled fallback arm (a rule-driven OFF keeps the
 *    engine's native subtitle preference unset) and the rule → global
 *    language precedence;
 *  - the AUTO-mode bitrate cap (forced modes never consult the resolver;
 *    the cap resolves against the server host — the #19 split);
 *  - buffer sizing from the preload-buffer preference;
 *  - the engineSpecific payload assembly.
 */
class PlaybackRequestPlanTest {

    /** Records the identity/bitrate reads the fold drives — the test's probe. */
    private class RecordingProjections {
        val headerTokens = mutableListOf<String>()
        val bitrateHosts = mutableListOf<String?>()
        var bitrateCap: Long? = 8_000_000L

        fun authorizationHeader(token: String): Pair<String, String> {
            headerTokens += token
            return "Authorization" to "MediaBrowser Token=\"$token\""
        }

        fun resolveServerBitrate(host: String?): Long? {
            bitrateHosts += host
            return bitrateCap
        }
    }

    private val projections = RecordingProjections()

    private fun inputs(
        agg: VideoPlayerAggregate = VideoPlayerAggregate(),
        detail: MediaDetail = MediaDetail(
            item = MediaItem(id = "item-1", name = "Some Movie", mediaType = MediaType.MOVIE),
        ),
        url: String = "https://example.com/videos/1/stream",
        startPositionTicks: Long = 0L,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mimeType: String? = null,
        // Ported URL: serverHostOf's host projection is exact for host:port forms
        // (the un-portable shape's path-carrying fallback is the core helper's
        // pre-existing behavior, not this plan's).
        serverUrl: String? = "https://example.com:8096/jelly/web/index.html",
        token: String? = null,
        clientTls: PlaybackTls? = null,
    ) = PlaybackRequestPlan.Inputs(
        url = url,
        startPositionTicks = startPositionTicks,
        playMethod = playMethod,
        mimeType = mimeType,
        detail = detail,
        agg = agg,
        streams = emptyList(),
        externalSubtitles = emptyList(),
        artworkUri = "https://example.com/items/1/poster",
        serverUrl = serverUrl,
        token = token,
        clientTls = clientTls,
        authorizationHeader = projections::authorizationHeader,
        resolveServerBitrate = projections::resolveServerBitrate,
    )

    /** An episode detail — the rule engine's episode content type. */
    private fun episodeDetail(name: String = "Pilot", seriesName: String = "Some Series") = MediaDetail(
        item = MediaItem(
            id = "ep-1",
            name = name,
            mediaType = MediaType.EPISODE,
            seriesId = "series-1",
            seriesName = seriesName,
        ),
    )

    // ── Header/token resolution ──────────────────────────────────────────────

    @Test
    fun `a present token contributes the authorization header and rides as authToken`() {
        val request = PlaybackRequestPlan.of(inputs(token = "tok-1"))

        assertEquals(
            mapOf("Authorization" to "MediaBrowser Token=\"tok-1\""),
            request.headers,
        )
        assertEquals("tok-1", request.authToken)
    }

    @Test
    fun `a blank or absent token contributes no header - the token still rides raw`() {
        val blank = PlaybackRequestPlan.of(inputs(token = "   "))
        assertTrue(blank.headers.isEmpty(), "a blank token must not mint an empty header entry")
        assertEquals("   ", blank.authToken)

        val absent = PlaybackRequestPlan.of(inputs(token = null))
        assertTrue(absent.headers.isEmpty())
        assertNull(absent.authToken)
    }

    // ── The subtitle-disabled fallback arm + language precedence ─────────────

    @Test
    fun `a rule-driven OFF keeps the engine's native subtitle preference unset`() {
        val agg = VideoPlayerAggregate().copy(
            subtitle = SubtitleSlice(
                languageRules = LanguageRuleSet(
                    preset = TrackSelectionPreset.CUSTOM,
                    rules = listOf(
                        LanguageRule(id = "off", audioLanguages = listOf("ger"), subtitleMode = SubtitleTrackMode.OFF),
                    ),
                ),
                preferredSubtitleLanguage = "eng",
                preferredAudioLanguage = "jpn",
            ),
        )

        val request = PlaybackRequestPlan.of(inputs(agg = agg, detail = episodeDetail()))

        assertNull(
            request.preferredSubtitleLanguage,
            "the OFF rule must null the engine's subtitle preference — the helper's ladder enforces the Off",
        )
        // The matched rule's audio language wins over the global rung.
        assertEquals("ger", request.preferredAudioLanguage)
    }

    @Test
    fun `a rule language wins over the global preferred languages`() {
        val agg = VideoPlayerAggregate().copy(
            subtitle = SubtitleSlice(
                languageRules = LanguageRuleSet(
                    preset = TrackSelectionPreset.CUSTOM,
                    rules = listOf(
                        LanguageRule(id = "subs", subtitleLanguages = listOf("eng")),
                    ),
                ),
                preferredSubtitleLanguage = "fra",
                preferredAudioLanguage = "jpn",
            ),
        )

        val request = PlaybackRequestPlan.of(inputs(agg = agg, detail = episodeDetail()))

        assertEquals("eng", request.preferredSubtitleLanguage)
        // No audio axis on the matched rule → the global preferred falls through.
        assertEquals("jpn", request.preferredAudioLanguage)
    }

    @Test
    fun `no rule resolution falls through to the global preferred languages`() {
        val agg = VideoPlayerAggregate().copy(
            subtitle = SubtitleSlice(
                preferredSubtitleLanguage = "fra",
                preferredAudioLanguage = "jpn",
            ),
        )

        val request = PlaybackRequestPlan.of(inputs(agg = agg, detail = episodeDetail()))

        assertEquals("fra", request.preferredSubtitleLanguage)
        assertEquals("jpn", request.preferredAudioLanguage)
    }

    // ── The AUTO-mode bitrate cap ────────────────────────────────────────────

    @Test
    fun `AUTO mode resolves the cap against the server host`() {
        val agg = VideoPlayerAggregate().copy(
            playback = PlaybackSlice(playbackMode = PlaybackMode.AUTO),
        )

        val request = PlaybackRequestPlan.of(inputs(agg = agg))

        // The #19 split: the host projection of the server URL, not the URL.
        assertEquals(listOf<String?>("example.com"), projections.bitrateHosts)
        assertEquals(8_000_000, request.maxVideoBitrate)
    }

    @Test
    fun `a forced mode never consults the bitrate resolver`() {
        val agg = VideoPlayerAggregate().copy(
            playback = PlaybackSlice(playbackMode = PlaybackMode.FORCE_DIRECT_PLAY),
        )

        val request = PlaybackRequestPlan.of(inputs(agg = agg))

        assertTrue(projections.bitrateHosts.isEmpty(), "the resolver lambda must stay untouched on a forced mode")
        assertNull(request.maxVideoBitrate)
    }

    @Test
    fun `AUTO mode with no effective cap stays capped-null`() {
        val agg = VideoPlayerAggregate().copy(
            playback = PlaybackSlice(playbackMode = PlaybackMode.AUTO),
        )
        projections.bitrateCap = null

        val request = PlaybackRequestPlan.of(inputs(agg = agg))

        assertNull(request.maxVideoBitrate)
    }

    // ── Buffer sizing ────────────────────────────────────────────────────────

    @Test
    fun `buffer sizes ride the preload-buffer preference`() {
        val high = VideoPlayerAggregate().copy(
            videoPlayer = VideoPlayerAggregate().videoPlayer.copy(videoPreloadBufferSize = PreloadBufferSize.HIGH),
        )
        val request = PlaybackRequestPlan.of(inputs(agg = high))
        assertEquals(50_000, request.minBufferMs)
        assertEquals(120_000, request.maxBufferMs)

        val low = VideoPlayerAggregate().copy(
            videoPlayer = VideoPlayerAggregate().videoPlayer.copy(videoPreloadBufferSize = PreloadBufferSize.LOW),
        )
        val lowRequest = PlaybackRequestPlan.of(inputs(agg = low))
        assertEquals(10_000, lowRequest.minBufferMs)
        assertEquals(25_000, lowRequest.maxBufferMs)
    }

    // ── The engineSpecific payload ───────────────────────────────────────────

    @Test
    fun `the engineSpecific payload carries the gain, MIME hint and TLS paths`() {
        val request = PlaybackRequestPlan.of(
            inputs(
                mimeType = "video/mp4",
                clientTls = PlaybackTls(clientCertificatePath = "/cert.pem", clientKeyPath = "/key.pem"),
                detail = MediaDetail(
                    item = MediaItem(
                        id = "item-1",
                        name = "Some Movie",
                        mediaType = MediaType.MOVIE,
                        normalizationGain = 0.42f,
                        runTimeTicks = 54_000_000_000L,
                    ),
                ),
                startPositionTicks = 90_000_000_000L,
            ),
        )

        val specific = request.requestSpecific
        assertEquals(0.42f, specific?.normalizationGain)
        assertEquals("video/mp4", specific?.mimeType)
        assertEquals("/cert.pem", specific?.tls?.clientCertificatePath)
        // The ticks → ms projections (the engine's duration fallback + resume start).
        assertEquals(9_000_000L, request.startPositionMs)
        assertEquals(5_400_000L, request.serverDurationMs)
    }

    @Test
    fun `the engineSpecific payload stays null-fielded without the optional inputs`() {
        val request = PlaybackRequestPlan.of(inputs())

        val specific = request.requestSpecific
        assertNull(specific?.normalizationGain)
        assertNull(specific?.mimeType)
        assertNull(specific?.tls)
        assertEquals(0L, request.serverDurationMs)
    }
}
