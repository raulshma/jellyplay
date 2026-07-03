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
        dialogueBoostStrength: com.raulshma.jellyplay.core.model.EffectStrength?,
    ) {
        val existing = dao.getByKey(scope.name, key)
        // `save` treats a null argument as "leave untouched" (preserve the
        // existing value for that field). Explicit single-field clearing goes
        // through the dedicated `clear*` methods so "clear" and "not provided"
        // remain distinguishable.
        val mergedAudio = audioLanguage ?: existing?.audioLanguage
        val mergedSub = subtitleLanguage ?: existing?.subtitleLanguage
        val mergedBoost = dialogueBoostStrength ?: existing?.dialogueBoostStrength?.let {
            runCatching { com.raulshma.jellyplay.core.model.EffectStrength.valueOf(it) }.getOrNull()
        }
        // A row with nothing set carries no preference — drop it so the table
        // stays tidy and `get` returns null (i.e. "inherit global").
        if (mergedAudio == null && mergedSub == null && mergedBoost == null) {
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
                dialogueBoostStrength = mergedBoost?.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun clearAudioLanguage(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        if (existing.subtitleLanguage == null && existing.dialogueBoostStrength == null) {
            // Nothing left to remember — remove the row entirely.
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(existing.copy(audioLanguage = null, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun clearSubtitleLanguage(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        if (existing.audioLanguage == null && existing.dialogueBoostStrength == null) {
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(existing.copy(subtitleLanguage = null, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun clearDialogueBoostStrength(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        if (existing.audioLanguage == null && existing.subtitleLanguage == null) {
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(existing.copy(dialogueBoostStrength = null, updatedAt = System.currentTimeMillis()))
        }
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
            dialogueBoostStrength = dialogueBoostStrength?.let {
                runCatching { com.raulshma.jellyplay.core.model.EffectStrength.valueOf(it) }.getOrNull()
            },
            updatedAt = updatedAt,
        )
}
