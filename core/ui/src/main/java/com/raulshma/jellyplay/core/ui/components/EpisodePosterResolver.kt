package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * Resolved image inputs for a [PosterCard]. See [rememberEpisodeCardImage].
 */
data class PosterCardImage(
    val imageUrl: String,
    val fallbackUrls: List<String>,
    val blurHash: String?,
    val showSeriesBadge: Boolean,
)

/**
 * Series-artwork fallback for a season whose own poster isn't available (e.g.
 * a season whose library scan never generated season artwork). Returns the
 * parent series' poster URL (resolved via [resolve]) when [item] is a season
 * with a [MediaItem.seriesId]; an empty list otherwise. Used by card/list
 * paths that don't go through [rememberEpisodeCardImage] (list rows, thumb card).
 */
fun MediaItem.seriesImageFallback(resolve: (String) -> String): List<String> =
    if (mediaType == MediaType.SEASON) {
        seriesId?.let { listOf(resolve(it)) } ?: emptyList()
    } else {
        emptyList()
    }

/**
 * [remember]-wrapped [seriesImageFallback] for the list/thumb card paths. The
 * memo is keyed on the only fields that affect the result (the item's type and
 * [MediaItem.seriesId]) so a prefetch merge that produces a new item reference
 * doesn't invalidate the fallback list each pass. Returns an empty list for
 * non-seasons, so callers can pass it unconditionally as a `fallbackUrls` arg.
 */
@Composable
fun MediaItem.rememberSeriesImageFallback(getImageUrl: (String) -> String): List<String> =
    remember(mediaType, seriesId) { seriesImageFallback(getImageUrl) }

/**
 * Picks the image shown on a poster card. Shared by the home rows and the
 * library grid so episodes and seasons render identically in both.
 *
 * For episodes the item's own Jellyfin "Primary" image is a landscape scene
 * grab (essentially a still from the episode), which reads as a backdrop in a
 * 2:3 poster slot — other Jellyfin clients instead show the parent series'
 * poster. When [item] is an episode with a [MediaItem.seriesId], the fallback
 * chain is: series Primary (poster) → series Backdrop → episode own Primary.
 * A series that has a Backdrop but no Primary (common for brand-new additions
 * whose library scan has not yet generated a poster crop) therefore still
 * renders an identifiable image instead of a blank card. The episode's own
 * primary image is the last-resort fallback so a series with no images at all
 * still renders the episode still. The episode's primary blurhash is dropped
 * (it describes the landscape still, not the portrait poster we now show).
 *
 * For seasons the season's own Primary stays the card image (season covers are
 * proper posters), but the parent series' poster is appended to the URL
 * fallback chain so a season without its own artwork (or whose poster 404s)
 * still shows the show's poster instead of a blank card.
 *
 * The series-name title + S# E# chip ([PosterCard]'s `showEpisodeSeriesBadge`)
 * is forced on for any episode card, since the poster now identifies the show
 * rather than the episode.
 *
 * @param itemImageUrl resolves the item's own primary image URL (e.g.
 *  `viewModel.getImageUrl(item.id)`).
 * @param seriesPosterResolver resolves a series' poster URL by id (e.g.
 *  `viewModel.getImageUrl(seriesId)`).
 * @param seriesBackdropResolver resolves a series' backdrop URL by id, used as
 *  the middle fallback when a series has no Primary image. Pass `{ "" }` to
 *  skip the backdrop step.
 * @param fallbackImageUrls builder-supplied fallbacks (e.g. album art for
 *  music). Empty by default.
 * @param showEpisodeSeriesBadge the caller's default badge preference; ignored
 *  for episodes (always on).
 */
@Composable
fun rememberEpisodeCardImage(
    item: MediaItem,
    itemImageUrl: String,
    seriesPosterResolver: (String) -> String,
    seriesBackdropResolver: (String) -> String = { "" },
    fallbackImageUrls: List<String> = emptyList(),
    showEpisodeSeriesBadge: Boolean = false,
): PosterCardImage {
    return remember(item, itemImageUrl, seriesPosterResolver, seriesBackdropResolver, showEpisodeSeriesBadge) {
        val isEpisode = item.mediaType == MediaType.EPISODE
        val isSeason = item.mediaType == MediaType.SEASON
        val seriesId = item.seriesId
        val usesSeriesImage = isEpisode || isSeason
        val seriesPoster = if (usesSeriesImage && !seriesId.isNullOrBlank()) {
            seriesPosterResolver(seriesId)
        } else ""
        val seriesBackdrop = if (usesSeriesImage && !seriesId.isNullOrBlank() && seriesPoster.isBlank()) {
            seriesBackdropResolver(seriesId)
        } else ""
        when {
            isEpisode && seriesPoster.isNotBlank() -> PosterCardImage(
                imageUrl = seriesPoster,
                fallbackUrls = if (itemImageUrl.isNotBlank()) listOf(itemImageUrl) + fallbackImageUrls else fallbackImageUrls,
                blurHash = null,
                showSeriesBadge = true,
            )
            isEpisode && seriesBackdrop.isNotBlank() -> PosterCardImage(
                // Series has a Backdrop but no Primary (common for freshly-added
                // series whose library scan hasn't generated a poster yet). Use
                // the backdrop as the card image and fall through to the episode
                // still if the backdrop URL also 404s at fetch time.
                imageUrl = seriesBackdrop,
                fallbackUrls = if (itemImageUrl.isNotBlank()) listOf(itemImageUrl) + fallbackImageUrls else fallbackImageUrls,
                blurHash = null,
                showSeriesBadge = true,
            )
            isEpisode -> PosterCardImage(
                // No series poster or backdrop available — keep the episode still
                // and still badge it so the card identifies the show by name.
                imageUrl = itemImageUrl,
                fallbackUrls = fallbackImageUrls,
                blurHash = item.blurHashes.primary,
                showSeriesBadge = true,
            )
            isSeason && itemImageUrl.isBlank() && seriesPoster.isNotBlank() -> PosterCardImage(
                // No season artwork at all — show the series poster outright.
                imageUrl = seriesPoster,
                fallbackUrls = if (seriesBackdrop.isNotBlank()) listOf(seriesBackdrop) + fallbackImageUrls else fallbackImageUrls,
                blurHash = null,
                showSeriesBadge = false,
            )
            isSeason && itemImageUrl.isBlank() && seriesBackdrop.isNotBlank() -> PosterCardImage(
                imageUrl = seriesBackdrop,
                fallbackUrls = fallbackImageUrls,
                blurHash = null,
                showSeriesBadge = false,
            )
            isSeason -> PosterCardImage(
                // Season poster first; the parent series poster/backdrop are the
                // fetch-time fallbacks when the season's own image 404s.
                imageUrl = itemImageUrl,
                fallbackUrls = buildList {
                    if (seriesPoster.isNotBlank()) add(seriesPoster)
                    if (seriesBackdrop.isNotBlank()) add(seriesBackdrop)
                    addAll(fallbackImageUrls)
                },
                blurHash = item.blurHashes.primary,
                showSeriesBadge = false,
            )
            else -> PosterCardImage(
                imageUrl = itemImageUrl,
                fallbackUrls = fallbackImageUrls,
                blurHash = item.blurHashes.primary,
                showSeriesBadge = showEpisodeSeriesBadge,
            )
        }
    }
}
