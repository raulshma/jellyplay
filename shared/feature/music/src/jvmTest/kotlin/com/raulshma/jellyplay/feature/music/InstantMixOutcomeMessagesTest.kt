package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mix_unavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins the shared instant-mix outcome → message mapping that the album and
 * artist detail screens resolve identically. Types only: [MixErrorMessage.Resource]
 * stays UNRESOLVED until render time (the commonMain VM seam has no Context),
 * so tests assert the carried [StringResource] identity, never a rendered string.
 */
class InstantMixOutcomeMessagesTest {

    @Test
    fun empty_mapsToSharedUnavailableResource() {
        val message = AudioQueueOutcome.Empty.toMixErrorMessage()

        assertSame(Res.string.music_mix_unavailable, (message as MixErrorMessage.Resource).res)
    }

    @Test
    fun failed_mapsCauseMessage() {
        val message = AudioQueueOutcome.Failed(RuntimeException("boom")).toMixErrorMessage()

        assertEquals("boom", (message as MixErrorMessage.Raw).message)
    }

    @Test
    fun failed_nullCauseMessage_mapsFallback() {
        val message = AudioQueueOutcome.Failed(RuntimeException()).toMixErrorMessage()

        assertEquals("Failed to start Instant Mix", (message as MixErrorMessage.Raw).message)
    }

    @Test
    fun started_mapsToNull() {
        assertNull(AudioQueueOutcome.Started(emptyList(), 0).toMixErrorMessage())
    }

    @Test
    fun suppressed_mapsToNull() {
        assertNull(AudioQueueOutcome.Suppressed.toMixErrorMessage())
    }
}
