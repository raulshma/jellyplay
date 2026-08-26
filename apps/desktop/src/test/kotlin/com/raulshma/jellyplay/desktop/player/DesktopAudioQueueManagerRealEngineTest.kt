package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import java.io.File
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The V2b-precedent real-engine slice, one level up the stack: the FULL audio
 * path through [DesktopAudioQueueManager] driving an actual `MpvDesktopEngine`
 * (`vo=null` like the production audio factory plus `ao=null` so CI machines
 * without an audio device still initialize mpv — production itself keeps the
 * real audio output and passes only `vo=null`)
 * across generated short WAV fixtures — no display, no network.
 *
 * What this buys beyond the fake-engine suite: it empirically proves that a
 * keep-open EOF parks the core WITHOUT leaving the engine paused, so the very
 * next `loadfile replace` auto-plays — i.e., desktop auto-advance has
 * ExoPlayer's post-STATE_ENDED behavior without compensating code in the
 * manager. If mpv ever changes that internal contract, THIS test fails first,
 * pointing straight at the advance path instead of a mysteriously silent queue.
 *
 * Skips on machines without libmpv (`jna.library.path` / `MPV_LIBRARY` /
 * system install), like [MpvDesktopEngineTest].
 */
class DesktopAudioQueueManagerRealEngineTest {

    private var cleanupDir: File? = null

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    @AfterTest
    fun tearDown() {
        cleanupDir?.deleteRecursively()
    }

    @Test
    fun realEngineAutoAdvancesAcrossTwoWavsAndParksAtEndOfQueue() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-audio-e2e-${System.nanoTime()}")
            .apply { mkdirs() }
            .also { cleanupDir = it }
        // 3 s fixtures — NOT arbitrary. The manager's position ticker runs the
        // exact Android cadence: while the engine is NOT playing it rechecks
        // every POSITION_PAUSED_RECHECK_MS (2.5 s). With sub-second WAVs the
        // ticker's first wake lands after BOTH tracks already ended, so
        // _currentPosition/_duration legitimately never sample anything and
        // stop(prev) has no ticks to report — correct-at-parity behavior for
        // degenerate media, fatal for the assertion below. Real music is
        // minutes long; 3 s per fixture guarantees the ticker catches live
        // data inside track A's window, exactly like production.
        val wavA = writeTestWav(File(dir, "a.wav"), seconds = 3.0)
        val wavB = writeTestWav(File(dir, "b.wav"), seconds = 3.0)

        val executor = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        val repo = FakePlaybackRepository()
        val manager = DesktopAudioQueueManager(
            trackResolver = FakeResolver().apply {
                tracks["a"] = resolvedTrack("a", uri = wavA.absolutePath)
                tracks["b"] = resolvedTrack("b", uri = wavB.absolutePath)
            },
            playbackRepository = repo,
            imageUrlProvider = FakeImages(),
            queuePersistenceHelper = QueuePersistenceHelper(InMemoryQueueDao()),
            lyricsManager = AudioLyricsManager(FakeLyricsRepository()),
            sleepTimerManager = SleepTimerManager(TestTimeSource()),
            scope = scope,
            // Production parity: the same engine shape desktopPlayerModule
            // uses (audio-only MpvDesktopEngine), plus ao=null so headless CI
            // without an audio device still reaches mpv_initialize.
            engineFactory = { MpvDesktopEngine(extraOptions = mapOf("vo" to "null", "ao" to "null")) },
            mainThreadGuard = false,
        )
        try {
            manager.start()
            manager.playQueue(queueOf(wavA, wavB), startIndex = 0)

            // Track A plays through its natural EOF and the queue AUTO-ADVANCES
            // to B with no user action — the media3 mid-queue transition shape,
            // proven against the real mpv keep-open engine.
            pollUntil("auto-advanced onto track b", timeoutMs = 25_000) {
                manager.currentPlayingItemId.value == "b" && manager.currentIndex.value == 1
            }
            pollUntil("ticker sampled live position", timeoutMs = 10_000) {
                manager.currentPosition.value > 0 && manager.duration.value > 0
            }
            pollUntil("stop(a) reported on session rotation", timeoutMs = 10_000) {
                repo.stops.any { it.first == "a" && it.third > 0 }
            }
            pollUntil("start(b) reported with the rotated session", timeoutMs = 10_000) {
                repo.starts.any { it.itemId == "b" } &&
                    repo.stops.firstOrNull()?.second != repo.starts.last { it.itemId == "b" }.sessionId
            }

            // End of queue under RepeatNone: cursor stays parked on the last
            // row, isPlaying flips off, metadata kept (STATE_ENDED parity).
            pollUntil("end-of-queue park (isPlaying off after b)", timeoutMs = 15_000) {
                !manager.isPlaying.value && manager.currentIndex.value == 1
            }
            assertEquals(1, manager.currentIndex.value, "cursor parked on the ended item")
            assertFalse(manager.isPlaying.value)
            assertEquals("b", manager.currentPlayingItemId.value, "metadata kept")
            assertEquals("ms-b", repo.starts.last { it.itemId == "b" }.mediaSourceId)
        } finally {
            manager.stopAndRelease()
            scope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun realEngineKeepsPausedAcrossASkipReloadLikeExoPlayWhenReady() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-audio-pause-${System.nanoTime()}")
            .apply { mkdirs() }
            .also { cleanupDir = it }
        // Long-enough A to pause inside; B only needs to load.
        val wavA = writeTestWav(File(dir, "a.wav"), seconds = 10.0)
        val wavB = writeTestWav(File(dir, "b.wav"), seconds = 3.0)

        val executor = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        val manager = DesktopAudioQueueManager(
            trackResolver = FakeResolver().apply {
                tracks["a"] = resolvedTrack("a", uri = wavA.absolutePath)
                tracks["b"] = resolvedTrack("b", uri = wavB.absolutePath)
            },
            playbackRepository = FakePlaybackRepository(),
            imageUrlProvider = FakeImages(),
            queuePersistenceHelper = QueuePersistenceHelper(InMemoryQueueDao()),
            lyricsManager = AudioLyricsManager(FakeLyricsRepository()),
            sleepTimerManager = SleepTimerManager(TestTimeSource()),
            scope = scope,
            engineFactory = { MpvDesktopEngine(extraOptions = mapOf("vo" to "null", "ao" to "null")) },
            mainThreadGuard = false,
        )
        try {
            manager.start()
            manager.playQueue(queueOf(wavA, wavB), startIndex = 0)
            pollUntil("track a playing", timeoutMs = 20_000) { manager.isPlaying.value }

            // Pause, then skip: ExoPlayer carries playWhenReady=false into the
            // next item — desktop parity rides mpv's `pause` property
            // persisting across `loadfile replace` (the pinned divergence
            // bullet). B must sit loaded but PAUSED and stay paused by itself.
            manager.pause()
            pollUntil("pause settled before the skip", timeoutMs = 5_000) { !manager.isPlaying.value }
            manager.skipToNext()
            pollUntil("skipped onto track b", timeoutMs = 15_000) {
                manager.currentPlayingItemId.value == "b" && manager.currentIndex.value == 1
            }
            Thread.sleep(2_500) // give an auto-play regression time to surface
            assertFalse(manager.isPlaying.value, "B must stay paused after a skip from paused")
        } finally {
            manager.stopAndRelease()
            scope.cancel()
            executor.shutdownNow()
        }
    }
}

/** Two-item queue whose ids match the test resolver seeds. */
private fun queueOf(wavA: File, wavB: File): List<AudioQueueItem> = listOf(
    AudioQueueItem(id = "a", name = "Track a", artist = "Artist a", album = null, imageUrl = null, mediaSourceId = "ms-a", durationMs = 3_000L),
    AudioQueueItem(id = "b", name = "Track b", artist = "Artist b", album = null, imageUrl = null, mediaSourceId = "ms-b", durationMs = 3_000L),
)

/**
 * Generates a minimal 16-bit mono PCM WAV of [seconds] length (440 Hz sine).
 */
private fun writeTestWav(file: File, seconds: Double): File {
    val sampleRate = 22_050
    val sampleCount = (sampleRate * seconds).toInt()
    val dataSize = sampleCount * 2
    file.outputStream().use { out ->
        fun leInt(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }

        fun leShort(value: Short) {
            out.write(value.toInt() and 0xFF)
            out.write((value.toInt() shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray()); leInt(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); leInt(16)
        leShort(1); leShort(1); leInt(sampleRate); leInt(sampleRate * 2); leShort(2); leShort(16)
        out.write("data".toByteArray()); leInt(dataSize)
        repeat(sampleCount) { i ->
            val sample = (12_000 * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toInt()
            leShort(sample.toShort())
        }
    }
    check(file.length() > 44L) { "wav fixture missing content: $file" }
    return file
}
