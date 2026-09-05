package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.repository.withTransaction
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.TrackType

class ItemPlaybackPreferenceRepositoryImpl constructor(
    private val dao: ItemPlaybackPreferenceDao,
    private val database: JellyPlayDatabase,
    /** Clock seam for the persisted `updatedAt` stamps (last-write-wins merge). */
    private val timeSource: TimeSource,
) : ItemPlaybackPreferenceRepository {

    override suspend fun get(scope: PlaybackPrefScope, key: String): ItemPlaybackPreference? =
        dao.getByKey(scope.name, key)?.toDomain()

    override suspend fun save(
        scope: PlaybackPrefScope,
        key: String,
        audioLanguage: String?,
        subtitleLanguage: String?,
        subtitleForced: Boolean?,
        subtitleHearingImpaired: Boolean?,
        dialogueBoostStrength: com.raulshma.jellyplay.core.model.EffectStrength?,
    ) {
        // Wrap the read-merge-write in a transaction so two concurrent `save`
        // calls for the same (scope, key) can't clobber each other's merge.
        database.withTransaction {
            val existing = dao.getByKey(scope.name, key)
            // `save` treats a null argument as "leave untouched" (preserve the
            // existing value for that field). Explicit single-field clearing goes
            // through the dedicated `clear*` methods so "clear" and "not provided"
            // remain distinguishable.
            val mergedAudio = audioLanguage ?: existing?.audioLanguage
            val mergedSub = subtitleLanguage ?: existing?.subtitleLanguage
            val mergedForced = subtitleForced ?: existing?.subtitleForced
            val mergedSdh = subtitleHearingImpaired ?: existing?.subtitleHearingImpaired
            val mergedBoost = dialogueBoostStrength ?: existing?.dialogueBoostStrength?.let {
                runCatching { com.raulshma.jellyplay.core.model.EffectStrength.valueOf(it) }.getOrNull()
            }
            // Subtitle language and "subtitles off" are mutually exclusive:
            // pinning a language clears any prior disabled intent so the two
            // can't both be set on one row.
            val mergedDisabled = if (mergedSub != null) null else existing?.subtitleDisabled
            // A row with nothing set carries no preference — drop it so the table
            // stays tidy and `get` returns null (i.e. "inherit global"). Subtitle
            // role fields only persist alongside a language, so they are never the
            // sole occupants of a row.
            if (mergedAudio == null && mergedSub == null && mergedBoost == null && mergedDisabled == null) {
                dao.deleteByKey(scope.name, key)
                return@withTransaction
            }
            dao.upsert(
                ItemPlaybackPreferenceEntity(
                    id = existing?.id ?: 0,
                    scope = scope.name,
                    key = key,
                    audioLanguage = mergedAudio,
                    subtitleLanguage = mergedSub,
                    subtitleDisabled = mergedDisabled,
                    subtitleForced = mergedForced,
                    subtitleHearingImpaired = mergedSdh,
                    dialogueBoostStrength = mergedBoost?.name,
                    updatedAt = timeSource.nowEpochMillis(),
                )
            )
        }
    }

    override suspend fun clearAudioLanguage(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        if (existing.subtitleLanguage == null && existing.subtitleDisabled == null &&
            existing.dialogueBoostStrength == null
        ) {
            // Nothing left to remember — remove the row entirely.
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(existing.copy(audioLanguage = null, updatedAt = timeSource.nowEpochMillis()))
        }
    }

    override suspend fun clearSubtitleLanguage(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        if (existing.audioLanguage == null && existing.subtitleDisabled == null &&
            existing.dialogueBoostStrength == null
        ) {
            // Nothing left to remember — remove the row entirely.
            dao.deleteByKey(scope.name, key)
        } else {
            // Clear the subtitle language AND its pinned role together: the role
            // is meaningless without a language to apply it to.
            dao.upsert(
                existing.copy(
                    subtitleLanguage = null,
                    subtitleForced = null,
                    subtitleHearingImpaired = null,
                    updatedAt = timeSource.nowEpochMillis(),
                )
            )
        }
    }

    override suspend fun setSubtitleDisabled(scope: PlaybackPrefScope, key: String, disabled: Boolean) {
        database.withTransaction {
            val existing = dao.getByKey(scope.name, key)
            if (disabled) {
                // Pinning "off" clears any pinned subtitle language + role: the
                // two intents are mutually exclusive.
                dao.upsert(
                    (existing ?: ItemPlaybackPreferenceEntity(
                        scope = scope.name,
                        key = key,
                        audioLanguage = null,
                        subtitleLanguage = null,
                        updatedAt = timeSource.nowEpochMillis(),
                    )).copy(
                        subtitleLanguage = null,
                        subtitleForced = null,
                        subtitleHearingImpaired = null,
                        subtitleDisabled = true,
                        updatedAt = timeSource.nowEpochMillis(),
                    )
                )
            } else {
                // Clearing the disabled intent: drop the row if nothing else is set.
                val row = existing ?: return@withTransaction
                val cleared = row.copy(subtitleDisabled = null, updatedAt = timeSource.nowEpochMillis())
                val hasNothingElse = cleared.audioLanguage == null &&
                    cleared.subtitleLanguage == null &&
                    cleared.dialogueBoostStrength == null
                if (hasNothingElse) {
                    dao.deleteByKey(scope.name, key)
                } else {
                    dao.upsert(cleared)
                }
            }
        }
    }

    override suspend fun clearDialogueBoostStrength(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        if (existing.audioLanguage == null && existing.subtitleLanguage == null &&
            existing.subtitleDisabled == null
        ) {
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(existing.copy(dialogueBoostStrength = null, updatedAt = timeSource.nowEpochMillis()))
        }
    }

    override suspend fun saveRememberedTrack(
        scope: PlaybackPrefScope,
        key: String,
        type: TrackType,
        track: RememberedTrack?,
    ) {
        val existing = dao.getByKey(scope.name, key)
        // If clearing and the row has nothing else to remember, drop it entirely.
        // Otherwise upsert the remembered fields onto the existing (or a fresh) row.
        if (track == null) {
            val row = existing ?: return
            val cleared = when (type) {
                TrackType.AUDIO -> row.copy(
                    rememberedAudioLabel = null,
                    rememberedAudioLanguage = null,
                    rememberedAudioIndex = null,
                )
                TrackType.SUBTITLE -> row.copy(
                    rememberedSubtitleLabel = null,
                    rememberedSubtitleLanguage = null,
                    rememberedSubtitleIndex = null,
                )
            }
            val hasNothingElse = cleared.audioLanguage == null &&
                cleared.subtitleLanguage == null &&
                cleared.subtitleDisabled == null &&
                cleared.dialogueBoostStrength == null &&
                cleared.rememberedAudioLabel == null &&
                cleared.rememberedSubtitleLabel == null
            if (hasNothingElse) {
                dao.deleteByKey(scope.name, key)
            } else {
                dao.upsert(cleared.copy(updatedAt = timeSource.nowEpochMillis()))
            }
            return
        }
        val base = existing ?: ItemPlaybackPreferenceEntity(
            scope = scope.name,
            key = key,
            audioLanguage = null,
            subtitleLanguage = null,
            updatedAt = timeSource.nowEpochMillis(),
        )
        val updated = when (type) {
            TrackType.AUDIO -> base.copy(
                rememberedAudioLabel = track.label,
                rememberedAudioLanguage = track.language,
                rememberedAudioIndex = track.indexWithinLanguage,
            )
            TrackType.SUBTITLE -> base.copy(
                rememberedSubtitleLabel = track.label,
                rememberedSubtitleLanguage = track.language,
                rememberedSubtitleIndex = track.indexWithinLanguage,
            )
        }
        dao.upsert(updated.copy(updatedAt = timeSource.nowEpochMillis()))
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
            subtitleDisabled = subtitleDisabled,
            subtitleForced = subtitleForced,
            subtitleHearingImpaired = subtitleHearingImpaired,
            dialogueBoostStrength = dialogueBoostStrength?.let {
                runCatching { com.raulshma.jellyplay.core.model.EffectStrength.valueOf(it) }.getOrNull()
            },
            rememberedAudioTrack = rememberedAudioLabel?.let {
                RememberedTrack(
                    label = it,
                    language = rememberedAudioLanguage,
                    indexWithinLanguage = rememberedAudioIndex ?: -1,
                )
            },
            rememberedSubtitleTrack = rememberedSubtitleLabel?.let {
                RememberedTrack(
                    label = it,
                    language = rememberedSubtitleLanguage,
                    indexWithinLanguage = rememberedSubtitleIndex ?: -1,
                )
            },
            updatedAt = updatedAt,
        )
}
