package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exercises [ItemPlaybackPreferenceRepositoryImpl] against a real in-memory
 * Room database. The load-bearing invariants are the read-merge-write rules:
 *  - `save` treats a null argument as "leave untouched", never as "clear";
 *  - a row that ends up carrying no preference is dropped entirely
 *    (`get` returns null → "inherit global");
 *  - subtitle language and the explicit "subtitles off" intent are mutually
 *    exclusive on one row;
 *  - ITEM and SERIES scopes are isolated per key.
 */
class ItemPlaybackPreferenceRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: ItemPlaybackPreferenceRepositoryImpl

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = ItemPlaybackPreferenceRepositoryImpl(
            dao = database.itemPlaybackPreferenceDao(),
            database = database,
            timeSource = FakeTimeSource(),
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    // ── Upsert + read-back per item ─────────────────────────────────────

    @Test
    fun `save then get round-trips the full preference for one item`() = runTest {
        repository.save(
            scope = PlaybackPrefScope.ITEM,
            key = "item-1",
            audioLanguage = "ger",
            subtitleLanguage = "eng",
            subtitleForced = true,
            subtitleHearingImpaired = false,
            dialogueBoostStrength = EffectStrength.MODERATE,
        )

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!

        assertEquals(PlaybackPrefScope.ITEM, pref.scope)
        assertEquals("item-1", pref.key)
        assertEquals("ger", pref.audioLanguage)
        assertEquals("eng", pref.subtitleLanguage)
        assertEquals(true, pref.subtitleForced)
        assertEquals(false, pref.subtitleHearingImpaired)
        assertEquals(EffectStrength.MODERATE, pref.dialogueBoostStrength)
        assertTrue(pref.updatedAt > 0)
    }

    @Test
    fun `get on an unknown key returns null`() = runTest {
        assertNull(repository.get(PlaybackPrefScope.ITEM, "missing"))
    }

    @Test
    fun `scopes are isolated - same key in ITEM and SERIES holds separate rows`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "k", audioLanguage = "ger", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)
        repository.save(PlaybackPrefScope.SERIES, "k", audioLanguage = "fre", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        assertEquals("ger", repository.get(PlaybackPrefScope.ITEM, "k")!!.audioLanguage)
        assertEquals("fre", repository.get(PlaybackPrefScope.SERIES, "k")!!.audioLanguage)
    }

    @Test
    fun `save merges null args onto the existing row instead of clearing`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger", subtitleLanguage = "eng", subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = EffectStrength.LOW)

        // All-null fields: "leave untouched".
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = null, subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertEquals("ger", pref.audioLanguage)
        assertEquals("eng", pref.subtitleLanguage)
        assertEquals(EffectStrength.LOW, pref.dialogueBoostStrength)
    }

    @Test
    fun `pinning a subtitle language clears a prior disabled intent`() = runTest {
        repository.setSubtitleDisabled(PlaybackPrefScope.ITEM, "item-1", disabled = true)
        assertEquals(true, repository.get(PlaybackPrefScope.ITEM, "item-1")!!.subtitleDisabled)

        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = null, subtitleLanguage = "eng", subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertEquals("eng", pref.subtitleLanguage)
        assertNull(pref.subtitleDisabled)
    }

    @Test
    fun `saving nothing on a fresh key creates no row so get returns null`() = runTest {
        // A row with nothing set carries no preference — "inherit global" — so
        // the all-null save must not materialize an empty row. Clearing an
        // existing field goes through the dedicated clear* methods.
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = null, subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        assertNull(repository.get(PlaybackPrefScope.ITEM, "item-1"))
        assertEquals(0, database.itemPlaybackPreferenceDao().countByScope(PlaybackPrefScope.ITEM.name))
    }

    // ── Clear helpers ───────────────────────────────────────────────────

    @Test
    fun `clearAudioLanguage drops the row when nothing else remains`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        repository.clearAudioLanguage(PlaybackPrefScope.ITEM, "item-1")

        assertNull(repository.get(PlaybackPrefScope.ITEM, "item-1"))
    }

    @Test
    fun `clearAudioLanguage keeps the row when a subtitle preference remains`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger", subtitleLanguage = "eng", subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        repository.clearAudioLanguage(PlaybackPrefScope.ITEM, "item-1")

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertNull(pref.audioLanguage)
        assertEquals("eng", pref.subtitleLanguage)
    }

    @Test
    fun `clearSubtitleLanguage also drops the forced and SDH role fields`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = null, subtitleLanguage = "eng", subtitleForced = true, subtitleHearingImpaired = true, dialogueBoostStrength = null)

        repository.clearSubtitleLanguage(PlaybackPrefScope.ITEM, "item-1")

        // The role is meaningless without its language — all three go together.
        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")
        assertNull(pref)
    }

    @Test
    fun `clear helpers on an unknown key are no-ops`() = runTest {
        repository.clearAudioLanguage(PlaybackPrefScope.ITEM, "missing")
        repository.clearSubtitleLanguage(PlaybackPrefScope.ITEM, "missing")
        repository.clearDialogueBoostStrength(PlaybackPrefScope.ITEM, "missing")
        assertNull(repository.get(PlaybackPrefScope.ITEM, "missing"))
    }

    // ── Subtitles-off intent ────────────────────────────────────────────

    @Test
    fun `setSubtitleDisabled true clears any pinned subtitle language and role`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger", subtitleLanguage = "eng", subtitleForced = true, subtitleHearingImpaired = true, dialogueBoostStrength = null)

        repository.setSubtitleDisabled(PlaybackPrefScope.ITEM, "item-1", disabled = true)

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertEquals(true, pref.subtitleDisabled)
        assertNull(pref.subtitleLanguage)
        assertNull(pref.subtitleForced)
        assertNull(pref.subtitleHearingImpaired)
        assertEquals("ger", pref.audioLanguage)
    }

    @Test
    fun `setSubtitleDisabled false drops the row when nothing else is pinned`() = runTest {
        repository.setSubtitleDisabled(PlaybackPrefScope.ITEM, "item-1", disabled = true)

        repository.setSubtitleDisabled(PlaybackPrefScope.ITEM, "item-1", disabled = false)

        assertNull(repository.get(PlaybackPrefScope.ITEM, "item-1"))
    }

    @Test
    fun `setSubtitleDisabled false keeps other pinned fields`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)
        repository.setSubtitleDisabled(PlaybackPrefScope.ITEM, "item-1", disabled = true)

        repository.setSubtitleDisabled(PlaybackPrefScope.ITEM, "item-1", disabled = false)

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertNull(pref.subtitleDisabled)
        assertEquals("ger", pref.audioLanguage)
    }

    // ── Remembered tracks ───────────────────────────────────────────────

    @Test
    fun `saveRememberedTrack round-trips audio and subtitle tracks per key`() = runTest {
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, RememberedTrack("English · 5.1", "eng", 0))
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.SUBTITLE, RememberedTrack("English SDH", "eng", 1))

        val pref = repository.get(PlaybackPrefScope.SERIES, "series-1")!!
        assertEquals(RememberedTrack("English · 5.1", "eng", 0), pref.rememberedAudioTrack)
        assertEquals(RememberedTrack("English SDH", "eng", 1), pref.rememberedSubtitleTrack)
    }

    @Test
    fun `saveRememberedTrack null clears only that track type`() = runTest {
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, RememberedTrack("English", "eng"))
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.SUBTITLE, RememberedTrack("English SDH", "eng"))

        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, track = null)

        val pref = repository.get(PlaybackPrefScope.SERIES, "series-1")!!
        assertNull(pref.rememberedAudioTrack)
        assertEquals(RememberedTrack("English SDH", "eng", -1), pref.rememberedSubtitleTrack)
    }

    @Test
    fun `clearing the last remembered track drops the row entirely`() = runTest {
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, RememberedTrack("English", "eng"))

        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, track = null)

        assertNull(repository.get(PlaybackPrefScope.SERIES, "series-1"))
    }

    @Test
    fun `saveRememberedTrack null on an unknown key is a no-op`() = runTest {
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "missing", TrackType.AUDIO, track = null)
        assertNull(repository.get(PlaybackPrefScope.SERIES, "missing"))
    }

    // ── Delete ──────────────────────────────────────────────────────────

    @Test
    fun `delete removes the row for that scope and key only`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)
        repository.save(PlaybackPrefScope.SERIES, "item-1", audioLanguage = "ger", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        repository.delete(PlaybackPrefScope.ITEM, "item-1")

        assertNull(repository.get(PlaybackPrefScope.ITEM, "item-1"))
        assertEquals("ger", repository.get(PlaybackPrefScope.SERIES, "item-1")!!.audioLanguage)
    }

    /**
     * Controllable [TimeSource] — same shape as the fake in
     * LyricsRepositoryImplTest (core:data deliberately hosts no shared test
     * fakes; see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
