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
 * A preference row stores an optional audio and/or subtitle language. Either
 * may be `null` ("inherit"); [save] therefore upserts a row preserving the
 * field that is not being updated, and a row whose languages are both null
 * is deleted to keep the table tidy.
 */
interface ItemPlaybackPreferenceRepository {

    /** Returns the stored preference for [scope]/[key], or null if none. */
    suspend fun get(scope: PlaybackPrefScope, key: String): ItemPlaybackPreference?

    /**
     * Upserts a preference row. A language argument has three states:
     *  - a value: remember it;
     *  - null: leave any existing value for that language untouched;
     * Use [clearAudioLanguage]/[clearSubtitleLanguage] to *explicitly* clear a
     * single field (passing `null` here does NOT clear — it means "not provided").
     * If both languages end up null the row is removed to keep the table tidy.
     */
    suspend fun save(
        scope: PlaybackPrefScope,
        key: String,
        audioLanguage: String? = null,
        subtitleLanguage: String? = null,
    )

    /** Clears the audio-language field for [scope]/[key] (the subtitle field is preserved). */
    suspend fun clearAudioLanguage(scope: PlaybackPrefScope, key: String)

    /** Clears the subtitle-language field for [scope]/[key] (the audio field is preserved). */
    suspend fun clearSubtitleLanguage(scope: PlaybackPrefScope, key: String)

    /** Removes the preference row for [scope]/[key], if any. */
    suspend fun delete(scope: PlaybackPrefScope, key: String)
}
