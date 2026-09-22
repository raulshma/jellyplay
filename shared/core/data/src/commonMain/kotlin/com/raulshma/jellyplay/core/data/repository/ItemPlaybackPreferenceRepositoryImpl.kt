package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.repository.withTransaction
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.TrackType

/**
 * promotion from jvmShared: the impl is DAO + Room transaction + clock
 * only (all commonMain Room 3 since), crossing over with the clock edge
 * narrowed to the common [EpochMillisSource] seam.
 */
class ItemPlaybackPreferenceRepositoryImpl constructor(
    private val dao: ItemPlaybackPreferenceDao,
    private val database: JellyPlayDatabase,
    /** Clock seam for the persisted `updatedAt` stamps (last-write-wins merge). */
    private val timeSource: EpochMillisSource,
) : ItemPlaybackPreferenceRepository {

    /**
     * A row carries no preference when every preference column is null — the
     * single emptiness check every clear path funnels through. A new column
     * extends this once; a stale per-site subset would drop rows that still
     * remember that column (e.g. a render profile surviving a language clear).
     * The remembered codec/language/index triples ride with their label
     * column, so the label alone carries the "is something remembered" signal.
     */
    private fun ItemPlaybackPreferenceEntity.hasNoPreferences() =
        audioLanguage == null &&
            subtitleLanguage == null &&
            subtitleForced == null &&
            subtitleHearingImpaired == null &&
            subtitleDisabled == null &&
            dialogueBoostStrength == null &&
            rememberedAudioLabel == null &&
            rememberedSubtitleLabel == null &&
            renderProfile == null

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
            // A corrupt stored strength parses to null (never throws) and is
            // rewritten cleanly by the merge below.
            val mergedBoost = dialogueBoostStrength
                ?: existing?.dialogueBoostStrength.toEnumOrNull()
            // Subtitle language and "subtitles off" are mutually exclusive:
            // pinning a language clears any prior disabled intent so the two
            // can't both be set on one row.
            val mergedDisabled = if (mergedSub != null) null else existing?.subtitleDisabled
            // A row with nothing set carries no preference — drop it so the table
            // stays tidy and `get` returns null (i.e. "inherit global").
            // Built as a `copy` of the existing row so every column `save`
            // doesn't own rides along untouched (remembered-track triples,
            // the render-profile blob) — a constructor rebuild would null
            // them and a language-rule save would wipe a remembered track.
            val row = (existing ?: ItemPlaybackPreferenceEntity(
                scope = scope.name,
                key = key,
                audioLanguage = null,
                subtitleLanguage = null,
                updatedAt = timeSource.nowEpochMillis(),
            )).copy(
                audioLanguage = mergedAudio,
                subtitleLanguage = mergedSub,
                subtitleDisabled = mergedDisabled,
                subtitleForced = mergedForced,
                subtitleHearingImpaired = mergedSdh,
                dialogueBoostStrength = mergedBoost?.name,
                updatedAt = timeSource.nowEpochMillis(),
            )
            if (row.hasNoPreferences()) {
                dao.deleteByKey(scope.name, key)
                return@withTransaction
            }
            dao.upsert(row)
        }
    }

    override suspend fun clearAudioLanguage(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        val cleared = existing.copy(audioLanguage = null, updatedAt = timeSource.nowEpochMillis())
        if (cleared.hasNoPreferences()) {
            // Nothing left to remember — remove the row entirely.
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(cleared)
        }
    }

    override suspend fun clearSubtitleLanguage(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        // Clear the subtitle language AND its pinned role together: the role
        // is meaningless without a language to apply it to.
        val cleared = existing.copy(
            subtitleLanguage = null,
            subtitleForced = null,
            subtitleHearingImpaired = null,
            updatedAt = timeSource.nowEpochMillis(),
        )
        if (cleared.hasNoPreferences()) {
            // Nothing left to remember — remove the row entirely.
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(cleared)
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
                if (cleared.hasNoPreferences()) {
                    dao.deleteByKey(scope.name, key)
                } else {
                    dao.upsert(cleared)
                }
            }
        }
    }

    override suspend fun clearDialogueBoostStrength(scope: PlaybackPrefScope, key: String) {
        val existing = dao.getByKey(scope.name, key) ?: return
        val cleared = existing.copy(dialogueBoostStrength = null, updatedAt = timeSource.nowEpochMillis())
        if (cleared.hasNoPreferences()) {
            dao.deleteByKey(scope.name, key)
        } else {
            dao.upsert(cleared)
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
                    rememberedAudioCodec = null,
                )
                TrackType.SUBTITLE -> row.copy(
                    rememberedSubtitleLabel = null,
                    rememberedSubtitleLanguage = null,
                    rememberedSubtitleIndex = null,
                    rememberedSubtitleCodec = null,
                )
            }
            if (cleared.hasNoPreferences()) {
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
            // The codec rides along when the selected stream exposed one:
            // the extra re-match rung for episodes whose track labels
            // churned. A null codec never erases a previously-remembered one —
            // clearing goes through the `track == null` branch above.
            TrackType.AUDIO -> base.copy(
                rememberedAudioLabel = track.label,
                rememberedAudioLanguage = track.language,
                rememberedAudioIndex = track.indexWithinLanguage,
                rememberedAudioCodec = track.codec ?: base.rememberedAudioCodec,
            )
            TrackType.SUBTITLE -> base.copy(
                rememberedSubtitleLabel = track.label,
                rememberedSubtitleLanguage = track.language,
                rememberedSubtitleIndex = track.indexWithinLanguage,
                rememberedSubtitleCodec = track.codec ?: base.rememberedSubtitleCodec,
            )
        }
        dao.upsert(updated.copy(updatedAt = timeSource.nowEpochMillis()))
    }

    override suspend fun delete(scope: PlaybackPrefScope, key: String) {
        dao.deleteByKey(scope.name, key)
    }

    override suspend fun setRenderProfile(
        scope: PlaybackPrefScope,
        key: String,
        overrides: com.raulshma.jellyplay.core.model.MpvRenderOverrides?,
    ) {
        val existing = dao.getByKey(scope.name, key)
        if (overrides == null) {
            // "Inherit": clear the override; drop the row when nothing else is
            // remembered on it.
            val row = existing ?: return
            val cleared = row.copy(renderProfile = null, updatedAt = timeSource.nowEpochMillis())
            if (cleared.hasNoPreferences()) {
                dao.deleteByKey(scope.name, key)
            } else {
                dao.upsert(cleared)
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
        dao.upsert(
            base.copy(
                renderProfile = renderProfileCodec.encodeToString(
                    com.raulshma.jellyplay.core.model.MpvRenderOverrides.serializer(),
                    overrides,
                ),
                updatedAt = timeSource.nowEpochMillis(),
            )
        )
    }

    // Parse the persisted enum columns through the repo-wide seam: a corrupt
    // stored value degrades to the documented default instead of throwing out
    // of every read (scope → ITEM, the per-item default; an unknown strength
    // → null, i.e. "no boost preference").
    private fun ItemPlaybackPreferenceEntity.toDomain(): ItemPlaybackPreference =
        ItemPlaybackPreference(
            scope = scope.toEnumOrNull() ?: PlaybackPrefScope.ITEM,
            key = key,
            audioLanguage = audioLanguage,
            subtitleLanguage = subtitleLanguage,
            subtitleDisabled = subtitleDisabled,
            subtitleForced = subtitleForced,
            subtitleHearingImpaired = subtitleHearingImpaired,
            dialogueBoostStrength = dialogueBoostStrength.toEnumOrNull(),
            rememberedAudioTrack = rememberedAudioLabel?.let {
                RememberedTrack(
                    label = it,
                    language = rememberedAudioLanguage,
                    indexWithinLanguage = rememberedAudioIndex ?: -1,
                    codec = rememberedAudioCodec,
                )
            },
            rememberedSubtitleTrack = rememberedSubtitleLabel?.let {
                RememberedTrack(
                    label = it,
                    language = rememberedSubtitleLanguage,
                    indexWithinLanguage = rememberedSubtitleIndex ?: -1,
                    codec = rememberedSubtitleCodec,
                )
            },
            renderProfile = decodeRenderProfile(renderProfile),
            updatedAt = updatedAt,
        )

    internal companion object {
        /**
         * The `renderProfile` blob codec: lenient + ignoreUnknownKeys
         * so a profile written by a newer build still loads (unknown fields
         * dropped) and a corrupt blob degrades to null ("inherit global")
         * instead of failing every read of the row.
         */
        internal val renderProfileCodec: kotlinx.serialization.json.Json =
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        internal fun decodeRenderProfile(raw: String?): com.raulshma.jellyplay.core.model.MpvRenderOverrides? {
            if (raw.isNullOrBlank()) return null
            return try {
                renderProfileCodec.decodeFromString(
                    com.raulshma.jellyplay.core.model.MpvRenderOverrides.serializer(),
                    raw,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
