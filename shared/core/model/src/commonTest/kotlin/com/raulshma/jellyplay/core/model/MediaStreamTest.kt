package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of [MediaStream.isBundleableSubtitle] — the single
 * predicate deciding which subtitle streams an offline download bundles:
 *
 *  - Only SUBTITLE streams are bundleable; video/audio/image streams never are.
 *  - A subtitle stream qualifies when it is EXTERNAL (a sidecar file) OR it
 *    exposes a server [MediaStream.deliveryUrl] (embedded but fetchable).
 *  - An embedded subtitle with neither externality nor a delivery URL is NOT
 *    bundleable — nothing on disk could be written for it.
 *  - A blank deliveryUrl counts as absent (the server emits "" for unusable
 *    URLs); the predicate must not bundle a stream whose URL is whitespace.
 */
class MediaStreamTest {

    private fun stream(
        type: StreamType = StreamType.SUBTITLE,
        isExternal: Boolean = false,
        deliveryUrl: String? = null,
    ) = MediaStream(
        index = 0,
        type = type,
        isExternal = isExternal,
        deliveryUrl = deliveryUrl,
    )

    @Test
    fun `external subtitle is bundleable with or without a delivery url`() {
        assertTrue(stream(isExternal = true, deliveryUrl = null).isBundleableSubtitle)
        assertTrue(stream(isExternal = true, deliveryUrl = "/videos/1/sub.srt").isBundleableSubtitle)
    }

    @Test
    fun `embedded subtitle with a delivery url is bundleable`() {
        assertTrue(stream(isExternal = false, deliveryUrl = "/videos/1/sub.vtt").isBundleableSubtitle)
    }

    @Test
    fun `embedded subtitle with no delivery url is not bundleable`() {
        assertFalse(stream(isExternal = false, deliveryUrl = null).isBundleableSubtitle)
    }

    @Test
    fun `blank delivery url counts as absent`() {
        assertFalse(stream(isExternal = false, deliveryUrl = "").isBundleableSubtitle)
        assertFalse(stream(isExternal = false, deliveryUrl = "   ").isBundleableSubtitle)
    }

    @Test
    fun `non-subtitle streams are never bundleable`() {
        for (type in StreamType.entries - StreamType.SUBTITLE) {
            assertFalse(
                stream(type = type, isExternal = true, deliveryUrl = "/videos/1/x").isBundleableSubtitle,
                type.name,
            )
        }
    }
}
