package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.toDomain
import com.raulshma.jellyplay.core.data.toEntity
import com.raulshma.jellyplay.core.model.EpochMillisSource
import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.dao.SmartPlaylistDao
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistPreference
import com.raulshma.jellyplay.core.model.SmartPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * DAO pass-throughs over the ONE playlist row codec
 * ([com.raulshma.jellyplay.core.data] `PlaylistRowCodec.kt` — the same
 * entity↔payload↔domain tier the `playlists` sync adapter and the backup
 * slice decode through), so the JSON on disk and the decode tolerances
 * cannot drift between the three consumers. What stays here are the
 * query/upsert verbs.
 */
class SmartPlaylistRepository constructor(
    private val smartPlaylistDao: SmartPlaylistDao,
    private val json: Json,
) {
    fun observeSmartPlaylists(): Flow<List<SmartPlaylist>> =
        smartPlaylistDao.observeAll().map { list -> list.map { it.toDomain(json) } }

    suspend fun getAll(): List<SmartPlaylist> = smartPlaylistDao.getAll().map { it.toDomain(json) }

    suspend fun getById(id: String): SmartPlaylist? = smartPlaylistDao.getById(id)?.toDomain(json)

    suspend fun upsert(playlist: SmartPlaylist) {
        smartPlaylistDao.insert(playlist.toEntity(json))
    }

    suspend fun delete(id: String) {
        smartPlaylistDao.deleteById(id)
    }
}

class MoodPlaylistRepository constructor(
    private val moodPlaylistDao: MoodPlaylistDao,
    private val json: Json,
    /** Clock seam for the preference row's `lastPlayedAt`/`updatedAt` stamps. */
    private val timeSource: EpochMillisSource,
) {
    fun observeMoodPlaylists(): Flow<List<MoodPlaylist>> =
        moodPlaylistDao.observeAll().map { list -> list.map { it.toDomain(json) } }

    suspend fun getAll(): List<MoodPlaylist> = moodPlaylistDao.getAll().map { it.toDomain(json) }

    suspend fun getById(id: String): MoodPlaylist? = moodPlaylistDao.getById(id)?.toDomain(json)

    suspend fun upsert(playlist: MoodPlaylist) {
        moodPlaylistDao.insert(playlist.toEntity(json))
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
                lastPlayedAt = timeSource.nowEpochMillis(),
                updatedAt = timeSource.nowEpochMillis(),
            ),
        )
    }
}
