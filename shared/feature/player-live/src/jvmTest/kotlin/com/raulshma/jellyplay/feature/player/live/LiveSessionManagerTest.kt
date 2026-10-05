package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackResolution
import com.raulshma.jellyplay.core.model.PlaybackResolveRequest
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [LiveSessionManager.resolve]'s policy over the repository's ONE
 * resolve seam ([PlaybackRepository.resolvePlayable]) — the live
 * source-resolution choreography extracted from the ViewModel's former
 * inline `resolveLiveStream` (playChannel's tune and onTranscodeFallback's
 * TRANSCODE re-resolve both funnel through it):
 *
 *  - a trustworthy resolve verdict returns directly, without any forced
 *    re-request;
 *  - an Unplayable verdict AND the DIRECT_STREAM probe-override (Direct
 *    Stream asked, transcode resolved) both fall to the forced re-request,
 *    whose request carries `forceLiveStreamFallback` and every field of the
 *    original;
 *  - every failure arm of the forced re-request resolves to null. (The
 *    ladder's URL-building arms themselves — direct stream → transcode URL →
 *    liveStreamId, the always-direct-URL divergence included — live in the
 *    repository now and are pinned there: core:data's LiveStreamResolutionTest
 *    and PlaybackRepositoryImplTest.)
 *
 * The mockk `PlaybackRepository` also pins exactly which arguments cross
 * the repo seam (blank mediaSourceId, AUTO mode, startTimeTicks 0, the
 * stream-index overrides and the liveStreamOption passthrough).
 */
class LiveSessionManagerTest {

    private val playbackRepo: PlaybackRepository = mockk(relaxed = true)
    private val manager = LiveSessionManager(playbackRepo)

    private fun channel(id: String = "ch-1", name: String = "BBC One") =
        LiveTvChannel(id = id, name = name)

    /** The request the manager must build for [option] (blank source id et al). */
    private fun expectedRequest(
        option: LiveStreamOption,
        force: Boolean = false,
        audioStreamIndex: Int? = 2,
        subtitleStreamIndex: Int? = 5,
    ) = PlaybackResolveRequest(
        itemId = "ch-1",
        mediaSourceId = "",
        startTimeTicks = 0L,
        audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex,
        maxStreamingBitrateBits = null,
        mode = PlaybackMode.AUTO,
        playerType = PlayerType.EXTERNAL,
        liveStreamOption = option,
        forceLiveStreamFallback = force,
    )

    private fun serverVerdict(url: String = "https://server/verdict", method: PlayMethod) =
        PlaybackResolution.Resolved(
            ResolvedPlayback(
                mediaSourceId = "server-src",
                streamUrl = url,
                playMethod = method,
                playSessionId = "psid-server",
                maxStreamingBitrate = null,
            ),
        )

    private fun ladderVerdict(
        url: String = "https://server/Videos/src-1/stream",
        method: PlayMethod = PlayMethod.DIRECT_STREAM,
    ) = PlaybackResolution.Resolved(
        ResolvedPlayback(
            mediaSourceId = "src-1",
            streamUrl = url,
            playMethod = method,
            playSessionId = "psid-1",
            maxStreamingBitrate = null,
            container = "ts",
        ),
    )

    @Test
    fun `trustworthy verdict returns directly without any forced re-request`() = runTest {
        coEvery {
            playbackRepo.resolvePlayable(expectedRequest(LiveStreamOption.AUTO))
        } returns serverVerdict(method = PlayMethod.DIRECT_PLAY)

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
        // Exactly one resolve call: no forced re-request may follow it.
        coVerify(exactly = 1) { playbackRepo.resolvePlayable(any()) }
    }

    @Test
    fun `unplayable verdict falls to the forced ladder and crosses the exact repo arguments`() = runTest {
        coEvery {
            playbackRepo.resolvePlayable(expectedRequest(LiveStreamOption.AUTO))
        } returns PlaybackResolution.Unplayable
        coEvery {
            playbackRepo.resolvePlayable(expectedRequest(LiveStreamOption.AUTO, force = true))
        } returns ladderVerdict()

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = 2,
            subtitleStreamIndex = 5,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        // The ladder's ResolvedPlayback: source identity + server play
        // session, the direct-stream URL the ladder built.
        assertEquals("src-1", resolved?.mediaSourceId)
        assertEquals("https://server/Videos/src-1/stream", resolved?.streamUrl)
        assertEquals(PlayMethod.DIRECT_STREAM, resolved?.playMethod)
        assertEquals("psid-1", resolved?.playSessionId)
        // The forced re-request carries every field of the original plus the
        // force flag (blank mediaSourceId on both calls — a channel-id-as-
        // source-id makes the server return an empty source list).
        coVerify(exactly = 1) { playbackRepo.resolvePlayable(expectedRequest(LiveStreamOption.AUTO)) }
        coVerify(exactly = 1) { playbackRepo.resolvePlayable(expectedRequest(LiveStreamOption.AUTO, force = true)) }
    }

    @Test
    fun `probe override - DIRECT_STREAM option over a transcode verdict falls to the ladder`() = runTest {
        // The user asked for Direct Stream; the server's live-source probe
        // failed and it resolved a transcode. The verdict must be ignored.
        coEvery {
            playbackRepo.resolvePlayable(
                expectedRequest(LiveStreamOption.DIRECT_STREAM, audioStreamIndex = null, subtitleStreamIndex = null),
            )
        } returns serverVerdict(method = PlayMethod.TRANSCODE)
        coEvery {
            playbackRepo.resolvePlayable(
                expectedRequest(LiveStreamOption.DIRECT_STREAM, force = true, audioStreamIndex = null, subtitleStreamIndex = null),
            )
        } returns ladderVerdict()

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
        coVerify(exactly = 1) { playbackRepo.resolvePlayable(match { it.forceLiveStreamFallback }) }
    }

    @Test
    fun `transcode option accepts the server verdict without a forced re-request`() = runTest {
        // The onTranscodeFallback re-resolve runs under TRANSCODE, where the
        // probe-override never fires — the server's verdict rides as-is.
        coEvery {
            playbackRepo.resolvePlayable(
                expectedRequest(LiveStreamOption.TRANSCODE, audioStreamIndex = null, subtitleStreamIndex = null),
            )
        } returns serverVerdict(
            url = "https://server/master.m3u8",
            method = PlayMethod.TRANSCODE,
        )

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.TRANSCODE,
            playerType = PlayerType.EXTERNAL,
        )

        assertEquals("https://server/master.m3u8", resolved?.streamUrl)
        assertEquals(PlayMethod.TRANSCODE, resolved?.playMethod)
        coVerify(exactly = 1) { playbackRepo.resolvePlayable(any()) }
    }

    @Test
    fun `a failed forced re-request resolves to null`() = runTest {
        // Every ladder failure arm (fetch failed, no sources, no playable
        // method, blank URL) reaches the manager as Unplayable.
        coEvery {
            playbackRepo.resolvePlayable(any())
        } returns PlaybackResolution.Unplayable

        val resolved = manager.resolve(
            channel = channel(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            option = LiveStreamOption.AUTO,
            playerType = PlayerType.EXTERNAL,
        )

        assertNull(resolved)
        coVerify(exactly = 2) { playbackRepo.resolvePlayable(any()) }
    }
}
