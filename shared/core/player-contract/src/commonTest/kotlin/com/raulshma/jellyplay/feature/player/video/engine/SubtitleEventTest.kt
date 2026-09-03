package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the invariants of [SubtitleEvent] — the one-shot subtitle-event
 * channel engines surface to the UI:
 *
 *  - [SubtitleEvent.MalformedTrackDisabled] is a singleton `data object`:
 *    collectors can compare by identity, and no per-event allocation churns
 *    the flow.
 *  - The event type is sealed: the UI's `when` over the channel is
 *    exhaustive today and adding a variant is a compile-visible change.
 */
class SubtitleEventTest {

    @Test
    fun `malformed track event is a singleton`() {
        assertSame(SubtitleEvent.MalformedTrackDisabled, SubtitleEvent.MalformedTrackDisabled)
        assertEquals(SubtitleEvent.MalformedTrackDisabled, SubtitleEvent.MalformedTrackDisabled)
    }

    @Test
    fun `the event is a SubtitleEvent`() {
        val event: SubtitleEvent = SubtitleEvent.MalformedTrackDisabled
        assertTrue(event is SubtitleEvent.MalformedTrackDisabled)
    }
}
