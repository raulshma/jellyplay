package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlayMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the live play-method policy that stays CLIENT-SIDE in the live
 * feature: the DIRECT_STREAM probe-override ([shouldIgnoreServerTranscodeVerdict])
 * — the one arm that re-requests with `forceLiveStreamFallback` despite a
 * non-null resolve verdict. The fallback ladder itself (direct stream →
 * transcode URL → liveStreamId, plus its play-method fold) moved into the
 * repository's `resolvePlayable` seam and is pinned there
 * (core:data's LiveStreamResolutionTest + PlaybackRepositoryImplTest).
 */
class LiveStreamResolutionTest {

    @Test
    fun `probe-override fires only for DIRECT_STREAM option over a transcode verdict`() {
        // The one arm that re-requests despite a non-null resolvePlayback:
        // the user asked for Direct Stream and the server's live-source
        // probe failed, resolving a transcode instead.
        assertTrue(
            shouldIgnoreServerTranscodeVerdict(LiveStreamOption.DIRECT_STREAM, PlayMethod.TRANSCODE),
        )
        // AUTO/TRANSCODE options accept whatever the server picks.
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.AUTO, PlayMethod.TRANSCODE))
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.TRANSCODE, PlayMethod.TRANSCODE))
        // Any non-transcode verdict is trusted regardless of the option.
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.DIRECT_STREAM, PlayMethod.DIRECT_STREAM))
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.DIRECT_STREAM, PlayMethod.DIRECT_PLAY))
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.AUTO, PlayMethod.DIRECT_STREAM))
    }
}
