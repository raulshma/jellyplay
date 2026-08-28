package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import java.io.File

/**
 * Durable per-item subtitle storage for **streaming** (non-downloaded) items.
 *
 * The offline-subtitle pipeline (`DownloadArtifacts` + `OfflineSubtitleManifest`)
 * keys subtitles off a downloaded media file's directory, which streaming items
 * don't have. This store fills that gap: external-provider subtitles (OpenSubtitles,
 * Wyzie) downloaded while streaming are persisted under
 * `<appDataDir>/streaming-subtitles/<itemId>/` — Android `filesDir`, desktop
 * appdata dir; the durable app-data dir, not the disposable cache dir used
 * for the one-shot side-load — so they survive replay
 * and remain usable when the Jellyfin server is unreachable.
 *
 * The store is the single owner of that directory; everything else goes through
 * this interface so the on-disk layout stays consistent.
 */
interface StreamingSubtitleStore {

    /**
     * Persists [bytes] as a subtitle for [itemId] and records it in the manifest.
     * Returns the resulting [SavedSubtitle] (with the resolved relative file path).
     *
     * If a subtitle with the same `provider` + `providerSubtitleId` already
     * exists, its file is overwritten and the manifest entry updated in place.
     */
    suspend fun save(
        itemId: String,
        provider: com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind,
        providerSubtitleId: String,
        fileName: String,
        language: String?,
        codec: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
        bytes: ByteArray,
    ): SavedSubtitle

    /** All saved subtitles for [itemId], keyed off the on-disk manifest. */
    suspend fun loadAll(itemId: String): List<SavedSubtitle>

    /** Resolves the on-disk [File] for a saved subtitle. */
    suspend fun fileFor(itemId: String, saved: SavedSubtitle): File

    /** Deletes one saved subtitle (file + manifest entry) for [itemId]. */
    suspend fun delete(itemId: String, saved: SavedSubtitle)

    /** Removes every saved subtitle for [itemId] (the whole item dir). */
    suspend fun clear(itemId: String)
}
