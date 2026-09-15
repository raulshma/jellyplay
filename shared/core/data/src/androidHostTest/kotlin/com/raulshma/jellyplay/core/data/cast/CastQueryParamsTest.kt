package com.raulshma.jellyplay.core.data.cast

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure query-shaping tests for `String.withCastQueryParams` — the cast URL
 * enrichment moved from CastManager.kt to commonMain beside
 * [CastMediaOptions] (CastStateFanout precedent). No Robolectric, no Android:
 * what is pinned here is the exact query-param construction — param order,
 * separator choice, existing-param preservation, the all-null short-circuit,
 * and the declared mediaSourceId exclusion. CastManagerTest pins the
 * Android-side MediaItem fold on top of this helper.
 */
class CastQueryParamsTest {

    @Test
    fun `all-null options return the receiver unchanged`() {
        val url = "http://server/Videos/1/stream"

        assertEquals(url, url.withCastQueryParams(CastMediaOptions()))
    }

    @Test
    fun `full options append audio subtitle and bitrate params in fixed order`() {
        val url = "http://server/Videos/1/stream"

        val enriched = url.withCastQueryParams(
            CastMediaOptions(audioStreamIndex = 2, subtitleStreamIndex = 3, maxVideoBitrate = 8_000_000),
        )

        assertEquals(
            "http://server/Videos/1/stream?AudioStreamIndex=2&SubtitleStreamIndex=3&MaxVideoBitrate=8000000",
            enriched,
        )
    }

    @Test
    fun `existing query params are preserved and joined with an ampersand`() {
        val url = "http://server/Videos/1/stream?api_key=k"

        val enriched = url.withCastQueryParams(CastMediaOptions(audioStreamIndex = 1))

        assertEquals("http://server/Videos/1/stream?api_key=k&AudioStreamIndex=1", enriched)
    }

    @Test
    fun `each option contributes its param alone when the others are null`() {
        assertEquals(
            "http://s/stream?SubtitleStreamIndex=5",
            "http://s/stream".withCastQueryParams(CastMediaOptions(subtitleStreamIndex = 5)),
        )
        assertEquals(
            "http://s/stream?MaxVideoBitrate=4000000",
            "http://s/stream".withCastQueryParams(CastMediaOptions(maxVideoBitrate = 4_000_000)),
        )
    }

    @Test
    fun `DECLARED divergence - mediaSourceId is never folded into the URL`() {
        // mediaSourceId targets source selection on the PlaybackInfo / admin
        // play-command side, not the stream URL's query params — and on its
        // own it must not even flip the separator.
        val url = "http://server/Videos/1/stream"

        assertEquals(url, url.withCastQueryParams(CastMediaOptions(mediaSourceId = "ms-1")))
    }
}
