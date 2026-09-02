package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor
import com.raulshma.jellyplay.core.model.AudioBitrateTier
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.NameGuidPair
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins [DesktopAudioSourceResolver] — the desktop case-for-case port of the
 * Android audio path's `AudioLibraryBrowser.buildPlayableMediaItem`:
 *
 *  - a usable on-disk download wins and is handed to mpv as the RAW absolute
 *    path (`isLocalFile = true`) — never a `file:` URI, which mpv mis-parses
 *    with a single slash (V2b note);
 *  - otherwise the stream URL comes from the shared
 *    [PlaybackRepository.getStreamUrl] overload with EXACTLY the Android
 *    browser's arguments: `useAudioEndpoint = false` and
 *    `maxBitrate = tier.targetKbps * 1000` from the [AdaptiveBitrateSelector]
 *    tier over the persisted [StreamingQuality]; resume positions ride in as
 *    `startTimeTicks = startPositionMs * 10_000` (0 when at position 0);
 *  - both `getMediaDetail` and `resolveLocalSource` always run (the item
 *    metadata on the local path comes from the detail — so a detail success
 *    is observable even when the local file wins);
 *  - detail failure + no local → null (Android's "Failed to load track");
 *    local wins even when the detail fetch failed (the queue-only fallback).
 *
 * Hand-rolled fakes per module convention (no mocking library here). The one
 * reflective fake is [DetailMediaRepository]: [MediaRepository] aggregates
 * five parent interfaces (100+ members) for a single exercised method — a
 * literal hand fake would be hundreds of lines of stub noise, so a
 * `java.lang.reflect.Proxy` answers `getMediaDetail` and errors on anything
 * else. Stream-URL recording subclasses the shared [FakePlaybackRepository]
 * fixture to capture the six getStreamUrl arguments.
 */
class DesktopAudioSourceResolverTest {

    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    // ── fakes ─────────────────────────────────────────────────────────────

    /**
     * Proxy-backed [MediaRepository] answering only `getMediaDetail`.
     * `detail == null` scripts a FAILED detail fetch (the resolver surfaces
     * failures via `.getOrNull()`, so the fake must return a Result, not throw).
     */
    private class DetailMediaRepository(private val detail: Result<MediaDetail>?) {
        val requestedItemIds = mutableListOf<String>()

        val proxy: MediaRepository = Proxy.newProxyInstance(
            MediaRepository::class.java.classLoader,
            arrayOf(MediaRepository::class.java),
        ) { _, method, args ->
            // Kotlin mangles suspend fun names on the JVM interface
            // ("getMediaDetail-0E7RQCE") — match on the prefix. AND: a
            // suspend fun whose Kotlin return type is the VALUE class
            // `Result<T>` hands a synchronous caller the UNBOXED value (the
            // T-or-null itself, here the getOrNull() result) — returning the
            // boxed kotlin.Result CCEs inside the compiled caller.
            when {
                method.name.startsWith("getMediaDetail") -> {
                    requestedItemIds += args[0] as String
                    detail?.getOrNull()
                }
                else -> error("unexpected MediaRepository call: ${method.name}")
            }
        } as MediaRepository
    }

    /** Scripted [PlaybackSourceResolver.resolveLocalSource]; everything else errors. */
    private class FakeLocalSource(private val local: ResolvedPlaybackSource.Local?) : PlaybackSourceResolver {
        var askedFor = mutableListOf<String>()

        override suspend fun resolvePlaybackSource(
            itemId: String,
            mediaSourceId: String?,
            startPositionTicks: Long,
        ): ResolvedPlaybackSource? = error("unexpected")

        override suspend fun resolveUsableDownload(itemId: String): DownloadItem? = error("unexpected")

        override suspend fun resolveLocalSource(itemId: String): ResolvedPlaybackSource.Local? {
            askedFor += itemId
            return local
        }

        override suspend fun resolveStartPositionTicks(itemId: String, explicitTicks: Long): Long =
            error("unexpected")
    }

    private data class StreamUrlCall(
        val itemId: String,
        val mediaSourceId: String,
        val startTimeTicks: Long,
        val maxBitrate: Int?,
        val useAudioEndpoint: Boolean,
        val liveStreamId: String?,
    )

    /**
     * Argument-recording [PlaybackRepository]: delegates the unused bulk of
     * the interface to the shared [FakePlaybackRepository] fixture (final —
     * composition, not subclassing) and overrides getStreamUrl to capture the
     * six arguments the resolver passes.
     */
    private class RecordingStreamUrlRepository(
        private val base: FakePlaybackRepository = FakePlaybackRepository(),
    ) : PlaybackRepository by base {
        val streamUrlCalls = mutableListOf<StreamUrlCall>()

        override fun getStreamUrl(
            itemId: String,
            mediaSourceId: String,
            startTimeTicks: Long,
            maxBitrate: Int?,
            useAudioEndpoint: Boolean,
            liveStreamId: String?,
        ): String {
            streamUrlCalls += StreamUrlCall(itemId, mediaSourceId, startTimeTicks, maxBitrate, useAudioEndpoint, liveStreamId)
            return "stream://$itemId/$mediaSourceId"
        }
    }

    /** Both bandwidth measurements inert — explicit quality tiers stay deterministic. */
    private fun newBitrateSelector() = AdaptiveBitrateSelector(BandwidthMonitor(), BandwidthInterceptor())

    private fun newResolver(
        detail: Result<MediaDetail>?,
        local: ResolvedPlaybackSource.Local?,
        quality: StreamingQuality,
        playbackRepository: PlaybackRepository = RecordingStreamUrlRepository(),
    ): DesktopAudioSourceResolver = DesktopAudioSourceResolver(
        mediaRepository = DetailMediaRepository(detail).also { detailRepo = it }.proxy,
        playbackRepository = playbackRepository,
        playbackSourceResolver = FakeLocalSource(local).also { localSource = it },
        adaptiveBitrateSelector = newBitrateSelector(),
        streamingQualityProvider = { quality },
    )

    private lateinit var detailRepo: DetailMediaRepository
    private lateinit var localSource: FakeLocalSource

    private fun streamRepo(repo: PlaybackRepository): RecordingStreamUrlRepository {
        assertTrue(repo is RecordingStreamUrlRepository)
        return repo as RecordingStreamUrlRepository
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun audioItem(
        id: String = "item1",
        name: String = "Song",
        runTimeTicks: Long? = 2_345_678_900L,
        playbackPositionTicks: Long? = 1_250_000L,
        normalizationGain: Float? = -7.5f,
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.AUDIO,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = playbackPositionTicks,
        normalizationGain = normalizationGain,
        albumArtist = "Album Artist",
        artistItems = listOf(NameGuidPair("Track Artist", "artist-2")),
        album = "The Album",
    )

    private fun detailOf(item: MediaItem, vararg sources: MediaSource) =
        Result.success(MediaDetail(item = item, mediaSources = sources.toList()))

    private fun downloadFor(path: String) = DownloadItem(
        id = "dl1",
        mediaItemId = "item1",
        name = "Local Song Title",
        mediaType = MediaType.AUDIO,
        downloadPath = path,
        downloadUrl = "http://server/item1/stream",
        totalSizeBytes = 1_000L,
        downloadedBytes = 1_000L,
        status = DownloadStatus.COMPLETED,
        mediaSourceId = "msrc-dl",
    )

    private fun localSourceFor(path: String): ResolvedPlaybackSource.Local =
        ResolvedPlaybackSource.Local(
            itemId = "item1",
            filePath = path,
            uri = "file:$path",
            title = "Local Song Title",
            download = downloadFor(path),
        )

    private fun tempFile(name: String): String =
        Files.createTempDirectory("jellyplay-resolver-test").also { tempDirs.add(it) }
            .resolve(name).toAbsolutePath().toString()

    // ── local-file path ───────────────────────────────────────────────────

    @Test
    fun usableDownloadWinsAndHandsMpvTheRawPath() = runTest {
        val filePath = tempFile("track.flac")
        val resolver = newResolver(
            detail = detailOf(audioItem()),
            local = localSourceFor(filePath),
            quality = StreamingQuality.HD_720P,
        )

        val track = resolver.resolve("item1", startPositionMs = 0L)

        assertTrue(track != null)
        assertTrue(track!!.isLocalFile, "a completed download must play locally")
        // V2b mpv pin: the RAW path, not Uri.fromFile's file:/ form.
        assertEquals(filePath, track.uri)
        assertFalse(track.uri.startsWith("file:"))
        // Item metadata comes from the DETAIL even though the local row wins.
        assertEquals("Song", track.title)
        assertEquals("Album Artist", track.artist)
        assertEquals("artist-2", track.artistId)
        assertEquals("The Album", track.album)
        // download.mediaSourceId, NOT the server source id.
        assertEquals("msrc-dl", track.mediaSourceId)
        assertEquals(234_567L, track.durationMs, "runTimeTicks / 10_000")
        assertEquals(-7.5f, track.normalizationGain)
        assertEquals(1_250_000L, track.resumePositionTicks)
        // Both probes ran (Android runs the same two asyncs unconditionally).
        assertEquals(listOf("item1"), detailRepo.requestedItemIds)
        assertEquals(listOf("item1"), localSource.askedFor)
        // …and the stream URL was never built.
        assertTrue(streamRepoNotUsed(track))
    }

    private fun streamRepoNotUsed(track: ResolvedAudioTrack): Boolean {
        // The playback repo fake only records via getStreamUrl; the local path
        // must not have triggered a URL build at all.
        return track.isLocalFile && !track.uri.startsWith("stream://")
    }

    @Test
    fun localFallsBackToLocalTitleWhenDetailFailed() = runTest {
        val filePath = tempFile("track.mp3")
        val resolver = newResolver(
            detail = null, // scripted FAILED detail fetch
            local = localSourceFor(filePath),
            quality = StreamingQuality.HD_720P,
        )

        val track = resolver.resolve("item1", startPositionMs = 0L)

        assertTrue(track != null)
        assertTrue(track!!.isLocalFile)
        // Queue-only local fallback: detail died, the local row's own title applies.
        assertEquals("Local Song Title", track.title)
        assertEquals(0L, track.durationMs, "no detail → no runtime metadata")
        assertNull(track.normalizationGain)
        assertNull(track.resumePositionTicks)
    }

    // ── stream-URL path ───────────────────────────────────────────────────

    @Test
    fun noDownloadStreamsViaTheSharedGetStreamUrlOverload() = runTest {
        val repo = RecordingStreamUrlRepository()
        val resolver = newResolver(
            detail = detailOf(audioItem(), MediaSource(id = "src1", name = "main")),
            local = null,
            quality = StreamingQuality.HD_720P,
            playbackRepository = repo,
        )

        val track = resolver.resolve("item1", startPositionMs = 1_500L)

        assertTrue(track != null)
        assertFalse(track!!.isLocalFile)
        assertEquals("stream://item1/src1", track.uri)
        assertEquals("Song", track.title)
        assertEquals("Album Artist", track.artist)
        assertEquals("The Album", track.album)
        assertEquals("src1", track.mediaSourceId)
        assertEquals(234_567L, track.durationMs)
        assertEquals(-7.5f, track.normalizationGain)
        assertEquals(1_250_000L, track.resumePositionTicks)

        // The exact Android-browser argument table, one call, nothing else:
        val call = repo.streamUrlCalls.single()
        assertEquals("item1", call.itemId)
        assertEquals("src1", call.mediaSourceId)
        assertEquals(15_000_000L, call.startTimeTicks, "1_500 ms × 10_000")
        // HD_720P → MEDIUM (192 kbps) × 1000 — the /Videos/static=true shape.
        assertEquals(AudioBitrateTier.MEDIUM.targetKbps * 1000, call.maxBitrate)
        assertFalse(call.useAudioEndpoint, "never the audio-endpoint URL form")
        assertNull(call.liveStreamId)
    }

    @Test
    fun zeroStartPositionSendsZeroStartTimeTicks() = runTest {
        val repo = RecordingStreamUrlRepository()
        val resolver = newResolver(
            detail = detailOf(audioItem(), MediaSource(id = "src1", name = "main")),
            local = null,
            quality = StreamingQuality.UHD_4K,
            playbackRepository = repo,
        )

        resolver.resolve("item1", startPositionMs = 0L)

        val call = repo.streamUrlCalls.single()
        assertEquals(0L, call.startTimeTicks, "position 0 must not request a mid-stream start")
        // UHD_4K → LOSSLESS (1411 kbps) × 1000.
        assertEquals(AudioBitrateTier.LOSSLESS.targetKbps * 1000, call.maxBitrate)
    }

    @Test
    fun detailWithoutMediaSourcesStillBuildsAUrlWithAnEmptySourceId() = runTest {
        val repo = RecordingStreamUrlRepository()
        val resolver = newResolver(
            detail = detailOf(audioItem()), // no mediaSources
            local = null,
            quality = StreamingQuality.LOW_360P,
            playbackRepository = repo,
        )

        val track = resolver.resolve("item1", startPositionMs = 0L)

        assertTrue(track != null)
        assertFalse(track!!.isLocalFile)
        val call = repo.streamUrlCalls.single()
        assertEquals("", call.mediaSourceId, "source?.id ?: '' — the same string Android passes")
        assertNull(track.mediaSourceId)
        assertEquals("stream://item1/", track.uri)
        assertEquals(AudioBitrateTier.LOW.targetKbps * 1000, call.maxBitrate)
    }

    // ── failure path ──────────────────────────────────────────────────────

    @Test
    fun detailFailureWithNoLocalYieldsNull() = runTest {
        val repo = RecordingStreamUrlRepository()
        val resolver = newResolver(
            detail = null,
            local = null,
            quality = StreamingQuality.HD_720P,
            playbackRepository = repo,
        )

        val track = resolver.resolve("item1", startPositionMs = 0L)

        assertNull(track, "the Android browser returns a null MediaItem here — 'Failed to load track'")
        assertTrue(repo.streamUrlCalls.isEmpty(), "no URL may be built for an undetailable item")
    }

    @Test
    fun detailFailureStillYieldsALocalTrackWhenADownloadExists() = runTest {
        val filePath = tempFile("track.wav")
        val resolver = newResolver(
            detail = null,
            local = localSourceFor(filePath),
            quality = StreamingQuality.HD_720P,
        )

        val track = resolver.resolve("item1", startPositionMs = 0L)

        assertTrue(track != null, "the local probe wins regardless of the detail outcome")
        assertTrue(track!!.isLocalFile)
        assertEquals(filePath, track.uri)
    }
}
