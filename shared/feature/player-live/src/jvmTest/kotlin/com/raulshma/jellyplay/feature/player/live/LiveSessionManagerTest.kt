package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [LiveSessionManager.resolve] ladder — the ONE live
 * source-resolution choreography extracted from the ViewModel's former
 * inline `resolveLiveStream` (playChannel's tune and onTranscodeFallback's
 * TRANSCODE re-resolve both funnel through it):
 *
 *  - a trustworthy `resolvePlayback` verdict returns directly, without any
 *    fallback repository call;
 *  - a null verdict and the DIRECT_STREAM probe-override (Direct Stream
 *    asked, transcode resolved) both fall to the
 *    fetchPlaybackInfo/getStreamUrl ladder;
 *  - the ladder ALWAYS builds a direct `/stream` URL via `getStreamUrl` —
 *    even on the transcode arm — keeping the engine's onPlayerError →
 *    transcode fallback eligible (the pinned divergence);
 *  - every failure arm (fetch failed, no sources, no playable method,
 *    blank URL) resolves to null.
 *
 * The mockk `PlaybackRepository` also pins exactly which arguments cross
 * the repo seams (blank mediaSourceId, AUTO mode, startTimeTicks 0, the
 * stream-index overrides and the liveStreamOption passthrough).
 */
class LiveSessionManagerTest {

    private val playbackRepo: PlaybackRepository = mockk(relaxed = true)
    private val manager = LiveSessionManager(playbackRepo)

    private data class StreamUrlCall(val itemId: String, val mediaSourceId: String, val liveStreamId: String?)
    private val streamUrlCalls = mutableListOf<StreamUrlCall>()

    /** Records the ladder's URL-builder calls; returns a direct stream URL. */
    private fun stubStreamUrl() {
        every { playbackRepo.getStreamUrl(any<String>(), any<String>(), any<Long>(), anyNullable<String>()) } answers {
            streamUrlCalls += StreamUrlCall(firstArg(), arg(1), arg(3))
            "https://server/Videos/${arg<String>(1)}/stream"
        }
    }

    private fun channel(id: String = "ch-1", name: String = "BBC One") =
        LiveTvChannel(id = id, name = name)

    private fun source(
        id: String = "src-1",
        supportsDirectPlay: Boolean = false,
        supportsDirectStream: Boolean = false,
        supportsTranscoding: Boolean = false,
        transcodeUrl: String? = null,
        liveStreamId: String? = null,
    ) = MediaSource(
        id = id,
        name = "Source 1",
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = supportsDirectStream,
        supportsTranscoding = supportsTranscoding,
        transcodeUrl = transcodeUrl,
        liveStreamId = liveStreamId,
    )

    private fun info(vararg sources: MediaSource, playSessionId: String? = "psid-1") =
        PlaybackInfoResult(playSessionId = playSessionId, mediaSources = sources.toList())

    private fun serverVerdict(url: String = "https://server/resolved", method: PlayMethod = PlayMethod.DIRECT_STREAM) =
        ResolvedPlayback(
            mediaSourceId = "server-src",
            streamUrl = url,
            playMethod = method,
            playSessionId = "psid-server",
            maxStreamingBitrate = null,
        )

    @Test
    fun `resolvePlayback verdict returns directly without any fallback call`() = runTest {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns serverVerdict(url = "https://server/verdict", method = PlayMethod.DIRECT_PLAY)
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = 2,
            subtitleStreamIndex = 5,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        assertEquals("https://server/verdict", resolved?.streamUrl)
        assertEquals(PlayMethod.DIRECT_PLAY, resolved?.playMethod)
        assertEquals("psid-server", resolved?.playSessionId)
        coVerify(exactly = 0) {
            playbackRepo.fetchPlaybackInfo(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyNullable<Int>(),
                anyNullable<Int>(),
                anyNullable<Long>(),
                any(),
                any(),
                anyNullable(),
            )
        }
        verify(exactly = 0) { playbackRepo.getStreamUrl(any<String>(), any<String>(), any<Long>(), anyNullable<String>()) }
    }

    @Test
    fun `null verdict falls to the ladder and crosses the exact repo arguments`() = runTest {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(info(source(supportsDirectStream = true, liveStreamId = "live-1")))
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = 2,
            subtitleStreamIndex = 5,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        // The ladder's ResolvedPlayback: source identity + server play
        // session, the direct-stream URL the builder produced.
        assertEquals("src-1", resolved?.mediaSourceId)
        assertEquals("https://server/Videos/src-1/stream", resolved?.streamUrl)
        assertEquals(PlayMethod.DIRECT_STREAM, resolved?.playMethod)
        assertEquals("psid-1", resolved?.playSessionId)

        // The exact choreography arguments: blank mediaSourceId on both
        // calls (a channel-id-as-source-id makes the server return an empty
        // source list), AUTO mode, zero start ticks, the overrides carried
        // through, and the liveStreamOption passthrough.
        coVerify(exactly = 1) {
            playbackRepo.resolvePlayback(
                itemId = "ch-1",
                mediaSourceId = "",
                startTimeTicks = 0L,
                audioStreamIndex = 2,
                subtitleStreamIndex = 5,
                maxStreamingBitrateBits = null,
                mode = PlaybackMode.AUTO,
                playerType = PlayerType.EXTERNAL,
                liveStreamOption = LiveStreamOption.AUTO,
            )
        }
        coVerify(exactly = 1) {
            playbackRepo.fetchPlaybackInfo(
                itemId = "ch-1",
                mediaSourceId = "",
                startTimeTicks = 0L,
                audioStreamIndex = 2,
                subtitleStreamIndex = 5,
                maxStreamingBitrateBits = null,
                mode = PlaybackMode.AUTO,
                playerType = PlayerType.EXTERNAL,
                liveStreamOption = LiveStreamOption.AUTO,
            )
        }
    }

    @Test
    fun `probe override - DIRECT_STREAM option over a transcode verdict falls to the ladder`() = runTest {
        // The user asked for Direct Stream; the server's live-source probe
        // failed and it resolved a transcode. The verdict must be ignored.
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns serverVerdict(method = PlayMethod.TRANSCODE)
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(info(source(supportsDirectStream = true, liveStreamId = "live-1")))
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.DIRECT_STREAM,
            playerType = PlayerType.EXTERNAL,
        )

        // The play method reflects the URL the LADDER built (direct stream),
        // not the server's transcode verdict.
        assertEquals(PlayMethod.DIRECT_STREAM, resolved?.playMethod)
        assertEquals("src-1", resolved?.mediaSourceId)
        assertEquals("https://server/Videos/src-1/stream", resolved?.streamUrl)
    }

    @Test
    fun `transcode option ladder still builds the direct stream URL`() = runTest {
        // The onTranscodeFallback re-resolve: resolvePlayback comes back
        // null under TRANSCODE and the source only offers transcoding — yet
        // the built URL is the direct /stream URL (the tuner session is
        // already open server-side), keeping the fallback ladder eligible.
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            info(source(supportsTranscoding = true, transcodeUrl = "https://server/master.m3u8"))
        )
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.TRANSCODE,
            playerType = PlayerType.EXTERNAL,
        )

        assertEquals(PlayMethod.DIRECT_STREAM, resolved?.playMethod)
        assertEquals("https://server/Videos/src-1/stream", resolved?.streamUrl)
        // Never the transcoding master.m3u8 the server's flags suggested.
        assertTrue(streamUrlCalls.isNotEmpty())
    }

    @Test
    fun `all-false flags resolve via liveStreamId and pass it to getStreamUrl`() = runTest {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(info(source(liveStreamId = "tuner-9")))
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        assertEquals(PlayMethod.DIRECT_STREAM, resolved?.playMethod)
        assertEquals("https://server/Videos/src-1/stream", resolved?.streamUrl)
        assertEquals(listOf(StreamUrlCall("ch-1", "src-1", "tuner-9")), streamUrlCalls)
    }

    @Test
    fun `fetchPlaybackInfo failure resolves to null`() = runTest {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("server down"))
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        assertNull(resolved)
        assertTrue(streamUrlCalls.isEmpty())
    }

    @Test
    fun `fetchPlaybackInfo with no media sources resolves to null`() = runTest {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(info())
        stubStreamUrl()

        assertNull(
            manager.resolve(
                channel = channel(),
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                option = LiveStreamOption.AUTO,
                playerType = PlayerType.EXTERNAL,
            ),
        )
    }

    @Test
    fun `degenerate source resolves to null without building a URL`() = runTest {
        // No capability flags and no liveStreamId: NoPlayableMethod.
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(info(source()))
        stubStreamUrl()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        assertNull(resolved)
        assertTrue(streamUrlCalls.isEmpty(), "NoPlayableMethod must not call the URL builder")
    }

    @Test
    fun `blank built URL resolves to null`() = runTest {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(info(source(supportsDirectStream = true)))
        // Relaxed getStreamUrl returns "" — the blank-URL guard must trip.
        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        assertNull(resolved)
    }
}
