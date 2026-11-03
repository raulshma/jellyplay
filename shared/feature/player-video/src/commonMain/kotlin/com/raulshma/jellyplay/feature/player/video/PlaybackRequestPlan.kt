package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackRequestSpecific
import com.raulshma.jellyplay.core.model.PlaybackTls
import com.raulshma.jellyplay.core.model.VideoPerformanceTuning
import com.raulshma.jellyplay.core.model.mediaRuleContentType
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource

/**
 * Pure plan for the engine load request — the decision half of
 * `PlayerSessionManager.initializeEngine` (the `MpvLoadOptionsPlan`
 * precedent: policy beside the spine it feeds). The manager executes the
 * returned [PlaybackRequest] through its own seams (adopt → load); the plan
 * owns every decision the 20-field inline construction used to hand-copy:
 *
 * - **Header/token resolution**: a non-blank access token contributes the
 *   identity's `Authorization` header (an absent/blank token contributes
 *   nothing) and rides the request as `authToken` either way — engines
 *   that need it for progress-URL signing read the raw value.
 * - **The language-rule resolution** ([TrackResolutionEngine.resolve]) over
 *   the item's rule context (content type, series-then-item titles, the
 *   published server streams): the resolved audio language falls back to
 *   the global preferred language on a null axis — the exact precedence
 *   the helper's ladder applies (per-item/series manual preferences are
 *   the helper's rung and outrank both). A rule-driven OFF keeps the
 *   engine's native subtitle preference unset — the helper's ladder
 *   enforces the Off itself.
 * - **The AUTO-mode bitrate cap**: only `PlaybackMode.AUTO` applies the
 *   caller-resolved profile-capped bitrate ([Inputs.resolvedMaxBitrate] —
 *   issue #186 §5.7 folded the former resolve lambda into the
 *   [PlaybackCapResolver] value the manager captures per request; a
 *   forced/pinned mode never applies it). Same server, same split (#19):
 *   the external hand-off honours the home tier on a LOCAL server like the
 *   in-app paths do, then the performance-profile ceiling on top.
 * - **Buffer sizing** from the preload-buffer preference clamped by the
 *   profile's [VideoPerformanceTuning.maxPreloadBuffer] ceiling (§5.9 —
 *   the user's stricter tier is never raised), the server duration
 *   projection (ticks → ms), and the **engineSpecific payload**
 *   (Exo unpacks the ReplayGain + MIME hint; the mpv engines unpack the
 *   app-level client-certificate paths).
 *
 * The impure projections arrive as plain inputs (data-in/data-out — the
 * fold touches no store, flow or identity seam itself): the side-loaded
 * subtitle set, the artwork URI, and the identity/bitrate reads captured
 * once per request and passed as values + lambdas.
 *
 * Pinned by `PlaybackRequestPlanTest` (jvmTest — header/token resolution,
 * the subtitle-disabled fallback arm, the AUTO cap, buffer sizing, the
 * engineSpecific payload).
 */
internal object PlaybackRequestPlan {

    /** The request slice the fold consumes (see the class KDoc for each decision). */
    data class Inputs(
        val url: String,
        val startPositionTicks: Long,
        val playMethod: PlayMethod,
        /** The MIME hint the offline probe inferred (`null` on the online path). */
        val mimeType: String?,
        val detail: MediaDetail,
        val agg: VideoPlayerAggregate,
        /** The published server streams (the rule context's signs/songs input). */
        val streams: List<MediaStream>,
        /** Plain projection of the side-loaded subtitle build (the impure caller half). */
        val externalSubtitles: List<SubtitleSource>,
        val artworkUri: String?,
        /** The identity reads, captured once per request by the caller. */
        val serverUrl: String?,
        val token: String?,
        val clientTls: PlaybackTls?,
        val authorizationHeader: (token: String) -> Pair<String, String>,
        /**
         * The profile-capped effective server bitrate (issue #186 §5.7): the
         * caller resolves `min(network/user cap, performance-profile cap)` via
         * [com.raulshma.jellyplay.core.data.playback.PlaybackCapResolver] and
         * passes the folded VALUE — the plan keeps no resolve lambda. Only
         * `PlaybackMode.AUTO` consumes it (the forced-mode bypass is
         * deliberate); `null` = uncapped.
         */
        val resolvedMaxBitrate: Long? = null,
        /**
         * The resolved performance tuning (§5.9): its `maxPreloadBuffer`
         * ceiling clamps the preload-buffer preference (the user's stricter
         * tier is never raised). Defaults to the no-profile no-op.
         */
        val performanceTuning: VideoPerformanceTuning = VideoPerformanceTuning.NONE,
    )

    fun of(inputs: Inputs): PlaybackRequest {
        val headers = mutableMapOf<String, String>()
        if (!inputs.token.isNullOrBlank()) {
            headers += inputs.authorizationHeader(inputs.token)
        }

        // When the language rule engine resolves languages for this
        // item, seed the request's preferred languages so the engine's native
        // track selection (ExoPlayer track-selection parameters) starts out in
        // agreement with the helper's restore ladder. A null resolution axis
        // falls back to the global preferred language — the exact precedence
        // the ladder applies (per-item/series manual preferences are the
        // helper's rung and outrank both).
        val ruleResolution = TrackResolutionEngine.resolve(
            inputs.agg.subtitle.languageRules,
            TrackRuleContext(
                contentType = mediaRuleContentType(inputs.detail.item.mediaType),
                titles = listOfNotNull(
                    inputs.detail.item.seriesName?.takeIf { it.isNotBlank() },
                    inputs.detail.item.name?.takeIf { it.isNotBlank() },
                ),
                streams = inputs.streams,
            ),
        )

        // §5.9: the profile's preload-buffer ceiling — the plan's exact fold.
        // The cap only ever LOWERS the user's tier (a user who already picked
        // LOW keeps it); `null` (HIGH / no profile) leaves the preference
        // alone. Compared on maxBufferMs, which is monotonic across
        // LOW < MEDIUM < HIGH < UNLIMITED.
        val userPreloadBuffer = inputs.agg.videoPlayer.videoPreloadBufferSize
        val preloadBuffer = inputs.performanceTuning.maxPreloadBuffer?.let { cap ->
            if (userPreloadBuffer.maxBufferMs > cap.maxBufferMs) cap else userPreloadBuffer
        } ?: userPreloadBuffer

        return PlaybackRequest(
            uri = inputs.url,
            title = inputs.detail.item.name,
            startPositionMs = inputs.startPositionTicks / 10_000,
            artworkUri = inputs.artworkUri,
            externalSubtitles = inputs.externalSubtitles,
            headers = headers,
            preferredAudioLanguage = ruleResolution?.audioLanguage
                ?: inputs.agg.subtitle.preferredAudioLanguage,
            preferAudioDescription = inputs.agg.subtitle.preferAudioDescription,
            // A rule-driven OFF keeps the engine's native subtitle preference
            // unset — the helper's ladder enforces the Off itself.
            preferredSubtitleLanguage = if (ruleResolution?.subtitleDisabled == true) {
                null
            } else {
                ruleResolution?.subtitleLanguage ?: inputs.agg.subtitle.preferredSubtitleLanguage
            },
            maxVideoBitrate = if (inputs.agg.playback.playbackMode == PlaybackMode.AUTO)
                // The caller-resolved profile-capped bitrate (#186 §5.7: same
                // server, same split — the home tier on a LOCAL server folds
                // in before the profile ceiling, exactly like the in-app
                // paths). Forced/pinned modes deliberately bypass the cap.
                inputs.resolvedMaxBitrate?.toInt()
                else null,
            serverUrl = inputs.serverUrl,
            authToken = inputs.token,
            playMethod = inputs.playMethod,
            minBufferMs = preloadBuffer.minBufferMs,
            maxBufferMs = preloadBuffer.maxBufferMs,
            serverDurationMs = (inputs.detail.item.runTimeTicks ?: 0L) / 10_000,
            // The per-engine request payload (core:model's
            // PlaybackRequestSpecific behind the contract's engineSpecific
            // slot): Exo unpacks the ReplayGain + MIME hint; the mpv engines
            // unpack the app-level client-certificate paths (null when no
            // certificate is enabled — OkHttp-backed engines inherit it via
            // the shared TLS layer instead).
            engineSpecific = PlaybackRequestSpecific(
                normalizationGain = inputs.detail.item.normalizationGain,
                mimeType = inputs.mimeType,
                tls = inputs.clientTls,
            ),
        )
    }
}
