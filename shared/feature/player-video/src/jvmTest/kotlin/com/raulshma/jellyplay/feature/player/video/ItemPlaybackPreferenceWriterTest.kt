package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests for [ItemPlaybackPreferenceWriter] — the single WRITE side of the
 * per-item/series playback preferences. Pins the three invariants the former
 * five hand-copied VM blocks encoded (and could drift apart on):
 * null means FORGET (the explicit clear, never a value-preserving save), the
 * scope-key derivation per policy (SERIES-only vs SERIES-then-ITEM), and the
 * resolver refresh firing after every completed write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemPlaybackPreferenceWriterTest {

    /** Every repository invocation the writer may issue, recorded for asserts. */
    private sealed interface RepoCall {
        data class Save(
            val scope: PlaybackPrefScope,
            val key: String,
            val audioLanguage: String?,
            val subtitleLanguage: String?,
            val subtitleForced: Boolean?,
            val subtitleHearingImpaired: Boolean?,
            val dialogueBoostStrength: EffectStrength?,
        ) : RepoCall

        data class ClearAudioLanguage(val scope: PlaybackPrefScope, val key: String) : RepoCall
        data class ClearSubtitleLanguage(val scope: PlaybackPrefScope, val key: String) : RepoCall
        data class SetSubtitleDisabled(val scope: PlaybackPrefScope, val key: String, val disabled: Boolean) : RepoCall
        data class ClearDialogueBoost(val scope: PlaybackPrefScope, val key: String) : RepoCall
        data class SaveRememberedTrack(
            val scope: PlaybackPrefScope,
            val key: String,
            val type: TrackType,
            val track: RememberedTrack?,
        ) : RepoCall
    }

    private class FakeRepository : ItemPlaybackPreferenceRepository {
        val calls = mutableListOf<RepoCall>()

        override suspend fun get(scope: PlaybackPrefScope, key: String): ItemPlaybackPreference? = null

        override suspend fun save(
            scope: PlaybackPrefScope,
            key: String,
            audioLanguage: String?,
            subtitleLanguage: String?,
            subtitleForced: Boolean?,
            subtitleHearingImpaired: Boolean?,
            dialogueBoostStrength: EffectStrength?,
        ) {
            calls += RepoCall.Save(scope, key, audioLanguage, subtitleLanguage, subtitleForced, subtitleHearingImpaired, dialogueBoostStrength)
        }

        override suspend fun clearAudioLanguage(scope: PlaybackPrefScope, key: String) {
            calls += RepoCall.ClearAudioLanguage(scope, key)
        }

        override suspend fun clearSubtitleLanguage(scope: PlaybackPrefScope, key: String) {
            calls += RepoCall.ClearSubtitleLanguage(scope, key)
        }

        override suspend fun setSubtitleDisabled(scope: PlaybackPrefScope, key: String, disabled: Boolean) {
            calls += RepoCall.SetSubtitleDisabled(scope, key, disabled)
        }

        override suspend fun clearDialogueBoostStrength(scope: PlaybackPrefScope, key: String) {
            calls += RepoCall.ClearDialogueBoost(scope, key)
        }

        override suspend fun saveRememberedTrack(
            scope: PlaybackPrefScope,
            key: String,
            type: TrackType,
            track: RememberedTrack?,
        ) {
            calls += RepoCall.SaveRememberedTrack(scope, key, type, track)
        }

        override suspend fun delete(scope: PlaybackPrefScope, key: String) = Unit
    }

    private lateinit var repository: FakeRepository
    private var seriesId: String? = null
    private var itemId: String? = null
    private var refreshCount: Int = 0
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var writer: ItemPlaybackPreferenceWriter

    @BeforeTest
    fun setUp() {
        repository = FakeRepository()
        seriesId = null
        itemId = null
        refreshCount = 0

        writer = ItemPlaybackPreferenceWriter(
            repository = repository,
            getCurrentSeriesId = { seriesId },
            getCurrentItemId = { itemId },
            scope = testScope,
            onPreferencesChanged = { refreshCount++ },
        )
    }

    // ─── null means FORGET — the explicit clear, never a preserving save ──────

    @Test
    fun setSeriesAudioLanguage_null_issuesExplicitClear_neverSave() = testScope.runTest {
        seriesId = "series1"

        writer.setSeriesAudioLanguage(null)

        assertEquals(
            listOf<RepoCall>(RepoCall.ClearAudioLanguage(PlaybackPrefScope.SERIES, "series1")),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun setSeriesAudioLanguage_value_saves() = testScope.runTest {
        seriesId = "series1"

        writer.setSeriesAudioLanguage("jpn")

        assertEquals(
            listOf<RepoCall>(
                RepoCall.Save(PlaybackPrefScope.SERIES, "series1", audioLanguage = "jpn", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null),
            ),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun setSeriesSubtitlePreference_null_issuesExplicitClear() = testScope.runTest {
        seriesId = "series1"

        writer.setSeriesSubtitlePreference(language = null, forced = true, hearingImpaired = false)

        assertEquals(
            listOf<RepoCall>(RepoCall.ClearSubtitleLanguage(PlaybackPrefScope.SERIES, "series1")),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun setSeriesSubtitlePreference_value_savesWithRolePins() = testScope.runTest {
        seriesId = "series1"

        writer.setSeriesSubtitlePreference(language = "eng", forced = true, hearingImpaired = false)

        assertEquals(
            listOf<RepoCall>(
                RepoCall.Save(PlaybackPrefScope.SERIES, "series1", audioLanguage = null, subtitleLanguage = "eng", subtitleForced = true, subtitleHearingImpaired = false, dialogueBoostStrength = null),
            ),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun setDialogueBoostStrength_none_issuesExplicitClear() = testScope.runTest {
        seriesId = "series1"

        writer.setDialogueBoostStrength(EffectStrength.NONE)

        assertEquals(
            listOf<RepoCall>(RepoCall.ClearDialogueBoost(PlaybackPrefScope.SERIES, "series1")),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun setDialogueBoostStrength_value_saves() = testScope.runTest {
        seriesId = "series1"

        writer.setDialogueBoostStrength(EffectStrength.MODERATE)

        assertEquals(
            listOf<RepoCall>(
                RepoCall.Save(PlaybackPrefScope.SERIES, "series1", audioLanguage = null, subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = EffectStrength.MODERATE),
            ),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    // ─── Scope-key derivation: both policies, declared per command ────────────

    @Test
    fun dialogueBoost_seriesScopeWinsOverItem() = testScope.runTest {
        seriesId = "series1"
        itemId = "item1"

        writer.setDialogueBoostStrength(EffectStrength.LOW)

        assertEquals(PlaybackPrefScope.SERIES, (repository.calls.single() as RepoCall.Save).scope)
        assertEquals("series1", (repository.calls.single() as RepoCall.Save).key)
    }

    @Test
    fun dialogueBoost_fallsBackToItemScopeWhenNoSeries() = testScope.runTest {
        itemId = "item1"

        writer.setDialogueBoostStrength(EffectStrength.LOW)

        assertEquals(
            listOf<RepoCall>(
                RepoCall.Save(PlaybackPrefScope.ITEM, "item1", audioLanguage = null, subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = EffectStrength.LOW),
            ),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun dialogueBoost_noKeysAtAll_noops() = testScope.runTest {
        writer.setDialogueBoostStrength(EffectStrength.LOW)

        assertTrue(repository.calls.isEmpty())
        assertEquals(0, refreshCount)
    }

    @Test
    fun seriesOnlyCommands_noopWithoutASeries() = testScope.runTest {
        itemId = "item1" // SERIES_ONLY must NOT fall back to the item scope

        writer.setSeriesAudioLanguage("eng")
        writer.setSeriesSubtitlePreference("eng", null, null)
        writer.setSeriesSubtitleDisabled(true)
        writer.rememberTrack(TrackType.AUDIO, RememberedTrack(label = "English", language = "eng", indexWithinLanguage = 0))

        assertTrue(repository.calls.isEmpty())
        assertEquals(0, refreshCount)
    }

    @Test
    fun setSeriesSubtitleDisabled_writesSeriesScope() = testScope.runTest {
        seriesId = "series1"

        writer.setSeriesSubtitleDisabled(true)

        assertEquals(
            listOf<RepoCall>(RepoCall.SetSubtitleDisabled(PlaybackPrefScope.SERIES, "series1", true)),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    // ─── Remembered track (G5) — SERIES-only persistence ──────────────────────

    @Test
    fun rememberTrack_savesSeriesScopeAndRefreshes() = testScope.runTest {
        seriesId = "series1"
        val remembered = RememberedTrack(label = "Spanish", language = "spa", indexWithinLanguage = 1)

        writer.rememberTrack(TrackType.SUBTITLE, remembered)

        assertEquals(
            listOf<RepoCall>(RepoCall.SaveRememberedTrack(PlaybackPrefScope.SERIES, "series1", TrackType.SUBTITLE, remembered)),
            repository.calls,
        )
        assertEquals(1, refreshCount)
    }

    // ─── The resolver refresh fires after EVERY completed write ───────────────

    @Test
    fun refresh_firesAfterEveryWrite_inSequence() = testScope.runTest {
        seriesId = "series1"

        writer.setSeriesAudioLanguage("eng")
        writer.setSeriesSubtitleDisabled(false)
        writer.setSeriesAudioLanguage(null)

        assertEquals(3, repository.calls.size)
        assertEquals(3, refreshCount)
    }
}
