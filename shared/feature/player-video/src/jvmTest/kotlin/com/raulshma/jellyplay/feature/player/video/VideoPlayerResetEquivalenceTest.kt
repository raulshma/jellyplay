package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SubtitleStyle
import io.mockk.coEvery
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.test.Test

/**
 * Golden characterization test for the item-switch reset semantics — the
 * pre-KMP suite reinstated against the KMP VM (construction in
 * [VideoPlayerViewModelTestHarness]; the command funs became
 * [VideoPlayerUiEvent]s, the store bundle became [PlayerStores]). Drives one
 * scripted session mutating every migrated slice (sleep timer, audio
 * effects, dialogue boost, subtitle search, A/B repeat, SyncPlay group
 * join) plus residual probes (subtitle style persists; playback speed /
 * stats reset), triggers the item-switch path (`Initialize` with a new item
 * id, which routes through `releaseInternals()`), and snapshots the outcome
 * leaf-by-leaf against the golden below (a leaf is a flat UiState field
 * today, or a `slice.sub` path once a slice data class migrates into the
 * constructor — see [assertResidualPartition]).
 *
 * **Intended divergence — exactly one:** the A/B
 * repeat window now RESETS on item switch. Before, the reset ritual
 * wiped the UiState mirror of the controller's `AbRepeatState` but never
 * reset the controller's own copy, so after an episode switch the loop
 * monitor could seek the *next* episode back to the *previous* episode's A
 * point, and one tap on the toggle resurrected the stale points (the
 * divergence bug the change fixes; see `AbRepeatControllerTest`).
 *
 * A second, benign nuance: `audioDelayMs` previously dropped to 0 for the
 * instant between the reset and the new engine binding (it was never
 * whitelisted); with ownership it now carries across that instant and is
 * re-seeded from the persisted preference when the engine binds (the
 * engineFlow collector's `seedFromPreferences`), converging to the same
 * value. With no engine bound in this harness, the golden pins the carried
 * value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerResetEquivalenceTest : VideoPlayerViewModelHarness() {

    /**
     * The residual reset whitelist — the only UiState leaves carried across
     * an item switch.
     *
     * Entries are LEAF PATHS, not flat field names: a flat constructor
     * property is named plainly (`"brightnessLevel"`); once a slice data
     * class (state/ package) is stored in the UiState constructor, its
     * sub-fields are named `"<sliceField>.<subField>"`. When a slice
     * migration moves flat fields into a stored slice, rewrite the affected
     * entries here IN THE SAME COMMIT — the stale-path guard in
     * [assertResidualPartition] fails with the exact missing names otherwise.
     */
    private val residualWhitelist = setOf(
        "preferredPlayerType",
        "uiPrefs.defaultOrientation", "uiPrefs.controlsTimeoutMs",
        "uiPrefs.showPlaybackMetadata", "uiPrefs.showClock",
        "uiPrefs.showTimeRemaining", "uiPrefs.keepScreenOnDuringVideo",
        "gestures.seekDurationMs", "gestures.gestureMode", "gestures.defaultSpeed",
        "gestures.swipeSeekMaxMs", "gestures.rememberBrightness", "gestures.brightnessLevel",
        "segmentState.segmentBehaviors", "episodes.videoEpisodeBrowserEnabled",
        "videoFx.tvZoomModePercent", "subtitleStyle",
    )

    /**
     * Leaves the *load* coroutine legitimately re-populates after the reset
     * (the loading screen lifts in the `finally` even when the detail fetch
     * fails; the session's play-method string is re-resolved during load and
     * the autoplay-next pref default differs from the constructor default),
     * so "reset to default" does not hold for them at snapshot time.
     * Same leaf-path convention as [residualWhitelist].
     */
    // `title` rides the load too: the item-2 detail fetch is stubbed to
    // fail in this harness, and the session's failLoad writes the localized
    // error title AFTER the reset ritual (the KMP pipeline's failure
    // surfacing, #146) — a load-time write, not a reset leak.
    private val loadRepopulated = setOf("isInitializing", "title", "media.playMethod", "autoplay.videoAutoplayNext")

    /**
     * Leaves the reset re-sets to explicit (non-default) values: the per-item
     * dialogue boost zeroes so it can't bleed into the next item before the
     * resolver re-applies the per-item rule (strength NONE, not the MODERATE
     * constructor default).
     */
    private val explicitlyReset = mapOf(
        "dialogueBoostEnabled" to false,
        "dialogueBoostStrength" to EffectStrength.NONE,
    )

    private fun driveSessionMutations() {
        // ── Sleep slice: running timed timer + last-used duration ──
        viewModel.playbackSession.sleepTimer.startSleepTimer(15_000L)

        // ── Audio-effects slice: every user effect to a non-default value ──
        viewModel.playbackSession.effects.toggleNightMode()
        viewModel.playbackSession.effects.setNightModeStrength(EffectStrength.HIGH)
        viewModel.playbackSession.effects.setDecoderMode(DecoderMode.SW_ONLY)
        viewModel.playbackSession.effects.setAudioPassthrough(true)
        viewModel.playbackSession.effects.setAudioNormalizationMode(AudioNormalizationMode.TRACK)
        viewModel.playbackSession.effects.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)
        viewModel.playbackSession.effects.toggleBassBoost()
        viewModel.playbackSession.effects.setBassBoostStrength(EffectStrength.HIGH)
        viewModel.playbackSession.effects.toggleVirtualizer()
        viewModel.playbackSession.effects.setVirtualizerStrength(750)
        viewModel.playbackSession.effects.setReverbPreset(ReverbPreset.LARGE_HALL)
        viewModel.playbackSession.effects.setAudioDelay(250L)

        // ── Dialogue boost (residual UiState, per-item resolver-driven) ──
        viewModel.onEvent(VideoPlayerUiEvent.SetDialogueBoostStrength(EffectStrength.HIGH))

        // ── Subtitle-workflow slice: a completed search + remote list ──
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(
            listOf(RemoteSubtitleInfo(id = "s1", name = "English"))
        )
        viewModel.playbackSession.subtitles.loadRemoteSubtitles()
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.success(
            listOf(RemoteSubtitleInfo(id = "os1", name = "OpenSub en"))
        )
        viewModel.playbackSession.subtitles.searchRemoteSubtitles("eng")

        // ── A/B repeat: armed window ──
        viewModel.playbackSession.abRepeat.setEnabled(true)
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(1_000L))
        viewModel.playbackSession.abRepeat.setPointA()
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(5_000L))
        viewModel.playbackSession.abRepeat.setPointB()

        // ── SyncPlay group display: joined group ──
        viewModel.playbackSession.syncPlay.joinGroup("group-1")

        // ── Residual probes: whitelisted (persists) vs not (resets) ──
        viewModel.onEvent(VideoPlayerUiEvent.SetSubtitleStyle(SubtitleStyle(fontSize = 40))) // whitelisted
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(2.0f))                          // not whitelisted
        viewModel.onEvent(VideoPlayerUiEvent.ToggleVideoStats)                                // not whitelisted
    }

    @Test
    fun itemSwitch_goldenSnapshot() {
        setExternalPlayer()
        coEvery { mediaRepository.getMediaDetail("item-2") } returns Result.failure(RuntimeException("no detail"))
        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-1"))
        driveSessionMutations()

        val before = viewModel.uiState.value

        // Sanity: the session mutations actually landed.
        assertTrue(before.dialogueBoostEnabled)
        assertEquals(EffectStrength.HIGH, before.dialogueBoostStrength)
        assertEquals(2.0f, before.playbackSpeed, 0.001f)
        assertTrue(before.uiPrefs.showVideoStats)
        assertEquals(40, before.subtitleStyle.fontSize)
        assertTrue(viewModel.playbackSession.sleepTimer.state.value.sleepTimerActive)
        assertTrue(viewModel.playbackSession.effects.state.value.nightModeEnabled)
        assertEquals(250L, viewModel.playbackSession.effects.state.value.audioDelayMs)
        assertTrue(viewModel.playbackSession.subtitles.state.value.hasSearchedSubtitles)
        assertEquals(1, viewModel.playbackSession.subtitles.state.value.remoteSubtitles.size)
        assertTrue(viewModel.playbackSession.abRepeat.state.value.isActive)
        assertTrue(viewModel.playbackSession.syncPlay.state.value.isInSyncPlaySession)
        assertEquals("group-1", viewModel.playbackSession.syncPlay.state.value.syncPlayGroupName)

        // ── The item switch (routes through releaseInternals) ──
        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-2"))

        // ── Golden: controller-owned slices ──

        // Sleep timer deliberately PERSISTS.
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SleepTimerState(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = false,
                sleepTimerLastUsedDurationMs = 15_000L,
            ),
            viewModel.playbackSession.sleepTimer.state.value,
        )

        // User audio effects PERSIST. audioDelayMs nuance: see class KDoc.
        val effects = viewModel.playbackSession.effects.state.value
        assertTrue(effects.nightModeEnabled)
        assertEquals(EffectStrength.HIGH, effects.nightModeStrength)
        assertEquals(DecoderMode.SW_ONLY, effects.decoderMode)
        assertTrue(effects.audioPassthrough)
        assertEquals(AudioNormalizationMode.TRACK, effects.audioNormalizationMode)
        assertTrue(effects.audioNormalizationEnabled)
        assertEquals(ChannelMixMode.SURROUND_UPMIX, effects.channelMixMode)
        assertTrue(effects.channelMixEnabled)
        assertTrue(effects.bassBoostEnabled)
        assertEquals(EffectStrength.HIGH, effects.bassBoostStrength)
        assertTrue(effects.virtualizerEnabled)
        assertEquals(750, effects.virtualizerStrength)
        assertEquals(ReverbPreset.LARGE_HALL, effects.reverbPreset)
        assertEquals(250L, effects.audioDelayMs)

        // Subtitle workflow RESETS (never whitelisted).
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SubtitleState(),
            viewModel.playbackSession.subtitles.state.value,
        )

        // SyncPlay group display RESETS (never whitelisted) — the mirror into
        // the residual UiState follows the bridge's state.
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState(),
            viewModel.playbackSession.syncPlay.state.value,
        )
        assertFalse(viewModel.uiState.value.isInSyncPlaySession)

        // THE ONE INTENDED DIVERGENCE (bug fix): the A/B repeat
        // window resets on item switch. Before, the reset wiped only the
        // UiState mirror while the controller kept its stale points — the
        // loop monitor could seek the next episode back to the previous
        // episode's A point, and one tap resurrected them. Now the single
        // home is cleared.
        assertEquals(AbRepeatState(), viewModel.playbackSession.abRepeat.state.value)

        // Dialogue boost (residual, per-item resolver-driven) resets so it
        // can't bleed into the next item before the resolver re-applies.
        assertFalse(viewModel.uiState.value.dialogueBoostEnabled)
        assertEquals(EffectStrength.NONE, viewModel.uiState.value.dialogueBoostStrength)

        // Residual probes.
        assertEquals(40, viewModel.uiState.value.subtitleStyle.fontSize) // whitelisted → persists
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed, 0.001f) // not whitelisted → resets
        assertFalse(viewModel.uiState.value.uiPrefs.showVideoStats)      // not whitelisted → resets

        // ── Golden: residual UiState partition (leaf-by-leaf) ──
        assertResidualPartition(before)
    }

    /**
     * The whitelist-equivalence proof: every UiState leaf either carries its
     * pre-switch value ([residualWhitelist]) or resets to the default (modulo
     * [loadRepopulated]). Adding a leaf to UiState or moving one between homes
     * without updating this partition fails here — the diff IS the review
     * artifact.
     *
     * Java reflection (not kotlin-reflect, which is not a test dependency):
     * a data class's constructor parameters are its declared backing fields.
     * The enumeration is slice-aware and DUAL-MODE, so it passes against the
     * flat UiState of today AND against the sliced UiState after each
     * migration PR — a declared field whose type lives in the state/ package
     * expands into one leaf per slice constructor property, named
     * `"<sliceField>.<subField>"` (recursed ONE level — slices have no
     * nested slices).
     */
    private fun assertResidualPartition(before: VideoPlayerUiState) {
        val after = viewModel.uiState.value
        val defaults = VideoPlayerUiState()
        val leaves = uiStateLeaves()
        assertTrue(
            "expected a substantial leaf set (flat + slice-expanded), got ${leaves.size}",
            leaves.size >= 30,
        )

        // Partition hygiene: every partition entry must name a real leaf, so
        // a slice migration that forgets to rewrite its paths fails HERE
        // first, with the stale names spelled out.
        val leafPaths = leaves.mapTo(mutableSetOf()) { it.path }
        val stalePartitionEntries =
            (residualWhitelist + loadRepopulated + explicitlyReset.keys) - leafPaths
        assertTrue(
            "partition entries match no leaf (stale flat names after a slice migration?): $stalePartitionEntries",
            stalePartitionEntries.isEmpty(),
        )

        for (leaf in leaves) {
            val path = leaf.path
            val afterVal = leaf.read(after)
            val beforeVal = leaf.read(before)
            val defaultVal = leaf.read(defaults)
            when {
                path in residualWhitelist ->
                    assertEquals("whitelisted field $path must persist", beforeVal, afterVal)
                path in loadRepopulated -> Unit // re-populated by the load, not the reset
                path in explicitlyReset ->
                    assertEquals("explicitly re-set field $path", explicitlyReset[path], afterVal)
                else ->
                    assertEquals("non-whitelisted field $path must reset to default", defaultVal, afterVal)
            }
        }
    }

    /**
     * Package of the slice data classes (state/). A declared UiState
     * constructor field whose type lives here expands into `slice.sub`
     * leaves instead of being treated as one opaque leaf.
     */
    private val stateSlicePackage = "com.raulshma.jellyplay.feature.player.video.state"

    private fun uiStateLeaves(): List<UiStateLeaf> =
        constructorPropertyFields(VideoPlayerUiState::class.java).flatMap { field ->
            if (field.type.name.startsWith("$stateSlicePackage.")) {
                constructorPropertyFields(field.type).map { subField ->
                    UiStateLeaf(
                        path = "${field.name}.${subField.name}",
                        sliceField = field,
                        leafField = subField,
                    )
                }
            } else {
                listOf(UiStateLeaf(path = field.name, sliceField = null, leafField = field))
            }
        }.sortedBy { it.path }

    private fun constructorPropertyFields(type: Class<*>): List<Field> =
        type.declaredFields
            .filter { !it.name.startsWith("$") && !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }

    private class UiStateLeaf(
        val path: String,
        private val sliceField: Field?,
        private val leafField: Field,
    ) {
        fun read(state: VideoPlayerUiState): Any? =
            if (sliceField == null) {
                leafField.get(state)
            } else {
                sliceField.get(state)?.let(leafField::get)
            }
    }

    /**
     * The A/B-repeat bug fix, VM-level: after an item switch the window is
     * gone AND cannot be resurrected by a toggle tap. (The "no seekTo past
     * the old B" half is pinned at the controller level in
     * AbRepeatControllerTest — this harness binds no engine.)
     */
    @Test
    fun itemSwitch_stopsAbRepeatLoop_andTapCannotResurrect() {
        setExternalPlayer()
        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-1"))
        viewModel.playbackSession.abRepeat.setEnabled(true)
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(1_000L))
        viewModel.playbackSession.abRepeat.setPointA()
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(5_000L))
        viewModel.playbackSession.abRepeat.setPointB()
        assertTrue(viewModel.playbackSession.abRepeat.state.value.isActive)

        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-2"))

        // Window cleared…
        assertNull(viewModel.playbackSession.abRepeat.state.value.aMs)
        assertNull(viewModel.playbackSession.abRepeat.state.value.bMs)
        assertFalse(viewModel.playbackSession.abRepeat.state.value.isActive)

        // …and a single toggle tap does NOT resurrect the previous episode's
        // points (previous behaviour: the stale mirror came back alive).
        viewModel.playbackSession.abRepeat.setEnabled(true)
        assertNull(viewModel.playbackSession.abRepeat.state.value.aMs)
        assertNull(viewModel.playbackSession.abRepeat.state.value.bMs)
        assertFalse(viewModel.playbackSession.abRepeat.state.value.isActive)
    }
}
