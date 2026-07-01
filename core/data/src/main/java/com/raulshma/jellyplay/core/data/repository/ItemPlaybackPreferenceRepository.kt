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
     * Upserts a preference row. Only the supplied (non-null) language is
     * written; any pre-existing value for the other language is preserved.
     * If both languages end up null the row is removed.
     *
     * Pass `null` for a language to explicitly clear it.
     */
    suspend fun save(
        scope: PlaybackPrefScope,
        key: String,
        audioLanguage: String? = null,
        subtitleLanguage: String? = null,
    )

    /** Removes the preference row for [scope]/[key], if any. */
    suspend fun delete(scope: PlaybackPrefScope, key: String)
}
