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
 * Picks the image shown on a poster card. Shared by the home rows and the
 * library grid so episodes render identically in both.
 *
 * For episodes the item's own Jellyfin "Primary" image is a landscape scene
 * grab (essentially a still from the episode), which reads as a backdrop in a
 * 2:3 poster slot — other Jellyfin clients instead show the parent series'
 * poster. When [item] is an episode with a [MediaItem.seriesId], prefer the
 * series poster resolved via [seriesPosterResolver]; the episode's own primary
 * image is appended as a fallback so a series with no poster still renders the
 * episode still instead of a blank. The episode's primary blurhash is dropped
 * (it describes the landscape still, not the portrait poster we now show).
 *
 * The series-name title + S# E# chip ([PosterCard]'s `showEpisodeSeriesBadge`)
 * is forced on for any episode card, since the poster now identifies the show
 * rather than the episode.
 *
 * @param itemImageUrl resolves the item's own primary image URL (e.g.
 *  `viewModel.getImageUrl(item.id)`).
 * @param seriesPosterResolver resolves a series' poster URL by id (e.g.
 *  `viewModel.getImageUrl(seriesId)`).
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
    fallbackImageUrls: List<String> = emptyList(),
    showEpisodeSeriesBadge: Boolean = false,
): PosterCardImage {
    return remember(item, itemImageUrl, seriesPosterResolver, showEpisodeSeriesBadge) {
        val isEpisode = item.mediaType == MediaType.EPISODE
        val seriesId = item.seriesId
        val seriesPoster = if (isEpisode && !seriesId.isNullOrBlank()) {
            seriesPosterResolver(seriesId)
        } else ""
        when {
            isEpisode && seriesPoster.isNotBlank() -> PosterCardImage(
                imageUrl = seriesPoster,
                fallbackUrls = if (itemImageUrl.isNotBlank()) listOf(itemImageUrl) + fallbackImageUrls else fallbackImageUrls,
                blurHash = null,
                showSeriesBadge = true,
            )
            isEpisode -> PosterCardImage(
                // No series poster available — keep the episode still and still
                // badge it so the card identifies the show by name.
                imageUrl = itemImageUrl,
                fallbackUrls = fallbackImageUrls,
                blurHash = item.blurHashes.primary,
                showSeriesBadge = true,
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
