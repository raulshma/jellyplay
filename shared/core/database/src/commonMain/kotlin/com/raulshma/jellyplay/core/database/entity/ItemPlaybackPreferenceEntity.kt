package com.raulshma.jellyplay.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Persisted form of a per-item / per-series playback-language preference.
 * The `(scope, key)` pair is unique, making an
 * [androidx.room3.OnConflictStrategy.REPLACE] insert behave as an upsert.
 *
 * Mapped to/from the domain [com.raulshma.jellyplay.core.model.ItemPlaybackPreference]
 * by the repository layer. `scope`/`updatedAt` are stored as the enum/text +
 * epoch-millis conventions used elsewhere in this database.
 *
 * `dialogueBoostStrength` stores the [com.raulshma.jellyplay.core.model.EffectStrength]
 * name (or null) — added in migration 27→28.
 *
 * `subtitleForced` / `subtitleHearingImpaired` pin the preferred subtitle's role
 * alongside `subtitleLanguage` (nullable: null = "don't care"), so a series can
 * remember e.g. "English SDH" and have it carry across episodes. Added in
 * migration 38→39.
 *
 * `subtitleDisabled` stores an explicit "subtitles off" intent per item/series
 * (nullable: null = "don't care"). When true the resolver forces subtitles off
 * instead of resolving a language match. Mutually exclusive with
 * `subtitleLanguage`; the repository keeps them consistent on write.
 * Added in migration 41→42.
 *
 * The six `rememberedAudio*` / `rememberedSubtitle*` columns persist the
 * *last-selected* audio/subtitle track's label, language, and position within
 * its language group per series — the cross-episode track-scoring memory (G5),
 * so a specific "English · 5.1" pick survives an app restart instead of being
 * remembered only in-process. All nullable: NULL means "no track remembered"
 * (preserves today's language-only behaviour for existing rows). Added in
 * migration 39→40.
 *
 * `rememberedAudioCodec` / `rememberedSubtitleCodec` persist the remembered
 * track's container codec alongside the label/language/index triple — an
 * extra re-match rung when a series' track layout churns between episodes
 * (labels change, codecs don't). Added in migration 56→57.
 *
 * `renderProfile` stores an optional per-series/per-item rendering override
 * blob (serialized `MpvRenderOverrides` — shader pack / tone mapping) for the
 * desktop render-profile feature (`RenderProfileResolver`/`RenderSheet`).
 * Added in migration 56→57.
 */
@Entity(
    tableName = "item_playback_preferences",
    indices = [
        Index(value = ["scope", "key"], unique = true),
        Index(value = ["updatedAt"]),
    ],
)
data class ItemPlaybackPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val key: String,
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val subtitleDisabled: Boolean? = null,
    val subtitleForced: Boolean? = null,
    val subtitleHearingImpaired: Boolean? = null,
    val dialogueBoostStrength: String? = null,
    val rememberedAudioLabel: String? = null,
    val rememberedAudioLanguage: String? = null,
    val rememberedAudioIndex: Int? = null,
    val rememberedAudioCodec: String? = null,
    val rememberedSubtitleLabel: String? = null,
    val rememberedSubtitleLanguage: String? = null,
    val rememberedSubtitleIndex: Int? = null,
    val rememberedSubtitleCodec: String? = null,
    val renderProfile: String? = null,
    val updatedAt: Long,
)
