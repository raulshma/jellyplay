package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One resolved, playable audio track — the desktop twin of what the Android
 * audio path carries inside a media3 `MediaItem` (URI + metadata).
 *
 * [uri] is EITHER an absolute filesystem path ([isLocalFile] = true — see the
 * V2b mpv note: single-slash `file:/C:/…` URIs are mis-parsed by mpv, so the
 * raw path is handed over instead of the `Uri.fromFile` string Android uses)
 * or the Jellyfin stream URL ([isLocalFile] = false).
 */
data class ResolvedAudioTrack(
    val itemId: String,
    val uri: String,
    val isLocalFile: Boolean,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String?,
    val mediaSourceId: String?,
    val durationMs: Long,
    val normalizationGain: Float?,
    /** Server-reported resume position (10 kHz ticks), if any. */
    val resumePositionTicks: Long?,
)

/**
 * Per-item stream resolution seam for [DesktopAudioQueueManager]. Kept a
 * fun-interface so the queue manager's unit tests can substitute local WAV
 * fixtures without standing up the repository cluster.
 */
fun interface AudioTrackResolver {
    suspend fun resolve(itemId: String, startPositionMs: Long): ResolvedAudioTrack?
}

/**
 * Desktop per-item playback source resolution — a case-for-case port of the
 * Android audio path's `AudioLibraryBrowser.buildPlayableMediaItem` (the
 * semantics source of truth for wave 9B):
 *
 *  1. `getMediaDetail` + `resolveLocalSource` run CONCURRENTLY (Android does
 *     the same via two asyncs);
 *  2. a usable on-disk download wins — played from its absolute file path;
 *  3. otherwise the stream URL is built by the SAME shared
 *     [PlaybackRepository.getStreamUrl] overload with the SAME arguments the
 *     Android browser passes: `useAudioEndpoint = false` (the
 *     `/Videos/{id}/stream?static=true&…` shape, never a second URL form) and
 *     `maxBitrate = tier.targetKbps * 1000` from
 *     [AdaptiveBitrateSelector.resolveBitrate] over the persisted
 *     [StreamingQuality] preference;
 *  4. auth rides in the URL (`api_key` query parameter) exactly like Android —
 *     Android's media3 stack sets NO request headers for audio, so the desktop
 *     loads with empty headers too (MpvDesktopEngine resets its
 *     `http-header-fields` list per load — the multi-server leak fix — and an
 *     empty map leaves it empty).
 *
 * Both detail and local failing yields null — the same condition under which
 * Android's browser returns a null MediaItem (and `AudioPlaybackManager.play`
 * surfaces "Failed to load track"). Android's second, queue-only local
 * fallback (detail fetch failed but a download exists) is covered by step 1's
 * concurrent probe already: `local != null` wins regardless of the detail
 * outcome.
 */
class DesktopAudioSourceResolver(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val adaptiveBitrateSelector: AdaptiveBitrateSelector,
    private val streamingQualityProvider: suspend () -> StreamingQuality,
) : AudioTrackResolver {

    override suspend fun resolve(itemId: String, startPositionMs: Long): ResolvedAudioTrack? =
        coroutineScope {
            val detailJob = async { mediaRepository.getMediaDetail(itemId).getOrNull() }
            val localJob = async { playbackSourceResolver.resolveLocalSource(itemId) }
            val detail = detailJob.await()
            val local = localJob.await()

            if (local != null) {
                val item = detail?.item
                return@coroutineScope ResolvedAudioTrack(
                    itemId = itemId,
                    uri = local.filePath,
                    isLocalFile = true,
                    title = item?.name ?: local.title,
                    artist = item?.albumArtist
                        ?: item?.artistItems?.firstOrNull()?.name
                        ?: "",
                    artistId = item?.artistItems?.firstOrNull()?.id,
                    album = item?.album,
                    mediaSourceId = local.download.mediaSourceId,
                    durationMs = item?.runTimeTicks?.let { it / 10_000 } ?: 0L,
                    normalizationGain = item?.normalizationGain,
                    resumePositionTicks = item?.playbackPositionTicks,
                )
            }

            if (detail == null) return@coroutineScope null
            val source = detail.mediaSources.firstOrNull()
            val tier = adaptiveBitrateSelector.resolveBitrate(streamingQualityProvider())
            val url = playbackRepository.getStreamUrl(
                itemId = itemId,
                mediaSourceId = source?.id ?: "",
                startTimeTicks = if (startPositionMs > 0) startPositionMs * 10_000 else 0L,
                maxBitrate = tier.targetKbps * 1000,
                useAudioEndpoint = false,
            )
            ResolvedAudioTrack(
                itemId = itemId,
                uri = url,
                isLocalFile = false,
                title = detail.item.name,
                artist = detail.item.albumArtist
                    ?: detail.item.artistItems.firstOrNull()?.name
                    ?: "",
                artistId = detail.item.artistItems.firstOrNull()?.id,
                album = detail.item.album,
                mediaSourceId = source?.id,
                durationMs = detail.item.runTimeTicks?.let { it / 10_000 } ?: 0L,
                normalizationGain = detail.item.normalizationGain,
                resumePositionTicks = detail.item.playbackPositionTicks,
            )
        }
}
