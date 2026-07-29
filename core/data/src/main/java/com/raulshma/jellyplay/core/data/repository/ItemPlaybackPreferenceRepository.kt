package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope

/**
 * Per-item / per-series playback-language preferences.
 *
 * Defined in `core:data` so feature modules depend on a domain-facing
 * interface rather than `core:database`'s DAOs/entities directly — mirroring
 * the [SeenMediaRepository] boundary. Backed by `ItemPlaybackPreferenceDao`.
 *
 * A preference row stores an optional audio and/or subtitle language plus an
 * optional dialogue-boost strength. Any field may be `null` ("inherit");
 * [save] therefore upserts a row preserving the fields that are not being
 * updated, and a row with every field null is deleted to keep the table tidy.
 */
interface ItemPlaybackPreferenceRepository {

    /** Returns the stored preference for [scope]/[key], or null if none. */
    suspend fun get(scope: PlaybackPrefScope, key: String): ItemPlaybackPreference?

    /**
     * Upserts a preference row. Each nullable argument follows the convention:
     *  - a value: remember it;
     *  - null: leave any existing value for that field untouched.
     * Use [clearAudioLanguage]/[clearSubtitleLanguage]/
     * [clearDialogueBoostStrength] to *explicitly* clear a single field
     * (passing `null` here does NOT clear — it means "not provided").
     * If all fields end up null the row is removed to keep the table tidy.
     *
     * The subtitle role fields ([subtitleForced], [subtitleHearingImpaired]) pin
     * the preferred subtitle's role alongside [subtitleLanguage]. They are cleared
     * together with the language by [clearSubtitleLanguage].
     */
    suspend fun save(
        scope: PlaybackPrefScope,
        key: String,
        audioLanguage: String? = null,
        subtitleLanguage: String? = null,
        subtitleForced: Boolean? = null,
        subtitleHearingImpaired: Boolean? = null,
        dialogueBoostStrength: com.raulshma.jellyplay.core.model.EffectStrength? = null,
    )

    /** Clears the audio-language field for [scope]/[key] (other fields are preserved). */
    suspend fun clearAudioLanguage(scope: PlaybackPrefScope, key: String)

    /**
     * Clears the subtitle-language field for [scope]/[key], together with its
     * pinned role ([subtitleForced] / [subtitleHearingImpaired]); the audio and
     * dialogue-boost fields are preserved.
     */
    suspend fun clearSubtitleLanguage(scope: PlaybackPrefScope, key: String)

    /** Clears the dialogue-boost field for [scope]/[key] (other fields are preserved). */
    suspend fun clearDialogueBoostStrength(scope: PlaybackPrefScope, key: String)

    /** Removes the preference row for [scope]/[key], if any. */
    suspend fun delete(scope: PlaybackPrefScope, key: String)
}
