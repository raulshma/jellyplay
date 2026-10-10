package com.raulshma.jellyplay.navigation.playbackhost

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.navigation.ExternalPlaybackOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The external-player reporting contract: the launch resolution for one
 * hand-off (download-vs-stream via [PlaybackSourceResolver] + the
 * external-subtitle payload + the preferred-app targeting) and the server
 * report pair that brackets the external playback session (start on launch,
 * stop with the parsed [ExternalPlaybackOutcome] on result). Consumed by
 * [ExternalPlayerHost] through the shell's
 * [com.raulshma.jellyplay.navigation.rememberExternalPlayerLauncherHost].
 *
 * Reports are fire-and-forget on this class's OWN scope
 * ([SupervisorJob] + [Dispatchers.Main.immediate] — the app-module
 * single-owned-scope pattern), not on any activity/ViewModel scope. Declared
 * behaviour delta of that ownership: a report may outlive the shell
 * activity's destroy. The stop report fires from the ActivityResult callback
 * — the activity is alive at that moment — and an in-flight report
 * *completing* after a subsequent finish() is acceptable and strictly better
 * than cancelling it (the server keeps a consistent session tail either
 * way). The stop report's [withTimeout] bounds the wait at 5 s.
 *
 * The preferred-app read goes straight to the owning slice
 * ([PlaybackStore.playback], read at decide time) — the narrowest existing
 * source of `preferredExternalPlayer`; the shell's merged preference
 * pipeline folds the same DataStore field into [com.raulshma.jellyplay.core.model.MainPreferences],
 * so the value read here is the value the settings surface wrote.
 */
class ExternalPlayerReports(
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val playbackStore: PlaybackStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Builds an [ExternalPlayerLaunch] for the given item, resolving either a
     * completed local download or the server stream URL. Works for both regular
     * videos ([com.raulshma.jellyplay.core.ui.navigation.Route.VideoPlayer]) and Live TV channels
     * ([com.raulshma.jellyplay.core.ui.navigation.Route.LiveTvChannelPlayer]) since the underlying repository calls
     * handle channel ids identically to the internal-engine path.
     *
     * The launch advertises `return_result`, so the app-level
     * `ActivityResultLauncher` in [com.raulshma.jellyplay.navigation.JellyPlayApp]
     * can read the external player's result and credit watched progress
     * via [reportExternalPlaybackStopped]. Beyond the ACTION_VIEW fold, the
     * launch carries the external-subtitle payload (server delivery URLs,
     * resolved through the shared
     * [com.raulshma.jellyplay.core.data.repository.PlaybackRepository.resolveSubtitleStreamUrl]
     * ladder the in-app side-load path uses) and the user's
     * preferred external app (the targeting the host applies at launch).
     * The launch construction itself — the intent, the extras vocabulary and
     * the per-launch playSessionId — is the pure [externalPlayerLaunch] fold
     * in navigation/playbackhost (beside [ExternalPlayerHost]); this member
     * owns the resolver/subtitle injection.
     */
    suspend fun buildExternalPlayerLaunch(
        request: ExternalPlayerRequest,
    ): ExternalPlayerLaunch? {
        // The download-vs-stream fork lives once in PlaybackSourceResolver: a
        // completed download with an existing file resolves to a `file://` URI
        // (title from the offline item, falling back to the download name),
        // else the resolver fetches `getMediaDetail` and builds the stream URL.
        // The resolver silently falls back to streaming when a COMPLETED row's
        // file has vanished from disk.
        val resolved = playbackSourceResolver.resolvePlaybackSource(
            itemId = request.itemId,
            mediaSourceId = request.mediaSourceId,
            startPositionTicks = request.startPositionTicks,
        ) ?: return null

        val preferredApp = playbackStore.playback.value.preferredExternalPlayer
        // Subtitles ride server-side streams only: a local download plays its
        // own sidecars, and the external player demuxes the container's
        // embedded tracks itself.
        val (subtitles, resolvedUrl) = when (resolved) {
            is ResolvedPlaybackSource.Stream -> resolved.mediaSource
                ?.let { source -> buildExternalSubtitles(request.itemId, source, request.subtitleStreamIndex) }
                .orEmpty() to resolved.url
            is ResolvedPlaybackSource.Local -> emptyList<ExternalSubtitle>() to resolved.uri
        }

        return externalPlayerLaunch(
            itemId = request.itemId,
            resolvedUrl = resolvedUrl,
            title = resolved.title,
            startPositionTicks = request.startPositionTicks,
            subtitles = subtitles,
            preferredApp = preferredApp,
        )
    }

    /**
     * Builds the external hand-off's [ExternalSubtitle] payload from one
     * [MediaSource]'s subtitle streams — resolving every stream through
     * [PlaybackRepository.resolveSubtitleStreamUrl] with `includeEmbedded =
     * false`: embedded streams are left to the target player's container
     * demux (side-loading them would duplicate each one), image codecs come
     * back null (the subtitle endpoint cannot serve them), and a
     * server-issued `deliveryUrl` resolves verbatim.
     */
    private fun buildExternalSubtitles(
        itemId: String,
        source: MediaSource,
        selectedStreamIndex: Int?,
    ): List<ExternalSubtitle> = source.mediaStreams
        .filter { it.type == StreamType.SUBTITLE }
        .mapNotNull { stream ->
            val url = playbackRepository.resolveSubtitleStreamUrl(
                stream = stream,
                itemId = itemId,
                mediaSourceId = source.id,
                includeEmbedded = false,
            ) ?: return@mapNotNull null
            ExternalSubtitle(
                url = url,
                name = stream.displayName,
                filename = stream.language,
                isSelected = stream.index == selectedStreamIndex,
            )
        }

    fun reportExternalPlaybackStart(playerLaunch: ExternalPlayerLaunch) {
        scope.launch {
            runCatchingRethrowingCancellation {
                playbackRepository.reportPlaybackStart(
                    com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        startPositionTicks = playerLaunch.startPositionTicks,
                    )
                )
            }
        }
    }

    fun reportExternalPlaybackStopped(playerLaunch: ExternalPlayerLaunch, outcome: ExternalPlaybackOutcome) {
        scope.launch {
            runCatchingRethrowingCancellation {
                withTimeout(5_000) {
                    // Completion marks played explicitly — the server's
                    // %-watched stop rule cannot fire for the contracts that
                    // report completion without a position (MPV/mpvKt). The
                    // stop report still ends the playback session.
                    if (outcome is ExternalPlaybackOutcome.Completed) {
                        mediaRepository.markPlayed(playerLaunch.itemId)
                    }
                    playbackRepository.reportPlaybackStopped(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        positionTicks = when (outcome) {
                            is ExternalPlaybackOutcome.Completed ->
                                outcome.positionTicks.takeIf { it > 0 } ?: playerLaunch.startPositionTicks
                            is ExternalPlaybackOutcome.StoppedAt -> outcome.positionTicks
                            is ExternalPlaybackOutcome.Cancelled -> playerLaunch.startPositionTicks
                        },
                    )
                }
            }
        }
    }
}
