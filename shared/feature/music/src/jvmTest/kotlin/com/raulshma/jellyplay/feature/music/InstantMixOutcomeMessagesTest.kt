package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.InstantMixError
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mix_unavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * Pins the ONE shared instant-mix message fold
 * ([InstantMixError?][com.raulshma.jellyplay.core.data.playback.InstantMixError].toMixErrorMessage)
 * that the album and artist detail screens resolve identically — the former
 * duplicate `MusicQueueOutcome.toMixErrorMessage` fold (zero production
 * callers) is deleted. Types only: [UiMessage.Resource] stays UNRESOLVED
 * until render time (the commonMain VM seam has no Context), so tests assert
 * the carried [StringResource] identity, never a rendered string.
 */
class InstantMixOutcomeMessagesTest {

    @Test
    fun emptyMix_mapsToSharedUnavailableResource() {
        val message = InstantMixError.EmptyMix.toMixErrorMessage()

        assertSame(Res.string.music_mix_unavailable, (message as UiMessage.Resource).res)
    }

    @Test
    fun failed_mapsCauseMessage() {
        val message = InstantMixError.Failed("boom").toMixErrorMessage()

        assertEquals("boom", (message as UiMessage.Raw).text)
    }

    @Test
    fun failed_nullCauseMessage_mapsFallback() {
        val message = InstantMixError.Failed(null).toMixErrorMessage()

        assertEquals("Failed to start Instant Mix", (message as UiMessage.Raw).text)
    }

    @Test
    fun null_mapsToNull() {
        assertNull(null.toMixErrorMessage())
    }
}
