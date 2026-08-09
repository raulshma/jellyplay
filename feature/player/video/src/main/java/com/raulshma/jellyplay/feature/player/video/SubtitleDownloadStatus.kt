package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable

/**
 * Lifecycle of a single remote-subtitle download, tracked per subtitle id so
 * the "Get Subtitles" sheet can show a per-row spinner / result without closing.
 *
 * Jellyfin *queues* a remote subtitle download on the server side — the
 * `POST .../Remote/Subtitles/{id}` call returns once the file is saved, but the
 * resulting `MediaStream` may not appear in the item's media info immediately.
 * [SubtitleManager] therefore polls the media detail after the API call returns,
 * and the state transitions reflect that two-phase flow:
 *
 * 1. [DOWNLOADING] — the API call is in flight, then the server is processing.
 * 2. [DOWNLOADED] — the new subtitle stream surfaced in the media info; the row
 *    shows a "Use" affordance to apply it.
 * 3. [DOWNLOADED_DEVICE_ONLY] — the external-provider subtitle was downloaded
 *    and saved durably on-device but the Jellyfin server upload failed (e.g. the
 *    server is unreachable). Usable this session and on replay via the
 *    streaming-subtitle store; not synced to the server.
 * 4. [DELAYED] — the poll budget elapsed without the stream appearing (the
 *    server may still be processing). Non-fatal — the user can retry.
 * 5. [FAILED] — the download API call itself failed (network / server error).
 *
 * Keyed by `RemoteSubtitleInfo.id` in [VideoPlayerUiState.downloadingSubtitles].
 */
@Immutable
enum class SubtitleDownloadState {
    DOWNLOADING,
    DOWNLOADED,
    DOWNLOADED_DEVICE_ONLY,
    DELAYED,
    FAILED,
}

/**
 * Per-subtitle download status. [errorMessage] is surfaced inline on the row
 * (and also via a toast) for [SubtitleDownloadState.FAILED].
 */
@Immutable
data class SubtitleDownloadStatus(
    val subtitleId: String,
    val state: SubtitleDownloadState,
    val errorMessage: String? = null,
)
