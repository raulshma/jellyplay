package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

/**
 * The settings-sync wire payloads for the `playlists` namespace — one DTO per
 * synced key family (`smart/{id}`, `mood/{id}`, `moodpref/{playlistId}`). The
 * definitions sync WHOLE ROWS: criteria/keyword definitions only, no cached
 * item lists (those are derived per library at query time and were never
 * local state). Enum-ish columns ride their raw names ([sortBy] mirrors
 * [MoodPlaylistSort]); the JSON definition blobs ([SmartPlaylistPayload.criteriaJson],
 * [MoodPlaylistPayload.genreKeywordsJson]/[excludedGenresJson]) ride as
 * opaque strings the receiving device decodes with its own (possibly newer)
 * criteria parser — an unknown criterion degrades there, not on the wire.
 *
 * Deliberately NOT the Room entities (those are persistence-tied) nor the
 * domain [MoodPlaylist] (that nests parsed keyword lists and evolves freely):
 * these are the stable wire forms, mapped explicitly by the sync adapter.
 * Install-local columns are absent; [SmartPlaylistPayload.id]/[MoodPlaylistPayload.id]/
 * [MoodPlaylistPreferencePayload.playlistId] are the one stable identity each
 * row carries across devices. `updatedAt` rides INFORMATIONALLY — arbitration
 * is the sync engine's server-side wire stamp. Fields default so an older
 * payload (missing a column added later) still decodes.
 *
 * MIXED-VERSION DOWNGRADE STANCE (accepted, not solved here): decoding runs
 * with `ignoreUnknownKeys`, so a field a NEWER app writes is silently dropped
 * by an OLDER app's decode — and that device's re-encoded row then reads
 * dirty against its mirror and gets re-pushed STRIPPED (with a fresh LWW
 * stamp), overwriting the newer device's full row. The contract: adding a
 * field to any payload below requires all of a user's devices to run a
 * version that knows it, or the user accepts the strip-on-adopt behavior.
 * Unknown-field preservation is deliberately NOT implemented (no
 * raw-passthrough envelope).
 */
@Serializable
data class SmartPlaylistPayload(
    val id: String,
    val name: String,
    val criteriaJson: String,
    val maxItems: Int = 50,
    val sortBy: String = "RANDOM",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class MoodPlaylistPayload(
    val id: String,
    val name: String,
    val emoji: String = "",
    val description: String = "",
    val genreKeywordsJson: String,
    val excludedGenresJson: String? = null,
    val minRating: Float? = null,
    val sortBy: String = "RANDOM",
    val maxItems: Int = 50,
    val themeColorHex: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class MoodPlaylistPreferencePayload(
    val playlistId: String,
    val isEnabled: Boolean = true,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val updatedAt: Long = 0L,
)
