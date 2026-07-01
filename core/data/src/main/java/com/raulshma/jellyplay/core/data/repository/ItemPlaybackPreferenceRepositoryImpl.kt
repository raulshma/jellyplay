package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemPlaybackPreferenceRepositoryImpl @Inject constructor(
    private val dao: ItemPlaybackPreferenceDao,
) : ItemPlaybackPreferenceRepository {

    override suspend fun get(scope: PlaybackPrefScope, key: String): ItemPlaybackPreference? =
        dao.getByKey(scope.name, key)?.toDomain()

    override suspend fun save(
        scope: PlaybackPrefScope,
        key: String,
        audioLanguage: String?,
        subtitleLanguage: String?,
    ) {
        val existing = dao.getByKey(scope.name, key)
        val mergedAudio = audioLanguage ?: existing?.audioLanguage
        val mergedSub = subtitleLanguage ?: existing?.subtitleLanguage
        // A row with nothing set carries no preference — drop it so the table
        // stays tidy and `get` returns null (i.e. "inherit global").
        if (mergedAudio == null && mergedSub == null) {
            dao.deleteByKey(scope.name, key)
            return
        }
        dao.upsert(
            ItemPlaybackPreferenceEntity(
                id = existing?.id ?: 0,
                scope = scope.name,
                key = key,
                audioLanguage = mergedAudio,
                subtitleLanguage = mergedSub,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun delete(scope: PlaybackPrefScope, key: String) {
        dao.deleteByKey(scope.name, key)
    }

    private fun ItemPlaybackPreferenceEntity.toDomain(): ItemPlaybackPreference =
        ItemPlaybackPreference(
            scope = PlaybackPrefScope.valueOf(scope),
            key = key,
            audioLanguage = audioLanguage,
            subtitleLanguage = subtitleLanguage,
            updatedAt = updatedAt,
        )
}
