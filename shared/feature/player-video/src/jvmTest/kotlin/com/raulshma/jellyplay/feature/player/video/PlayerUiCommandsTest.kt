package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pref-write choreography pins for the session's [PlayerUiCommands]
 * (the VM's former private setters — the ViewModel suites keep driving them
 * through `onEvent`; this suite pins the ORDER and the snapshot rules the
 * funnel arms rely on):
 *
 *  1. `setPlaybackMode` writes the ui-prefs mirror SYNCHRONOUSLY, then
 *     persists, then reloads — one launch, in that order;
 *  2. the reload input uses the pre-write sibling snapshot: the quality the
 *     mode guard read from the SAME pre-write prefs snapshot, never read
 *     back after the mirror write;
 *  3. a no-op change (same mode) writes nothing at all.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerUiCommandsTest {

    /** Recording rig: the mirror lens over a local prefs var + a call log. */
    private class Rig(initialPrefs: PlayerUiPrefsState) {
        val calls = mutableListOf<String>()

        /** (mode, quality, selection) the reload seam received. */
        var reloaded: Triple<PlaybackMode, StreamingQuality, MediaStreamSelection?>? = null

        var prefs: PlayerUiPrefsState = initialPrefs

        val engineStore = mockk<PlayerEngineStore>(relaxed = true).apply {
            every { playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        }

        val commands = PlayerUiCommands(
            scope = CoroutineScope(Dispatchers.Unconfined),
            uiPrefs = { prefs },
            updateUiPrefs = { transform ->
                prefs = transform(prefs)
                calls += "mirror"
            },
            updateVideoFx = { },
            updateGestures = { },
            rememberBrightness = { false },
            isMuted = { false },
            setMuted = { },
            updateAutoplay = { },
            getMediaStreams = { emptyList() },
            playbackStore = mockk<PlaybackStore>(relaxed = true).apply {
                // The persist leg records through the store write itself (the
                // commands' persist lambda is internal).
                coEvery { setPlaybackMode(any()) } answers { this@Rig.calls += "persist" }
            },
            networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true),
            videoPlayerStore = mockk<VideoPlayerStore>(relaxed = true),
            engineStore = engineStore,
            rememberMutedEnabled = { false },
            getCurrentItemId = { null },
            getEngine = { null },
            onPlaybackModeChanged = { calls += "fallbackLatchReset" },
            reloadForMode = { mode, quality, selection ->
                calls += "reload"
                reloaded = Triple(mode, quality, selection)
            },
            markEngineConfigDirtyDebounced = { },
            setAutoplayEnabled = { },
            persistVideoEffects = { },
        )
    }

    @Test
    fun `setPlaybackMode writes the mirror synchronously then persists then reloads`() {
        val rig = Rig(PlayerUiPrefsState(playbackMode = PlaybackMode.AUTO, streamingQuality = StreamingQuality.HD_720P))

        rig.commands.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)

        assertEquals(
            listOf("fallbackLatchReset", "mirror", "persist", "reload"),
            rig.calls,
            "the choreography order is load-bearing: the explicit-change fallback latch reset " +
                "precedes everything, the mirror write moves the toggle immediately, the " +
                "persist + reload follow in one launch",
        )
    }

    @Test
    fun `the reload input uses the pre-write sibling snapshot`() {
        val rig = Rig(PlayerUiPrefsState(playbackMode = PlaybackMode.AUTO, streamingQuality = StreamingQuality.HD_720P))

        // Changing the MODE: the sibling QUALITY must resolve to the pre-write
        // snapshot's value even though the mirror write has already landed by
        // the time the reload runs.
        rig.commands.setPlaybackMode(PlaybackMode.FORCE_DIRECT_PLAY)

        assertEquals(
            Triple(PlaybackMode.FORCE_DIRECT_PLAY, StreamingQuality.HD_720P, null),
            rig.reloaded,
            "the reload must receive the caller's post-change mode + the PRE-WRITE sibling " +
                "quality — never a mirror read-back",
        )
        // And the mirror itself holds exactly the post-change pair.
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, rig.prefs.playbackMode)
        assertEquals(StreamingQuality.HD_720P, rig.prefs.streamingQuality)
    }

    @Test
    fun `a same-mode change is a full no-op`() {
        val rig = Rig(PlayerUiPrefsState(playbackMode = PlaybackMode.AUTO, streamingQuality = StreamingQuality.HD_720P))

        rig.commands.setPlaybackMode(PlaybackMode.AUTO)

        assertEquals(emptyList(), rig.calls, "a same-value change must not touch the mirror, " +
            "the fallback latch, the store or the reload")
    }
}
