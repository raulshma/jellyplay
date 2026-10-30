package com.raulshma.jellyplay.core.data

import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistPayload
import com.raulshma.jellyplay.core.model.MoodPlaylistPreference
import com.raulshma.jellyplay.core.model.MoodPlaylistPreferencePayload
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.PlaylistCriterion
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.core.model.SmartPlaylistSort
import com.raulshma.jellyplay.core.model.SmartPlaylistPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * THE one row codec for the playlist families (the ADR-0011 `playlists`
 * namespace + the settings-backup `playlists` slice): every mapping a
 * playlist row ever needs lives here — Room entity ↔ wire payload (the tier
 * the sync adapter and the backup slice carry verbatim, criteria/keyword
 * blobs opaque) and wire payload ↔ domain (the criteria/keyword JSON decode,
 * lenient by contract). The repositories, the sync adapter and the slice
 * source all consume this codec — one codec per family, so the JSON on disk,
 * the payload wire shape and the decode tolerances cannot drift between the
 * three consumers.
 *
 * The Json instance is a parameter (not a constant): the repositories encode
 * the criteria/keyword blobs with their injected Json single, and the wire
 * carries the blob as an opaque string — so the blob's shape is whatever the
 * writer produced, and the codec only ever decodes leniently.
 */

// ── smart playlists ────────────────────────────────────────────────────────

internal fun SmartPlaylistEntity.toPayload(): SmartPlaylistPayload = SmartPlaylistPayload(
    id = id,
    name = name,
    criteriaJson = criteriaJson,
    maxItems = maxItems,
    sortBy = sortBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun entityFrom(payload: SmartPlaylistPayload) = SmartPlaylistEntity(
    id = payload.id,
    name = payload.name,
    criteriaJson = payload.criteriaJson,
    maxItems = payload.maxItems,
    sortBy = payload.sortBy,
    createdAt = payload.createdAt,
    updatedAt = payload.updatedAt,
)

/** The row's domain face: the criteria blob decoded leniently (a malformed blob degrades to no criteria). */
internal fun SmartPlaylistEntity.toDomain(json: Json): SmartPlaylist = toPayload().toDomain(json)

internal fun SmartPlaylistPayload.toDomain(json: Json): SmartPlaylist = SmartPlaylist(
    id = id,
    name = name,
    criteria = runCatching {
        json.decodeFromString<List<PlaylistCriterionDto>>(criteriaJson)
    }.getOrDefault(emptyList())
        .map { it.toDomain() },
    maxItems = maxItems,
    sortBy = sortBy.toEnumOrNull() ?: SmartPlaylistSort.RANDOM,
)

/**
 * The row's stored face, built from the domain: the criteria blob encoded
 * with [json] and the entity's wall-clock stamps — the fresh-local-write
 * rule the entity defaults express.
 */
internal fun SmartPlaylist.toEntity(json: Json): SmartPlaylistEntity = SmartPlaylistEntity(
    id = id,
    name = name,
    criteriaJson = json.encodeToString(criteria.map { it.toDto() }),
    maxItems = maxItems,
    sortBy = sortBy.name,
)

// ── mood playlists ─────────────────────────────────────────────────────────

internal fun MoodPlaylistEntity.toPayload(): MoodPlaylistPayload = MoodPlaylistPayload(
    id = id,
    name = name,
    emoji = emoji,
    description = description,
    genreKeywordsJson = genreKeywordsJson,
    excludedGenresJson = excludedGenresJson,
    minRating = minRating,
    sortBy = sortBy,
    maxItems = maxItems,
    themeColorHex = themeColorHex,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun entityFrom(payload: MoodPlaylistPayload) = MoodPlaylistEntity(
    id = payload.id,
    name = payload.name,
    emoji = payload.emoji,
    description = payload.description,
    genreKeywordsJson = payload.genreKeywordsJson,
    excludedGenresJson = payload.excludedGenresJson,
    minRating = payload.minRating,
    sortBy = payload.sortBy,
    maxItems = payload.maxItems,
    themeColorHex = payload.themeColorHex,
    createdAt = payload.createdAt,
    updatedAt = payload.updatedAt,
)

/** The row's domain face: the keyword/exclusion blobs decoded leniently (a malformed blob degrades to empty). */
internal fun MoodPlaylistEntity.toDomain(json: Json): MoodPlaylist = toPayload().toDomain(json)

internal fun MoodPlaylistPayload.toDomain(json: Json): MoodPlaylist = MoodPlaylist(
    id = id,
    name = name,
    emoji = emoji,
    description = description,
    genreKeywords = runCatching {
        json.decodeFromString<List<String>>(genreKeywordsJson)
    }.getOrDefault(emptyList()),
    excludedGenres = excludedGenresJson?.let {
        runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
    } ?: emptyList(),
    minRating = minRating,
    sortBy = sortBy.toEnumOrNull() ?: MoodPlaylistSort.RANDOM,
    maxItems = maxItems,
    themeColorHex = themeColorHex,
)

/** The row's stored face from the domain (wall-clock stamps, same rule as the smart family). */
internal fun MoodPlaylist.toEntity(json: Json): MoodPlaylistEntity = MoodPlaylistEntity(
    id = id,
    name = name,
    emoji = emoji,
    description = description,
    genreKeywordsJson = json.encodeToString(genreKeywords),
    excludedGenresJson = if (excludedGenres.isEmpty()) null
    else json.encodeToString(excludedGenres),
    minRating = minRating,
    sortBy = sortBy.name,
    maxItems = maxItems,
    themeColorHex = themeColorHex,
)

// ── mood-playlist preferences ──────────────────────────────────────────────

internal fun MoodPlaylistPreferenceEntity.toPayload(): MoodPlaylistPreferencePayload =
    MoodPlaylistPreferencePayload(
        playlistId = playlistId,
        isEnabled = isEnabled,
        isFavorite = isFavorite,
        lastPlayedAt = lastPlayedAt,
        updatedAt = updatedAt,
    )

internal fun entityFrom(payload: MoodPlaylistPreferencePayload) = MoodPlaylistPreferenceEntity(
    playlistId = payload.playlistId,
    isEnabled = payload.isEnabled,
    isFavorite = payload.isFavorite,
    lastPlayedAt = payload.lastPlayedAt,
    updatedAt = payload.updatedAt,
)

internal fun MoodPlaylistPreferenceEntity.toDomain(): MoodPlaylistPreference =
    MoodPlaylistPreference(
        playlistId = playlistId,
        isEnabled = isEnabled,
        isFavorite = isFavorite,
        lastPlayedAt = lastPlayedAt,
    )

// ── the criteria JSON vocabulary (the blob's only schema) ─────────────────

@Serializable
internal data class PlaylistCriterionDto(
    val type: String,
    val value: String,
    val operator: String = "EQUALS",
) {
    fun toDomain(): PlaylistCriterion = PlaylistCriterion(
        type = type.toEnumOrNull() ?: CriterionType.GENRE,
        value = value,
        operator = operator.toEnumOrNull() ?: CriterionOperator.EQUALS,
    )
}

internal fun PlaylistCriterion.toDto(): PlaylistCriterionDto = PlaylistCriterionDto(
    type = type.name,
    value = value,
    operator = operator.name,
)
