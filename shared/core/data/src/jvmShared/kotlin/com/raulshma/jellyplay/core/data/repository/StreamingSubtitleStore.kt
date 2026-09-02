package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.findSavedSubtitleStreamIndex
import com.raulshma.jellyplay.core.model.subtitle.matchesDeletedServerStream
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

    /**
     * Records the server `MediaStream.index` the upload of [saved] produced,
     * so a later server-side delete can purge exactly this copy and playback
     * load can skip entries whose stream no longer exists. No-op if the entry
     * vanished between save and mark.
     */
    suspend fun markServerStreamIndex(itemId: String, saved: SavedSubtitle, index: Int)

    /** Removes every saved subtitle for [itemId] (the whole item dir). */
    suspend fun clear(itemId: String)

    /**
     * Records the server `MediaStream.index` the just-completed upload of
     * [saved] produced, by locating it among [streamsAfterUpload] — so a later
     * server-side delete can purge exactly this local copy and playback load
     * can skip entries whose stream no longer exists.
     * No-op when nothing matches (upload not reflected yet / device-only
     * download).
     */
    suspend fun attributeUploadedSubtitle(
        itemId: String,
        saved: SavedSubtitle,
        streamsAfterUpload: List<MediaStream>,
        preUploadExternalIndices: Set<Int>,
    ) {
        findSavedSubtitleStreamIndex(streamsAfterUpload, saved, preUploadExternalIndices)
            ?.let { markServerStreamIndex(itemId, saved, it) }
    }

    /**
     * Deletes every local copy of the server subtitle stream just removed at
     * [index]: exact `serverStreamIndex` match when recorded, legacy entries
     * attribute-match the deleted stream. Without this purge, playback would
     * re-side-load the copy and the deleted track resurrects forever.
     */
    suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?) {
        for (entry in loadAll(itemId)) {
            if (entry.matchesDeletedServerStream(index, deletedStream)) delete(itemId, entry)
        }
    }
}
