package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * The JVM adapter over core:data's `DownloadRepository` single — the unified
 * [TrackDownloadStatusWindow] the audio player and the album screen consume.
 * Consolidates the two feature-local seams (player-audio's AudioTrackDownloads,
 * music's MusicTrackDownloads) that used to bridge this same repository into
 * per-feature interfaces; bound in DataKoinModule.
 *
 * Id honesty (the pin the player-audio window test carried, ported to
 * JvmTrackDownloadStatusWindowTest): `downloadsFor` answers for EVERY
 * requested id, in input order, via one per-id flow per id — the player's
 * historical `getDownloadByMediaItemIdFlow` shape — with null rows filtered.
 * Declared divergence from the music twin this replaces: the album screen
 * used to read ONE `getDownloadsByMediaItemIdsFlow` IN-query for its ~10-20
 * tracks; it now combines the same count of id-scoped flows. Same rows, same
 * emissions on the same invalidations — N narrow Room observers instead of
 * one IN query — never the whole table either way.
 */
internal class JvmTrackDownloadStatusWindow(
    private val downloadRepository: DownloadRepository,
) : TrackDownloadStatusWindow {
    override val isSupported: Boolean = true
    override fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>> =
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ids.map { downloadRepository.getDownloadByMediaItemIdFlow(it) }) { rows ->
                rows.filterNotNull()
            }
        }
    override suspend fun remove(downloadId: String) {
        downloadRepository.deleteDownload(downloadId)
    }
}
