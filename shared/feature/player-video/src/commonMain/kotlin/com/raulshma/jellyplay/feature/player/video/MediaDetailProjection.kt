package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.model.currentPlatform
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isMusicTrack
import com.raulshma.jellyplay.core.model.volumeBucketFor
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The media-detail application cluster extracted verbatim from
 * [VideoPlayerViewModel] (the [MediaContentProjector] twin — a projection,
 * not a decision home): what happens when a fresh or refreshed
 * [MediaDetail] lands — the ordered fan-out
 * detail-holder → chapters → media slice → episode adoption →
 * companion-lyrics fetch → volume memory, plus the refreshed-detail
 * choreography a subtitle download/upload re-sync runs
 * ([MediaDetailRefresh] → [applyRefreshedDetail]). Order is load-bearing:
 * the VM called these bodies in exactly this sequence; nothing here may
 * reorder them.
 *
 * Every uiState write the cluster performed stays a narrow constructor
 * lambda (no [VideoPlayerUiState] handle — the god-count ratchet is
 * unmoved); the volume-memory policy ladder is [VolumeMemoryPolicy]'s.
 */
internal class MediaDetailProjection(
    private val scope: CoroutineScope,
    private val lyricsRepository: LyricsRepository,
    /** Desktop-only volume memory reads/writes (the VM's `stores.volumeProfile`). */
    private val volumeProfileStore: VolumeProfileStore,
    /** Writes the VM's `@Volatile` detail holder (the getDetail seam's source). */
    private val setDetail: (MediaDetail) -> Unit,
    /** Chapters are a top-level uiState field — the one direct state write this cluster had. */
    private val setChapters: (List<ChapterInfo>) -> Unit,
    /** The media-slice write ([MediaContentProjector.onDetail]). */
    private val onDetail: (detail: MediaDetail, artworkUrl: String) -> Unit,
    /** The media slice's artwork URL (the VM's `getImageUrl(id, 400)`). */
    private val artworkUrl: (itemId: String) -> String,
    /** The episode-slice adoption ([EpisodeContinuationController.adoptSeasonOf]). */
    private val adoptSeasonOf: (MediaDetail) -> Unit,
    /** Lyrics fold into the media slice ([MediaContentProjector.onLyrics]). */
    private val onLyrics: (List<LyricsLine>) -> Unit,
    /** The refreshed-detail re-sync ([MediaContentProjector.onDetailRefreshed]). */
    private val onDetailRefreshed: (MediaDetailRefresh) -> Unit,
    /** The active engine (volume-memory restore + capture arm). */
    private val getEngine: () -> MediaEngine?,
) {

    fun applyDetail(detail: MediaDetail) {
        setDetail(detail)
        setChapters(detail.chapters)
        onDetail(detail, artworkUrl(detail.item.id))
        adoptSeasonOf(detail)
        fetchCompanionLyrics(detail)
        applyVolumeMemory(detail)
    }

    fun applyRefreshedDetail(refresh: MediaDetailRefresh) {
        onDetailRefreshed(refresh)
    }

    /**
     * per-content-type volume memory for the surfaces where the app
     * owns a volume scalar — on desktop that is the mpv engine's own volume
     * property. Restores the bucket's remembered level at item start and arms
     * the user-change capture (programmatic fades never fire it — see
     * [VolumeMemoryPolicy]). Android video stays on the system
     * `STREAM_MUSIC` model: the policy ladder returns nothing to restore and
     * never captures there.
     */
    private fun applyVolumeMemory(detail: MediaDetail) {
        if (currentPlatform != PlatformKind.DESKTOP) return
        val engine = getEngine() ?: return
        val bucket = volumeBucketFor(detail.item.mediaType)
        scope.launch {
            val slice = volumeProfileStore.volumeProfile.first()
            VolumeMemoryPolicy.restoreLevel(
                platform = PlatformKind.DESKTOP,
                rememberEnabled = slice.rememberVolumePerContentType,
                storedLevel = slice.volumeFor(bucket),
                enginePresent = getEngine() != null,
            )?.let { level ->
                engine.setVolume(level, isUserChange = false)
            }
            engine.onUserVolumeChange =
                if (VolumeMemoryPolicy.shouldCapture(
                        PlatformKind.DESKTOP,
                        slice.rememberVolumePerContentType,
                    )
                ) {
                    { level -> scope.launch { volumeProfileStore.setVolume(bucket, level) } }
                } else {
                    null
                }
        }
    }

    private fun fetchCompanionLyrics(detail: MediaDetail) {
        val item = detail.item
        if (item.mediaType.isAudioType || item.mediaType.isMusicTrack) {
            scope.launch {
                val artist = item.albumArtist ?: item.artistItems.firstOrNull()?.name ?: ""
                val durationSec = (item.runTimeTicks ?: 0L) / 10_000_000L
                val lyricsResult = lyricsRepository.getLyricsWithFallback(
                    itemId = item.id,
                    artistName = artist,
                    trackName = item.name,
                    duration = durationSec.toDouble(),
                ).getOrNull()
                onLyrics(lyricsResult?.lines ?: emptyList())
            }
        } else {
            onLyrics(emptyList())
        }
    }
}
