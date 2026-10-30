package com.raulshma.jellyplay.core.data

import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.MoodPlaylistPayload
import com.raulshma.jellyplay.core.model.MoodPlaylistPreferencePayload
import com.raulshma.jellyplay.core.model.SmartPlaylistPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Round-trips the playlist families' one row codec (the
 * [PlaylistRowCodec.kt] tier the repositories, the `playlists` sync adapter
 * and the backup slice all decode through) — the itemprefs codec's
 * round-trip contract (SettingsBackupSliceSourcesTest), applied per family:
 * every field NON-DEFAULT, so entity → payload → wire JSON → payload →
 * entity must come back EQUAL (a column mapped on one side only decodes back
 * as its default and fails loudly), plus the payload → domain → entity leg
 * the repositories ride.
 */
class PlaylistRowCodecTest {

    // The same codec stance the wire consumers use (SliceJson's config,
    // re-declared here: that single is jvmShared, this lane is common).
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun smartPlaylistRowRoundTripsEntityToPayloadToWireAndBack() {
        val original = SmartPlaylistEntity(
            id = "sp-1",
            name = "90s Rock",
            criteriaJson = """[{"type":"GENRE","value":"Rock","operator":"CONTAINS"}]""",
            maxItems = 25,
            sortBy = "YEAR",
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )

        val payload = original.toPayload()
        assertEquals(SmartPlaylistPayload::class, payload::class)
        assertEquals(original, entityFrom(payload))

        val decoded = json.encodeToJsonElement(payload)
            .toPayloadOrNull(SmartPlaylistPayload.serializer())!!
        assertEquals(original, entityFrom(decoded))
    }

    @Test
    fun moodPlaylistRowRoundTripsEntityToPayloadToWireAndBack() {
        val original = MoodPlaylistEntity(
            id = "mood-1",
            name = "Rainy Evening",
            emoji = "🌧",
            description = "Soft rock for wet windows",
            genreKeywordsJson = """["Soft Rock","Jazz"]""",
            excludedGenresJson = """["Metal"]""",
            minRating = 6.5f,
            sortBy = "RATING",
            maxItems = 40,
            themeColorHex = "#445566",
            createdAt = 3_000L,
            updatedAt = 4_000L,
        )

        val payload = original.toPayload()
        assertEquals(MoodPlaylistPayload::class, payload::class)
        assertEquals(original, entityFrom(payload))

        val decoded = json.encodeToJsonElement(payload)
            .toPayloadOrNull(MoodPlaylistPayload.serializer())!!
        assertEquals(original, entityFrom(decoded))
    }

    @Test
    fun moodPreferenceRowRoundTripsEntityToPayloadToWireAndBack() {
        val original = MoodPlaylistPreferenceEntity(
            playlistId = "mood-1",
            isEnabled = false,
            isFavorite = true,
            lastPlayedAt = 5_000L,
            updatedAt = 6_000L,
        )

        val payload = original.toPayload()
        assertEquals(MoodPlaylistPreferencePayload::class, payload::class)
        assertEquals(original, entityFrom(payload))

        val decoded = json.encodeToJsonElement(payload)
            .toPayloadOrNull(MoodPlaylistPreferencePayload.serializer())!!
        assertEquals(original, entityFrom(decoded))
    }

    @Test
    fun domainFacesDecodeLenientlyAndReEncodeToTheSameRow() {
        // payload → domain → entity: the domain face must rebuild the row's
        // payload-visible columns (the criteria blob re-encodes through the
        // same DTO vocabulary; the wall-clock stamps the entity defaults own
        // are the fresh-write rule, asserted separately by
        // PlaylistRepositoriesTest's real-Room round-trips).
        val entity = SmartPlaylistEntity(
            id = "sp-2",
            name = "New Releases",
            criteriaJson = """[{"type":"YEAR","value":"2020","operator":"GREATER_THAN"}]""",
            maxItems = 10,
            sortBy = "DATE_ADDED",
            createdAt = 7_000L,
            updatedAt = 8_000L,
        )
        val domain = entity.toDomain(json)
        assertEquals(1, domain.criteria.size)
        assertEquals(CriterionType.YEAR, domain.criteria[0].type)
        assertEquals(CriterionOperator.GREATER_THAN, domain.criteria[0].operator)

        // The criteria blob is round-trip stable: the payload tier's wire
        // shape (the opaque criteriaJson string) survives the decode/re-encode.
        val rebuilt = domain.toEntity(json)
        assertEquals(entity.criteriaJson, rebuilt.criteriaJson)
        assertEquals(entity.maxItems, rebuilt.maxItems)
        assertEquals(entity.sortBy, rebuilt.sortBy)

        // A malformed criteria blob degrades to no criteria — never throws.
        val corrupt = entity.copy(criteriaJson = "not json")
        assertEquals(emptyList(), corrupt.toDomain(json).criteria)
    }
}
