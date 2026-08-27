package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.dao.SmartPlaylistDao
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistPreference
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.PlaylistCriterion
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.core.model.SmartPlaylistSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SmartPlaylistRepository constructor(
    private val smartPlaylistDao: SmartPlaylistDao,
    private val json: Json,
) {
    fun observeSmartPlaylists(): Flow<List<SmartPlaylist>> =
        smartPlaylistDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<SmartPlaylist> = smartPlaylistDao.getAll().map { it.toDomain() }

    suspend fun getById(id: String): SmartPlaylist? = smartPlaylistDao.getById(id)?.toDomain()

    suspend fun upsert(playlist: SmartPlaylist) {
        smartPlaylistDao.insert(playlist.toEntity())
    }

    suspend fun delete(id: String) {
        smartPlaylistDao.deleteById(id)
    }

    private fun SmartPlaylistEntity.toDomain(): SmartPlaylist {
        val criteriaList = runCatching {
            json.decodeFromString<List<PlaylistCriterionDto>>(criteriaJson)
        }.getOrDefault(emptyList())
            .map { it.toDomain() }
        return SmartPlaylist(
            id = id,
            name = name,
            criteria = criteriaList,
            maxItems = maxItems,
            sortBy = runCatching { SmartPlaylistSort.valueOf(sortBy) }
                .getOrDefault(SmartPlaylistSort.RANDOM),
        )
    }

    private fun SmartPlaylist.toEntity(): SmartPlaylistEntity {
        val dtos = criteria.map { it.toDto() }
        return SmartPlaylistEntity(
            id = id,
            name = name,
            criteriaJson = json.encodeToString(dtos),
            maxItems = maxItems,
            sortBy = sortBy.name,
        )
    }
}

class MoodPlaylistRepository constructor(
    private val moodPlaylistDao: MoodPlaylistDao,
    private val json: Json,
) {
    fun observeMoodPlaylists(): Flow<List<MoodPlaylist>> =
        moodPlaylistDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<MoodPlaylist> = moodPlaylistDao.getAll().map { it.toDomain() }

    suspend fun getById(id: String): MoodPlaylist? = moodPlaylistDao.getById(id)?.toDomain()

    suspend fun upsert(playlist: MoodPlaylist) {
        moodPlaylistDao.insert(playlist.toEntity())
    }

    suspend fun delete(id: String) {
        moodPlaylistDao.deleteById(id)
    }

    suspend fun getPreference(playlistId: String): MoodPlaylistPreference? =
        moodPlaylistDao.getPreference(playlistId)?.toDomain()

    fun observePreferences(): Flow<List<MoodPlaylistPreference>> =
        moodPlaylistDao.observePreferences().map { list -> list.map { it.toDomain() } }

    suspend fun getAllPreferences(): List<MoodPlaylistPreference> =
        moodPlaylistDao.getAllPreferences().map { it.toDomain() }

    suspend fun setPreference(
        playlistId: String,
        isEnabled: Boolean = true,
        isFavorite: Boolean = false,
    ) {
        moodPlaylistDao.upsertPreference(
            MoodPlaylistPreferenceEntity(
                playlistId = playlistId,
                isEnabled = isEnabled,
                isFavorite = isFavorite,
                lastPlayedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun MoodPlaylistPreferenceEntity.toDomain(): MoodPlaylistPreference =
        MoodPlaylistPreference(
            playlistId = playlistId,
            isEnabled = isEnabled,
            isFavorite = isFavorite,
            lastPlayedAt = lastPlayedAt,
        )

    private fun MoodPlaylistEntity.toDomain(): MoodPlaylist {
        val keywords = runCatching {
            json.decodeFromString<List<String>>(genreKeywordsJson)
        }.getOrDefault(emptyList())
        val excluded = excludedGenresJson?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
        } ?: emptyList()
        return MoodPlaylist(
            id = id,
            name = name,
            emoji = emoji,
            description = description,
            genreKeywords = keywords,
            excludedGenres = excluded,
            minRating = minRating,
            sortBy = runCatching { MoodPlaylistSort.valueOf(sortBy) }
                .getOrDefault(MoodPlaylistSort.RANDOM),
            maxItems = maxItems,
            themeColorHex = themeColorHex,
        )
    }

    private fun MoodPlaylist.toEntity(): MoodPlaylistEntity = MoodPlaylistEntity(
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
}

@Serializable
private data class PlaylistCriterionDto(
    val type: String,
    val value: String,
    val operator: String = "EQUALS",
) {
    fun toDomain(): PlaylistCriterion = PlaylistCriterion(
        type = runCatching { CriterionType.valueOf(type) }.getOrDefault(CriterionType.GENRE),
        value = value,
        operator = runCatching { CriterionOperator.valueOf(operator) }
            .getOrDefault(CriterionOperator.EQUALS),
    )
}

private fun PlaylistCriterion.toDto(): PlaylistCriterionDto = PlaylistCriterionDto(
    type = type.name,
    value = value,
    operator = operator.name,
)
