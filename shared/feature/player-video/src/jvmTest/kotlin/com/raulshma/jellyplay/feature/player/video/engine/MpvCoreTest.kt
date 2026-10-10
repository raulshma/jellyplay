package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvBinding
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvCore
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvEndFileError
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeHost
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeValue
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvProperties
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleSideLoadPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM choreography pins for the shared [MpvCore] — the tests that
 * previously needed the libmpv parity suite (a real engine on a machine with
 * libmpv installed) now run EVERYWHERE against an in-memory scripted
 * [MpvBinding]: the mpv command/property write log IS the assertion surface.
 *
 * Covers the migration's ordering contracts: the property-intake cache
 * landings (including the two declared host divergences), the fold seeding
 * (FILE_LOADED from the live pause read, the eof re-derivation), the END_FILE
 * error-emission ordering + taxonomy hand-offs, the transport bodies (the
 * fixed-precision seek, the keep-open EOF restart, the track-selection
 * arms), the aspect plan, the side-load plan executions, the coalesced
 * refresh burst, the buffered-ranges decode, the cue accumulator's start
 * source, and the ownership refresh.
 */
class MpvCoreTest {

    // ── Fixture ─────────────────────────────────────────────────────────────

    /** Recorded write: (kind, property, value). */
    private data class Write(val kind: String, val name: String, val value: String)

    /**
     * In-memory scripted [MpvBinding]: records every command/property write
     * in order, serves reads from scriptable maps. The `track-id` write kind
     * marks [writeIntOrString]; a scripted `intWriteSucceeds = false` walks
     * the string fallback exactly like the real transports.
     */
    private class FakeMpvBinding : MpvBinding {
        val commands = mutableListOf<List<String>>()
        val writes = mutableListOf<Write>()
        val flagReads = mutableMapOf<String, Boolean>()
        val doubleReads = mutableMapOf<String, Double>()
        val nodeReads = mutableMapOf<String, Any?>()
        var alive = true
        var intWriteSucceeds = true

        override fun isAlive(): Boolean = alive

        override fun command(vararg args: String): Boolean {
            commands += args.toList()
            return true
        }

        override fun setOptionString(name: String, value: String) {
            writes += Write("option", name, value)
        }

        override fun setPropertyString(name: String, value: String) {
            writes += Write("string", name, value)
        }

        override fun setPropertyDouble(name: String, value: Double) {
            writes += Write("double", name, value.toString())
        }

        override fun setPropertyInt(name: String, value: Int) {
            writes += Write("int", name, value.toString())
        }

        override fun setPropertyBoolean(name: String, value: Boolean) {
            writes += Write("flag", name, value.toString())
        }

        override fun readFlag(name: String): Boolean = flagReads[name] ?: true

        override fun readDouble(name: String): Double? = doubleReads[name]

        override fun readString(name: String): String? = null

        override fun readNode(name: String): Any? = nodeReads[name]

        override fun writeIntOrString(name: String, value: Int): Boolean {
            writes += Write("track-id", name, value.toString())
            if (intWriteSucceeds) return true
            writes += Write("string", name, value.toString())
            return false
        }

        fun writesFor(name: String): List<Write> = writes.filter { it.name == name }
    }

    private class Fixture(
        val host: MpvIntakeHost = MpvIntakeHost.DESKTOP,
        val bufferedSink: MutableStateFlow<Long>? = null,
        val eagerSeek: Boolean = false,
        val cacheSpeed: Boolean = false,
        val queueBeforeLoad: Boolean = false,
        val cueStartSeconds: Double? = null,
    ) {
        val binding = FakeMpvBinding()
        val isPlaying = MutableStateFlow(false)
        val playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
        val currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
        val liveCue = MutableStateFlow<CharSequence?>(null)
        val bufferedRanges = MutableStateFlow<List<LongRange>>(emptyList())
        val availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
        val errors = mutableListOf<EngineError>()
        val transportErrors = mutableListOf<String>()
        val scheduledRefreshes = mutableListOf<Pair<String, Long>>()
        val eventLog = mutableListOf<String>()
        var publishedLists = 0
        val unsalvageableReports = mutableListOf<Set<String>>()

        val hosts = object : MpvCore.Hosts() {
            override fun onTransportError(op: String, error: Throwable) {
                transportErrors += op
            }

            override fun scheduleTrackRefresh(reason: String, delayMs: Long) {
                scheduledRefreshes += reason to delayMs
            }

            override fun onTracksPublished(tracks: List<MediaTrack>, reason: String) {
                publishedLists++
                eventLog += "published:$reason"
            }

            override fun onStartFile() {
                eventLog += "start-file-hook"
            }

            override fun onBeforeFileLoaded() {
                eventLog += "file-loaded-hook"
            }

            override fun onUnsalvageableConfKeys(keys: Set<String>) {
                unsalvageableReports += keys
            }

            override fun subStartSeconds(): Double? = cueStartSeconds

            override fun emitSubAdd(add: MpvSubtitleSideLoadPlan.SubAdd): Boolean {
                binding.commands += listOf(MpvProperties.CMD_SUB_ADD, add.source.url, add.flags, add.label)
                return true
            }
        }

        val core = MpvCore(
            host = host,
            binding = binding,
            isPlayingFlow = isPlaying,
            playbackStateFlow = playbackState,
            currentCuesSink = currentCues,
            liveSubtitleCueSink = liveCue,
            bufferedRangesSink = bufferedRanges,
            availableTracksSink = availableTracks,
            errorSink = { errors += it },
            bufferedSink = bufferedSink,
            scopeProvider = { CoroutineScope(SupervisorJob() + Dispatchers.Default) },
            hosts = hosts,
            eagerSeekPositionCache = eagerSeek,
            cacheSpeedPropertyWrites = cacheSpeed,
            queueSubtitlesBeforeFileLoaded = queueBeforeLoad,
        )
    }

    private fun source(label: String, id: String = "external:$label", language: String? = "en") = SubtitleSource(
        url = "https://example.test/$label.srt",
        label = label,
        language = language,
        mimeType = "application/x-subrip",
        id = id,
    )

    // ── Property intake + cached fields ─────────────────────────────────────

    @Test
    fun timePosIntakeLandsInCachedPosition() {
        val f = Fixture()
        f.core.onPropertyChange(MpvProperties.TIME_POS, MpvIntakeValue.Decimal(1.5))
        assertEquals(1500L, f.core.cachedPositionMs)
    }

    @Test
    fun durationIntakeClampsOnAndroidAndPassesThroughOnDesktop() {
        val android = Fixture(host = MpvIntakeHost.ANDROID)
        android.core.onPropertyChange(MpvProperties.DURATION, MpvIntakeValue.Decimal(-5.0))
        assertEquals(0L, android.core.cachedDurationMs, "the shipped Android clamp")

        val desktop = Fixture(host = MpvIntakeHost.DESKTOP)
        desktop.core.onPropertyChange(MpvProperties.DURATION, MpvIntakeValue.Decimal(-5.0))
        assertEquals(-5000L, desktop.core.cachedDurationMs, "the shipped desktop pass-through")
    }

    @Test
    fun bufferedScalarLandsAtObserverCadenceOnlyWhenASinkIsProvided() {
        val desktop = Fixture(bufferedSink = MutableStateFlow(0L))
        desktop.core.onPropertyChange(MpvProperties.DEMUXER_CACHE_TIME, MpvIntakeValue.Whole(2))
        assertEquals(2000L, desktop.core.cachedBufferedPositionMs)
        assertEquals(2000L, desktop.bufferedSink!!.value, "desktop publishes at observer cadence")

        val android = Fixture(host = MpvIntakeHost.ANDROID)
        android.core.onPropertyChange(MpvProperties.DEMUXER_CACHE_TIME, MpvIntakeValue.Whole(2))
        assertEquals(2000L, android.core.cachedBufferedPositionMs)
        assertEquals(0, android.availableTracks.value.size) // no flow was touched
    }

    @Test
    fun demuxerCacheDurationIsTheAndroidOnlyRelativeBufferedRow() {
        val android = Fixture(host = MpvIntakeHost.ANDROID)
        android.core.onPropertyChange(MpvProperties.TIME_POS, MpvIntakeValue.Decimal(4.0))
        android.core.onPropertyChange(MpvProperties.DEMUXER_CACHE_DURATION, MpvIntakeValue.Decimal(2.5))
        assertEquals(6500L, android.core.cachedBufferedPositionMs, "position + cache-duration (Android formula)")

        val desktop = Fixture(host = MpvIntakeHost.DESKTOP)
        desktop.core.onPropertyChange(MpvProperties.DEMUXER_CACHE_DURATION, MpvIntakeValue.Decimal(2.5))
        assertEquals(0L, desktop.core.cachedBufferedPositionMs, "the desktop does not observe this property")
    }

    @Test
    fun speedIntakeCachesOnlyForTheDesktopHost() {
        val desktop = Fixture()
        desktop.core.onPropertyChange(MpvProperties.SPEED, MpvIntakeValue.Decimal(1.25))
        assertEquals(1.25f, desktop.core.cachedSpeed)

        val android = Fixture(host = MpvIntakeHost.ANDROID)
        android.core.onPropertyChange(MpvProperties.SPEED, MpvIntakeValue.Decimal(1.25))
        assertEquals(1f, android.core.cachedSpeed, "Android registers the observation and drops it")
    }

    @Test
    fun channelLayoutRowFiresOnlyForTheDesktopHost() {
        var rebuilds = 0
        val binding = FakeMpvBinding()
        val hosts = object : MpvCore.Hosts() {
            override fun onChannelLayoutChanged(count: Int) {
                rebuilds++
            }
        }
        val desktop = MpvCore(
            host = MpvIntakeHost.DESKTOP,
            binding = binding,
            isPlayingFlow = MutableStateFlow(false),
            playbackStateFlow = MutableStateFlow(EnginePlaybackState.IDLE),
            currentCuesSink = MutableStateFlow(emptyList()),
            liveSubtitleCueSink = MutableStateFlow(null),
            bufferedRangesSink = MutableStateFlow(emptyList()),
            availableTracksSink = MutableStateFlow(emptyList()),
            errorSink = {},
            bufferedSink = null,
            scopeProvider = { CoroutineScope(SupervisorJob() + Dispatchers.Default) },
            hosts = hosts,
        )
        desktop.onPropertyChange(MpvProperties.AUDIO_PARAMS_CHANNEL_COUNT, MpvIntakeValue.Whole(6))
        assertEquals(6, desktop.observedChannelCount)
        assertEquals(1, rebuilds, "the desktop rebuilds its af chain against the new layout")
        desktop.onPropertyChange(MpvProperties.AUDIO_PARAMS_CHANNEL_COUNT, MpvIntakeValue.Whole(6))
        assertEquals(1, rebuilds, "an unchanged layout is the row's change-guarded no-op")

        val android = Fixture(host = MpvIntakeHost.ANDROID)
        android.core.onPropertyChange(MpvProperties.AUDIO_PARAMS_CHANNEL_COUNT, MpvIntakeValue.Whole(6))
        assertEquals(null, android.core.observedChannelCount, "the Android host never dispatches this row")
    }

    // ── Fold seeding ────────────────────────────────────────────────────────

    @Test
    fun fileLoadedSeedsReadyAndIsPlayingFromTheLivePauseFlag() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded()
        assertEquals(EnginePlaybackState.READY, f.playbackState.value)
        assertTrue(f.isPlaying.value)
        assertTrue(f.core.fileLoaded)
        assertEquals(listOf("file-loaded-hook"), f.eventLog, "host extras run BEFORE the fold")
    }

    @Test
    fun fileLoadedSeedsPausedWhenTheCoreAutoPlaysButTheFlagIsSet() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = true
        f.core.onFileLoaded()
        assertEquals(EnginePlaybackState.READY, f.playbackState.value)
        assertFalse(f.isPlaying.value)
    }

    @Test
    fun eofReachedFoldsTheKeepOpenParkAndTheReplaySeekBack() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded()
        assertTrue(f.isPlaying.value)

        f.core.onPropertyChange(MpvProperties.EOF_REACHED, MpvIntakeValue.Flag(true), livePaused = true)
        assertEquals(EnginePlaybackState.ENDED, f.playbackState.value)
        assertFalse(f.isPlaying.value)

        // The eof=false flip re-derives isPlaying from the LIVE pause read —
        // `pause` itself did not change, so its observer will not fire.
        f.core.onPropertyChange(MpvProperties.EOF_REACHED, MpvIntakeValue.Flag(false), livePaused = false)
        assertEquals(EnginePlaybackState.READY, f.playbackState.value)
        assertTrue(f.isPlaying.value)
    }

    @Test
    fun startFileRunsHostExtrasBeforeTheFold() {
        val f = Fixture()
        f.core.onStartFile()
        assertEquals(listOf("start-file-hook"), f.eventLog)
        assertEquals(EnginePlaybackState.BUFFERING, f.playbackState.value)
    }

    // ── END_FILE + taxonomy ─────────────────────────────────────────────────

    @Test
    fun endFileErrorEmitsThroughTheStringTaxonomyHandoff() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded()
        f.core.onEndFile(3, MpvEndFileError.StringCode("-13"))
        assertEquals(EnginePlaybackState.ERROR, f.playbackState.value)
        assertEquals(listOf<EngineError>(EngineError.Network(cause = null)), f.errors)
    }

    @Test
    fun endFileErrorEmitsThroughTheIntTaxonomyHandoff() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded()
        f.core.onEndFile(3, MpvEndFileError.IntCode(-14, "ao_init_failed"))
        assertEquals(EnginePlaybackState.ERROR, f.playbackState.value)
        assertEquals(listOf<EngineError>(EngineError.Decoder(codec = null, cause = null)), f.errors)
    }

    @Test
    fun stopEndFileParksAtIdleWithoutAnError() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded()
        f.core.onEndFile(1, MpvEndFileError.StringCode(null))
        assertEquals(EnginePlaybackState.IDLE, f.playbackState.value)
        assertTrue(f.errors.isEmpty(), "explicit user action never surfaces an error")
    }

    // ── Transport ───────────────────────────────────────────────────────────

    @Test
    fun seekToIssuesTheFixedPrecisionAbsoluteSeek() {
        val eager = Fixture(eagerSeek = true)
        eager.core.seekTo(90_500)
        assertEquals(listOf(listOf("seek", "90.500000", "absolute")), eager.binding.commands)
        assertEquals(90_500L, eager.core.cachedPositionMs, "the desktop's eager write-through")

        val lazy = Fixture()
        lazy.core.onPropertyChange(MpvProperties.TIME_POS, MpvIntakeValue.Decimal(3.0))
        lazy.core.seekTo(90_500)
        assertEquals(3000L, lazy.core.cachedPositionMs, "Android waits for the time-pos observer")
    }

    @Test
    fun transportOpsAreNoOpsOnADeadBinding() {
        val f = Fixture()
        f.binding.alive = false
        f.core.play()
        f.core.pause()
        f.core.commandStop()
        f.core.seekTo(1_000)
        f.core.setPlaybackSpeed(2f)
        f.core.selectTrack(TrackType.AUDIO, 1)
        assertTrue(f.binding.commands.isEmpty())
        assertTrue(f.binding.writes.isEmpty())
        assertTrue(f.transportErrors.isEmpty())
    }

    @Test
    fun playRestartsAKeepOpenEofBeforeUnpausing() {
        val f = Fixture()
        f.playbackState.value = EnginePlaybackState.ENDED
        f.core.play()
        // The EOF seek is a command; the unpause is the absorbing flag write.
        assertEquals(listOf("seek", "0", "absolute"), f.binding.commands.single())
        assertEquals(Write("flag", MpvProperties.PAUSE, "false"), f.binding.writes.single())
    }

    @Test
    fun playMidFileSkipsTheSeekAndOnlyUnpauses() {
        val f = Fixture()
        f.playbackState.value = EnginePlaybackState.READY
        f.core.play()
        assertTrue(f.binding.commands.isEmpty())
        assertEquals(Write("flag", MpvProperties.PAUSE, "false"), f.binding.writes.single())
    }

    @Test
    fun pauseWritesThePauseFlag() {
        val f = Fixture()
        f.core.pause()
        assertEquals(Write("flag", MpvProperties.PAUSE, "true"), f.binding.writes.single())
    }

    @Test
    fun commandStopIssuesTheBareStopCommand() {
        val f = Fixture()
        f.core.commandStop()
        assertEquals(listOf("stop"), f.binding.commands.single())
    }

    @Test
    fun speedWritesThePropertyAndCachesOnlyWhenWriteThroughIsOn() {
        val caching = Fixture(cacheSpeed = true)
        caching.core.setPlaybackSpeed(1.5f)
        assertEquals(Write("double", MpvProperties.SPEED, "1.5"), caching.binding.writes.single())
        assertEquals(1.5f, caching.core.cachedSpeed)

        val liveReading = Fixture()
        liveReading.core.setPlaybackSpeed(1.5f)
        assertEquals(1f, liveReading.core.cachedSpeed, "Android's getter live-reads the property")
    }

    // ── Track selection ─────────────────────────────────────────────────────

    @Test
    fun selectTrackAudioNegativeDeselectsToTheAutoHeuristic() {
        val f = Fixture()
        f.core.selectTrack(TrackType.AUDIO, -1)
        assertEquals(Write("string", MpvProperties.AID, "auto"), f.binding.writes.single())
    }

    @Test
    fun selectTrackAudioPositivePrefersTheTypedWriteWithStringFallback() {
        val typed = Fixture()
        typed.core.selectTrack(TrackType.AUDIO, 2)
        assertEquals(Write("track-id", MpvProperties.AID, "2"), typed.binding.writes.single())

        val fallback = Fixture()
        fallback.binding.intWriteSucceeds = false
        fallback.core.selectTrack(TrackType.AUDIO, 2)
        assertEquals(
            listOf(Write("track-id", MpvProperties.AID, "2"), Write("string", MpvProperties.AID, "2")),
            fallback.binding.writes,
        )
    }

    @Test
    fun selectTrackSubtitlePositiveReEnablesVisibilityAndNegativeDoesNot() {
        val select = Fixture()
        select.core.selectTrack(TrackType.SUBTITLE, 3)
        assertEquals(
            listOf(
                Write("track-id", MpvProperties.SID, "3"),
                Write("flag", MpvProperties.SUB_VISIBILITY, "true"),
            ),
            select.binding.writes,
        )

        val deselect = Fixture()
        deselect.core.selectTrack(TrackType.SUBTITLE, -1)
        assertEquals(listOf(Write("string", MpvProperties.SID, "no")), deselect.binding.writes)
    }

    @Test
    fun secondarySubtitleTrackWritesTheClearedOrTypedId() {
        val f = Fixture()
        f.core.setSecondarySubtitleTrack(-1)
        assertEquals(Write("string", MpvProperties.SECONDARY_SID, "no"), f.binding.writes.single())
        f.core.setSecondarySubtitleTrack(5)
        assertEquals(Write("track-id", MpvProperties.SECONDARY_SID, "5"), f.binding.writes.last())
    }

    // ── Aspect / visibility ─────────────────────────────────────────────────

    @Test
    fun setAspectRatioAppliesTheFullSharedPlanInOrder() {
        val f = Fixture()
        f.core.setAspectRatio(AspectRatio.CROP)
        assertEquals(
            listOf(
                Write("string", MpvProperties.VIDEO_ASPECT_OVERRIDE, "-1"),
                Write("double", MpvProperties.PANSCAN, "1.0"),
                Write("string", MpvProperties.SUB_USE_MARGINS, "yes"),
                Write("string", MpvProperties.SUB_ASS_FORCE_MARGINS, "yes"),
            ),
            f.binding.writes,
        )
        f.binding.writes.clear()
        f.core.setAspectRatio(AspectRatio.FILL)
        assertEquals(
            listOf(
                Write("string", MpvProperties.VIDEO_ASPECT_OVERRIDE, "-1"),
                Write("double", MpvProperties.PANSCAN, "0.0"),
                Write("string", MpvProperties.SUB_USE_MARGINS, "no"),
                Write("string", MpvProperties.SUB_ASS_FORCE_MARGINS, "no"),
            ),
            f.binding.writes,
        )
    }

    @Test
    fun setNativeSubtitlesVisibleWritesTheYesNoSpelling() {
        val f = Fixture()
        f.core.setNativeSubtitlesVisible(true)
        f.core.setNativeSubtitlesVisible(false)
        assertEquals(
            listOf(
                Write("string", MpvProperties.SUB_VISIBILITY, "yes"),
                Write("string", MpvProperties.SUB_VISIBILITY, "no"),
            ),
            f.binding.writes,
        )
    }

    // ── Load / item lifecycle ───────────────────────────────────────────────

    @Test
    fun beginLoadResetsThePerItemState() {
        val f = Fixture()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded()
        f.core.cachedPositionMs = 5_000
        f.core.serverDurationMs = 9_000
        f.core.released = true
        val request = PlaybackRequest(
            uri = "https://example.test/item.m3u8",
            title = "item",
            startPositionMs = 1_000,
            externalSubtitles = listOf(source("s1")),
            serverDurationMs = 654_321,
        )
        f.core.beginLoad(request)
        assertFalse(f.core.released)
        assertFalse(f.core.fileLoaded, "fresh fold latches")
        assertEquals(listOf(source("s1")), f.core.pendingSubtitles)
        assertTrue(f.core.sideLoadedSubtitleIds.isEmpty())
        assertEquals(654_321L, f.core.serverDurationMs)
        assertEquals(654_321L, f.core.effectiveDurationMs, "the server rung serves until the demuxer resolves")
        assertTrue(f.currentCues.value.isEmpty())
        assertEquals(null, f.liveCue.value)
        f.core.resetPlaybackCaches(positionMs = request.startPositionMs)
        assertEquals(1_000L, f.core.cachedPositionMs)
        assertEquals(0L, f.core.cachedDurationMs)
        assertEquals(0L, f.core.cachedBufferedPositionMs)
        assertEquals(-1.0, f.core.cachedSubStartSec)
    }

    @Test
    fun effectiveDurationPrefersTheDemuxerRung() {
        val f = Fixture()
        f.core.serverDurationMs = 9_000
        f.core.cachedDurationMs = 5_000
        assertEquals(5_000L, f.core.effectiveDurationMs)
        f.core.cachedDurationMs = 0L
        assertEquals(9_000L, f.core.effectiveDurationMs)
    }

    // ── Side-loaded subtitles ───────────────────────────────────────────────

    @Test
    fun flushPendingSubtitlesPlansTheBatchAndPreSeedsTheRegistry() {
        val f = Fixture()
        f.binding.nodeReads[MpvProperties.TRACK_LIST] = emptyList<Any?>()
        f.core.beginLoad(
            PlaybackRequest(
                uri = "https://example.test/item.mkv",
                title = "batch",
                externalSubtitles = listOf(source("s1"), source("s2")),
            ),
        )
        f.core.flushPendingSubtitles()
        assertEquals(2, f.binding.commands.size, "both planned adds execute")
        assertTrue(f.binding.commands.all { it.first() == MpvProperties.CMD_SUB_ADD })
        assertEquals(
            mapOf("s1" to "external:s1", "s2" to "external:s2"),
            f.core.sideLoadedSubtitleIds,
            "the registry store-back precedes the track enumeration",
        )
        assertTrue(f.core.pendingSubtitles.isEmpty())
    }

    @Test
    fun flushPendingSubtitlesDedupesAgainstTheLiveTrackList() {
        val f = Fixture()
        // A live "sub" row already titled s1 — the raw-title dedupe key.
        f.binding.nodeReads[MpvProperties.TRACK_LIST] = listOf(
            mapOf("id" to 1L, "type" to "sub", "title" to "s1", "selected" to true),
        )
        f.core.beginLoad(
            PlaybackRequest(
                uri = "https://example.test/item.mkv",
                title = "dedupe",
                externalSubtitles = listOf(source("s1")),
            ),
        )
        f.core.flushPendingSubtitles()
        assertTrue(f.binding.commands.isEmpty(), "a true re-add is skipped")
    }

    @Test
    fun addExternalSubtitleQueuesBeforeFileLoadedWhenGated() {
        val f = Fixture(queueBeforeLoad = true)
        f.core.addExternalSubtitle(source("early"))
        assertTrue(f.binding.commands.isEmpty())
        assertEquals(listOf(source("early")), f.core.pendingSubtitles)
    }

    @Test
    fun addExternalSubtitleDeliversAndSkipsTrueReAdds() {
        val f = Fixture(queueBeforeLoad = true)
        f.binding.nodeReads[MpvProperties.TRACK_LIST] = emptyList<Any?>()
        f.binding.flagReads[MpvProperties.PAUSE] = false
        f.core.onFileLoaded() // the gate opens

        f.core.addExternalSubtitle(source("s1"))
        assertEquals(1, f.binding.commands.size, "the planned add delivers")
        val registry = f.core.sideLoadedSubtitleIds
        assertEquals("external:s1", registry["s1"])

        f.core.addExternalSubtitle(source("s1"))
        assertEquals(1, f.binding.commands.size, "a double-tap re-add is skipped")
    }

    // ── Tracks ──────────────────────────────────────────────────────────────

    @Test
    fun delayedRefreshSlotsRouteToTheHostAndImmediatesCollapseIntoOneRead() = runTest {
        val scope = this
        val binding = FakeMpvBinding()
        var published = 0
        val scheduled = mutableListOf<Pair<String, Long>>()
        val hosts = object : MpvCore.Hosts() {
            override fun scheduleTrackRefresh(reason: String, delayMs: Long) {
                scheduled += reason to delayMs
            }

            override fun onTracksPublished(tracks: List<MediaTrack>, reason: String) {
                published++
            }
        }
        val core = MpvCore(
            host = MpvIntakeHost.ANDROID,
            binding = binding,
            isPlayingFlow = MutableStateFlow(false),
            playbackStateFlow = MutableStateFlow(EnginePlaybackState.IDLE),
            currentCuesSink = MutableStateFlow(emptyList()),
            liveSubtitleCueSink = MutableStateFlow(null),
            bufferedRangesSink = MutableStateFlow(emptyList()),
            availableTracksSink = MutableStateFlow(emptyList()),
            errorSink = {},
            bufferedSink = null,
            scopeProvider = { scope },
            hosts = hosts,
        )
        binding.nodeReads[MpvProperties.TRACK_LIST] = listOf(mapOf("id" to 1L, "type" to "audio"))

        core.refreshTracks("start-file", delayMs = 200)
        core.refreshTracks("start-file-late", delayMs = 800)
        assertEquals(
            listOf("start-file" to 200L, "start-file-late" to 800L),
            scheduled,
            "delayed refreshes keep their own host slot",
        )

        // The observer burst collapses into ONE debounced rebuild.
        core.onPropertyChange(MpvProperties.TRACK_LIST, MpvIntakeValue.Node)
        core.onPropertyChange(MpvProperties.SID, MpvIntakeValue.Unread)
        core.onPropertyChange(MpvProperties.AID, MpvIntakeValue.Unread)
        advanceTimeBy(TRACK_REFRESH_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(0, published, "the burst has not settled yet")

        core.onPropertyChange(MpvProperties.TRACK_LIST, MpvIntakeValue.Node)
        advanceTimeBy(TRACK_REFRESH_DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(1, published, "the coalesced body fires exactly once for the burst")
    }

    @Test
    fun refreshIsGatedByReleased() = runTest {
        val scope = this
        val binding = FakeMpvBinding()
        var published = 0
        val scheduled = mutableListOf<String>()
        val hosts = object : MpvCore.Hosts() {
            override fun scheduleTrackRefresh(reason: String, delayMs: Long) {
                scheduled += reason
            }
        }
        val core = MpvCore(
            host = MpvIntakeHost.DESKTOP,
            binding = binding,
            isPlayingFlow = MutableStateFlow(false),
            playbackStateFlow = MutableStateFlow(EnginePlaybackState.IDLE),
            currentCuesSink = MutableStateFlow(emptyList()),
            liveSubtitleCueSink = MutableStateFlow(null),
            bufferedRangesSink = MutableStateFlow(emptyList()),
            availableTracksSink = MutableStateFlow(emptyList()),
            errorSink = {},
            bufferedSink = null,
            scopeProvider = { scope },
            hosts = hosts,
        )
        core.released = true
        core.refreshTracks("coalesced")
        core.refreshTracks("start-file", delayMs = 200)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(scheduled.isEmpty())
        assertEquals(0, published)
    }

    // ── Buffered ranges ─────────────────────────────────────────────────────

    @Test
    fun decodeBufferedRangesTakesSeekableRangesDirectly() {
        val f = Fixture()
        f.core.serverDurationMs = 60_000
        f.core.decodeBufferedRanges(
            mapOf(
                "seekable-ranges" to listOf(
                    mapOf("start" to 0.0, "end" to 10.0),
                    mapOf("start" to 20.0, "end" to 30.0),
                ),
                "demuxer-start-time" to 0.0,
                "cache-end" to 30.0,
            ),
        )
        assertEquals(listOf(0L..10_000L, 20_000L..30_000L), f.bufferedRanges.value)
    }

    @Test
    fun decodeBufferedRangesFallsBackToTheContiguousWindow() {
        val f = Fixture()
        f.core.decodeBufferedRanges(
            mapOf("demuxer-start-time" to 2.0, "cache-end" to 12.5),
        )
        assertEquals(listOf(2_000L..12_500L), f.bufferedRanges.value)
    }

    @Test
    fun decodeBufferedRangesIgnoresNonMapPayloads() {
        val f = Fixture()
        f.core.decodeBufferedRanges(null)
        f.core.decodeBufferedRanges("not a map")
        assertTrue(f.bufferedRanges.value.isEmpty())
    }

    // ── Cue accumulation (G10) ──────────────────────────────────────────────

    @Test
    fun accumulateSubTextUsesTheHostStartSourceAndSkipsBlankClears() {
        val cached = Fixture(host = MpvIntakeHost.ANDROID, cueStartSeconds = 5.0)
        cached.core.onPropertyChange(MpvProperties.TIME_POS, MpvIntakeValue.Decimal(30.0))
        cached.core.accumulateSubText("First line")
        assertEquals(1, cached.currentCues.value.size)
        assertEquals(5_000_000L, cached.currentCues.value.single().startTimeUs, "the host's cue-start source wins")
        assertEquals("First line", cached.currentCues.value.single().text)
        cached.core.accumulateSubText("   ")
        assertEquals(1, cached.currentCues.value.size, "blank clears are ignored")

        val fallback = Fixture(host = MpvIntakeHost.ANDROID, cueStartSeconds = null)
        fallback.core.onPropertyChange(MpvProperties.TIME_POS, MpvIntakeValue.Decimal(7.5))
        fallback.core.accumulateSubText("Second")
        assertEquals(7_500_000L, fallback.currentCues.value.single().startTimeUs, "position fallback when no sub-start")
    }

    // ── Ownership (issue #165) ──────────────────────────────────────────────

    @Test
    fun ownershipRefreshPopulatesTheSnapshotAndReportsUnsalvageableKeys() {
        val f = Fixture()
        f.core.refreshUserOwnedSubtitleKeys(
            confText = "sub-color=#FF0000\nsub-font-size=80\n",
            extraConfigText = "sub-shadow=1.5\n",
        )
        assertTrue(MpvProperties.SUB_FONT_SIZE in f.core.ownedStyleKeys)
        assertTrue("sub-color" in f.core.ownedStyleKeys)
        assertTrue("sub-shadow" in f.core.ownedStyleKeys, "the extra-config text is an ownership source too")
        assertEquals(listOf(setOf("sub-color")), f.unsalvageableReports, "the unquoted-# trap is reported")

        // The desktop shape: no conf text, no report.
        val d = Fixture()
        d.core.refreshUserOwnedSubtitleKeys(null, "sub-pos=40\n")
        assertTrue(d.unsalvageableReports.isEmpty())
        assertTrue("sub-pos" in d.core.ownedStyleKeys)
    }

    // ── Style funnel ────────────────────────────────────────────────────────

    @Test
    fun styleRuntimeAppliesThroughTheSurfaceAndFiresTheHostExtra() {
        val f = Fixture()
        var styled: SubtitleStyle? = null
        val hosts = object : MpvCore.Hosts() {
            override fun onStyleApplied(style: SubtitleStyle) {
                styled = style
            }
        }
        val core = MpvCore(
            host = MpvIntakeHost.ANDROID,
            binding = f.binding,
            isPlayingFlow = MutableStateFlow(false),
            playbackStateFlow = MutableStateFlow(EnginePlaybackState.IDLE),
            currentCuesSink = MutableStateFlow(emptyList()),
            liveSubtitleCueSink = MutableStateFlow(null),
            bufferedRangesSink = MutableStateFlow(emptyList()),
            availableTracksSink = MutableStateFlow(emptyList()),
            errorSink = {},
            bufferedSink = null,
            scopeProvider = { CoroutineScope(SupervisorJob() + Dispatchers.Default) },
            hosts = hosts,
        )
        val style = SubtitleStyle()
        core.applySubtitleStyleRuntime(style)
        assertTrue(f.binding.writes.isNotEmpty(), "the applier wrote the sub-* table")
        assertEquals(style, styled, "the host extra fires after the writes")
    }
}
