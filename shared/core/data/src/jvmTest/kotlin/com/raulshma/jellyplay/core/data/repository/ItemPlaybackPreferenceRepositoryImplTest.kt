package com.raulshma.jellyplay.core.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MpvRenderOverrides
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.data.testutil.FakeTimeSource

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
    fun `a language save keeps the remembered tracks and render profile`() = runTest {
        // `save` is the language/boost surface; the remembered-track triples
        // and the render-profile blob belong to their own writers and must
        // survive it (a constructor rebuild of the row would null them).
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, RememberedTrack("English · 5.1", "eng", 0, codec = "eac3"))
        repository.setRenderProfile(PlaybackPrefScope.SERIES, "series-1", MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A))

        repository.save(PlaybackPrefScope.SERIES, "series-1", audioLanguage = "ger")

        val pref = repository.get(PlaybackPrefScope.SERIES, "series-1")!!
        assertEquals("ger", pref.audioLanguage)
        assertEquals(RememberedTrack("English · 5.1", "eng", 0, codec = "eac3"), pref.rememberedAudioTrack)
        assertEquals(MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A), pref.renderProfile)
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

    @Test
    fun `saveRememberedTrack captures and clears the codec column`() = runTest {
        // The codec rides alongside label/language/index and survives
        // the DAO round-trip — the language+codec re-match rung's input.
        repository.saveRememberedTrack(
            PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO,
            RememberedTrack("German 5.1", "ger", 0, codec = "eac3"),
        )
        val pref = repository.get(PlaybackPrefScope.SERIES, "series-1")!!
        assertEquals("eac3", pref.rememberedAudioTrack?.codec)
        assertEquals(RememberedTrack("German 5.1", "ger", 0, codec = "eac3"), pref.rememberedAudioTrack)

        // A later selection without a codec (stream exposed none) keeps the
        // previously remembered one instead of erasing it.
        repository.saveRememberedTrack(
            PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO,
            RememberedTrack("German", "ger", 0, codec = null),
        )
        assertEquals("eac3", repository.get(PlaybackPrefScope.SERIES, "series-1")!!.rememberedAudioTrack?.codec)

        // Clearing the remembered track drops its codec with it — and since
        // nothing else is pinned on the row, the row itself is dropped (the
        // `clearing the last remembered track drops the row entirely` pin).
        repository.saveRememberedTrack(PlaybackPrefScope.SERIES, "series-1", TrackType.AUDIO, track = null)
        assertNull(repository.get(PlaybackPrefScope.SERIES, "series-1"))
    }

    // ── Render profile ──────────────────────────────────────────

    @Test
    fun `setRenderProfile round-trips the override through the DAO`() = runTest {
        val overrides = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_B, toneMapping = MpvToneMapping.BT2390)
        repository.setRenderProfile(PlaybackPrefScope.SERIES, "series-1", overrides)
        assertEquals(overrides, repository.get(PlaybackPrefScope.SERIES, "series-1")!!.renderProfile)
    }

    @Test
    fun `setRenderProfile null clears the override and keeps the other fields`() = runTest {
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger")
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A))
        assertNotNull(repository.get(PlaybackPrefScope.ITEM, "item-1")!!.renderProfile)

        // "Inherit": the override clears, the language rule survives.
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", null)
        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertNull(pref.renderProfile)
        assertEquals("ger", pref.audioLanguage)
    }

    @Test
    fun `clearing the render profile on a row with nothing else drops the row`() = runTest {
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_C))
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", null)
        assertNull(repository.get(PlaybackPrefScope.ITEM, "item-1"))
    }

    @Test
    fun `setRenderProfile null on an unknown key is a no-op`() = runTest {
        repository.setRenderProfile(PlaybackPrefScope.SERIES, "missing", null)
        assertNull(repository.get(PlaybackPrefScope.SERIES, "missing"))
    }

    @Test
    fun `a language-rule save preserves the render profile blob`() = runTest {
        val overrides = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A, toneMapping = MpvToneMapping.HABLE)
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", overrides)
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "ger")
        assertEquals(overrides, repository.get(PlaybackPrefScope.ITEM, "item-1")!!.renderProfile)
        assertEquals("ger", repository.get(PlaybackPrefScope.ITEM, "item-1")!!.audioLanguage)
    }

    @Test
    fun `an all-null save keeps a row that only holds a render profile`() = runTest {
        val overrides = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_B)
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", overrides)

        // "Leave everything untouched" must not read as "no preference left":
        // the row still remembers the render profile.
        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = null, subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        assertEquals(overrides, repository.get(PlaybackPrefScope.ITEM, "item-1")!!.renderProfile)
    }

    @Test
    fun `clearAudioLanguage keeps a row that only holds a remembered track`() = runTest {
        repository.save(PlaybackPrefScope.SERIES, "series-1", audioLanguage = "ger", subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)
        repository.saveRememberedTrack(
            PlaybackPrefScope.SERIES, "series-1", TrackType.SUBTITLE,
            RememberedTrack("English SDH", "eng", 1),
        )

        repository.clearAudioLanguage(PlaybackPrefScope.SERIES, "series-1")

        val pref = repository.get(PlaybackPrefScope.SERIES, "series-1")!!
        assertNull(pref.audioLanguage)
        assertEquals(RememberedTrack("English SDH", "eng", 1), pref.rememberedSubtitleTrack)
    }

    @Test
    fun `get with a corrupt stored renderProfile degrades to null instead of throwing`() = runTest {
        database.itemPlaybackPreferenceDao().upsert(
            ItemPlaybackPreferenceEntity(
                scope = PlaybackPrefScope.ITEM.name,
                key = "item-1",
                audioLanguage = "ger",
                subtitleLanguage = null,
                renderProfile = "{not json",
                updatedAt = 1L,
            )
        )
        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertNull(pref.renderProfile)
        assertEquals("ger", pref.audioLanguage)
    }

    @Test
    fun `setRenderProfile overwrites a corrupt stored blob cleanly`() = runTest {
        database.itemPlaybackPreferenceDao().upsert(
            ItemPlaybackPreferenceEntity(
                scope = PlaybackPrefScope.ITEM.name,
                key = "item-1",
                audioLanguage = null,
                subtitleLanguage = null,
                renderProfile = "{corrupt",
                updatedAt = 1L,
            )
        )
        val overrides = MpvRenderOverrides(shaderPack = MpvShaderPack.CUSTOM, toneMapping = MpvToneMapping.CLIP)
        repository.setRenderProfile(PlaybackPrefScope.ITEM, "item-1", overrides)
        assertEquals(overrides, repository.get(PlaybackPrefScope.ITEM, "item-1")!!.renderProfile)
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

    // ── Corrupt stored values degrade to the documented default ─────────

    @Test
    fun `get with a corrupt stored dialogueBoostStrength returns null instead of throwing`() = runTest {
        // A row persisted with an unknown strength name (hand-edit / restore
        // from an incompatible build) must degrade to "no boost preference" —
        // the parse seam never throws on a corrupt value.
        database.itemPlaybackPreferenceDao().upsert(
            ItemPlaybackPreferenceEntity(
                scope = PlaybackPrefScope.ITEM.name,
                key = "item-1",
                audioLanguage = "ger",
                subtitleLanguage = null,
                dialogueBoostStrength = "BLAST",
                updatedAt = 1L,
            )
        )

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!

        assertNull(pref.dialogueBoostStrength)
        assertEquals("ger", pref.audioLanguage)
    }

    @Test
    fun `save merging onto a corrupt stored strength rewrites it as null without throwing`() = runTest {
        // "Leave untouched" (null arg) re-parses the corrupt stored value on
        // the merge path — the merge must yield null, keep the surviving
        // fields, and overwrite the corrupt column on write-back.
        database.itemPlaybackPreferenceDao().upsert(
            ItemPlaybackPreferenceEntity(
                scope = PlaybackPrefScope.ITEM.name,
                key = "item-1",
                audioLanguage = "ger",
                subtitleLanguage = null,
                dialogueBoostStrength = "BLAST",
                updatedAt = 1L,
            )
        )

        repository.save(PlaybackPrefScope.ITEM, "item-1", audioLanguage = null, subtitleLanguage = null, subtitleForced = null, subtitleHearingImpaired = null, dialogueBoostStrength = null)

        val pref = repository.get(PlaybackPrefScope.ITEM, "item-1")!!
        assertEquals("ger", pref.audioLanguage)
        assertNull(pref.dialogueBoostStrength)
    }
}
