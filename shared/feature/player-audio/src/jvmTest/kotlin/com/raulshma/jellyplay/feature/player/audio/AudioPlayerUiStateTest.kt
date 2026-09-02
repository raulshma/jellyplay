package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import com.raulshma.jellyplay.core.model.ReverbPreset
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the default/initial shape of [AudioPlayerUiState] and its nested
 * [Immutable] blocks ([AudioEffectsState], [LyricsState], [SleepTimerState],
 * [QueueState]).
 *
 * Two invariants matter beyond field values:
 *
 *  1. Nested-block identity — a fresh [AudioPlayerUiState] carries each nested
 *     block at its own default, and a `copy` that swaps one block leaves the
 *     others bit-identical (that atomicity is WHY the blocks exist: they pass
 *     whole to the sheet that owns them).
 *  2. High-frequency position lives OUTSIDE the state object — the 250 ms
 *     playback tick updates [AudioPlayerViewModel.currentPosition], not this
 *     class, so the tick recomposes narrowly instead of copying the whole
 *     state. If a `position`-like field ever leaks into [AudioPlayerUiState],
 *     that optimization silently dies — so it is pinned here by reflection.
 */
class AudioPlayerUiStateTest {

    // ── top-level defaults ─────────────────────────────────────────────

    @Test
    fun `top-level defaults are an empty resting player`() {
        val state = AudioPlayerUiState()
        assertEquals("", state.title)
        assertEquals("", state.artist)
        assertNull(state.artistId)
        assertEquals("", state.album)
        assertEquals("", state.albumArtUrl)
        assertNull(state.albumArtBlurHash)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.duration)
        assertEquals(1.0f, state.speed)
        assertFalse(state.isFavorite)
        assertNull(state.playbackError)
        assertFalse(state.isLoading)
        assertEquals(0L, state.crossfadeDurationMs)
    }

    @Test
    fun `playlist picker defaults are hidden and idle`() {
        val state = AudioPlayerUiState()
        assertFalse(state.showPlaylistPicker)
        assertTrue(state.playlists.isEmpty())
        assertFalse(state.isLoadingPlaylists)
        assertFalse(state.isAddingToPlaylist)
        assertNull(state.playlistMessage)
    }

    @Test
    fun `nested blocks default to their own defaults`() {
        val state = AudioPlayerUiState()
        assertEquals(AudioEffectsState(), state.effects)
        assertEquals(LyricsState(), state.lyrics)
        assertEquals(SleepTimerState(), state.sleepTimer)
        assertEquals(QueueState(), state.queue)
    }

    // ── AudioEffectsState defaults ─────────────────────────────────────

    @Test
    fun `effects defaults are everything off at neutral`() {
        val effects = AudioEffectsState()
        assertFalse(effects.equalizerEnabled)
        assertEquals(EqualizerPreset.FLAT, effects.equalizerPreset)
        assertFalse(effects.bassBoostEnabled)
        assertEquals(EffectStrength.MODERATE, effects.bassBoostStrength)
        assertFalse(effects.virtualizerEnabled)
        assertEquals(500, effects.virtualizerStrength)
        assertEquals(ReverbPreset.NONE, effects.reverbPreset)
        assertFalse(effects.dialogueBoostEnabled)
        assertFalse(effects.nightModeEnabled)
        assertEquals(0f, effects.lrBalance)
        assertEquals(0f, effects.pitchSemitones)
        assertFalse(effects.autoEqByGenre)
        assertEquals(AudioNormalizationMode.NONE, effects.normalizationMode)
        assertEquals(0f, effects.preAmpDb)
    }

    // ── LyricsState defaults + karaoke predicate ───────────────────────

    @Test
    fun `lyrics defaults are no lyrics not fetching unknown source`() {
        val lyrics = LyricsState()
        assertTrue(lyrics.lyrics.isEmpty())
        assertEquals(-1, lyrics.currentLyricIndex)
        assertEquals(LyricsSource.UNKNOWN, lyrics.lyricsSource)
        assertFalse(lyrics.isFetchingLyrics)
        assertEquals(
            AudioLyricsManager.DEFAULT_OFFSET_MS,
            lyrics.lyricsOffsetMs,
            "lyrics offset default tracks the historic fixed lead",
        )
        assertTrue(lyrics.searchResults.isEmpty())
        assertFalse(lyrics.isSearching)
        assertFalse(lyrics.karaokeMode)
    }

    @Test
    fun `hasKaraokeLyrics is false without lyrics or without word timings`() {
        assertFalse(LyricsState().hasKaraokeLyrics)
        assertFalse(
            LyricsState(lyrics = listOf(LyricsLine(timeMs = 0L, text = "plain line"))).hasKaraokeLyrics,
            "plain synced lines must not enable the karaoke view",
        )
    }

    @Test
    fun `hasKaraokeLyrics is true when any line carries word timings`() {
        val lyrics = LyricsState(
            lyrics = listOf(
                LyricsLine(timeMs = 0L, text = "no words here"),
                LyricsLine(
                    timeMs = 4_000L,
                    text = "word timed",
                    words = listOf(LyricsWord(timeMs = 4_000L, text = "word")),
                ),
            ),
        )
        assertTrue(lyrics.hasKaraokeLyrics)
    }

    // ── SleepTimerState + QueueState defaults ──────────────────────────

    @Test
    fun `sleep timer defaults to inactive`() {
        val timer = SleepTimerState()
        assertFalse(timer.active)
        assertFalse(timer.endOfEpisode)
        assertEquals(0L, timer.lastUsedDurationMs)
    }

    @Test
    fun `queue defaults to empty in-order non-repeating`() {
        val queue = QueueState()
        assertTrue(queue.queue.isEmpty())
        assertEquals(-1, queue.currentIndex)
        assertFalse(queue.shuffleMode)
        assertEquals(0, queue.repeatMode)
    }

    @Test
    fun `queue block with items tracks index without disturbing defaults`() {
        val item = AudioQueueItem(
            id = "track-1",
            name = "Song",
            artist = "Artist",
            album = "Album",
            imageUrl = null,
            mediaSourceId = "ms-1",
        )
        val queue = QueueState(queue = listOf(item), currentIndex = 0, shuffleMode = true)
        assertEquals(listOf(item), queue.queue)
        assertEquals(0, queue.currentIndex)
        assertTrue(queue.shuffleMode)
        assertEquals(0, queue.repeatMode, "repeat keeps its default unless set")
    }

    // ── nested-block identity (atomic update contract) ─────────────────

    @Test
    fun `swapping one nested block leaves the others intact`() {
        val state = AudioPlayerUiState(title = "Song A")
        val retimed = state.copy(sleepTimer = SleepTimerState(active = true, lastUsedDurationMs = 90_000L))
        assertEquals(SleepTimerState(active = true, lastUsedDurationMs = 90_000L), retimed.sleepTimer)
        assertEquals(state.effects, retimed.effects, "effects must not move with a sleep-timer update")
        assertEquals(state.lyrics, retimed.lyrics)
        assertEquals(state.queue, retimed.queue)
        assertEquals("Song A", retimed.title)
    }

    @Test
    fun `effects block passes whole to its sheet owner`() {
        // The effects sheet receives [AudioPlayerUiState.effects] directly,
        // decoupled from the ViewModel — a customized block must be value-
        // identical when read back off the state.
        val customized = AudioEffectsState(equalizerEnabled = true, preAmpDb = 3.5f)
        val state = AudioPlayerUiState(effects = customized)
        assertEquals(customized, state.effects)
        assertTrue(state.effects.equalizerEnabled)
    }

    // ── position lives OUTSIDE the UiState (high-frequency contract) ───

    @Test
    fun `no position field exists on the state object`() {
        val instanceFieldNames = AudioPlayerUiState::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
        val offenders = instanceFieldNames.filter {
            it.equals("position", ignoreCase = true) || it.equals("currentPosition", ignoreCase = true)
        }
        assertTrue(
            offenders.isEmpty(),
            "AudioPlayerUiState must not carry a position field " +
                "(the 250 ms tick updates currentPosition outside the state); found $offenders",
        )
    }
}
