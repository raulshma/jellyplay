package com.raulshma.jellyplay.feature.music.collection

import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * The music screens' one image-url seam: dense-list artwork always resolves
 * at [ImageUrlProvider.MUSIC_MAX_WIDTH]. Collapses the per-ViewModel
 * `getImageUrl` copies (artists/albums/tracks/browse) onto one declaration;
 * ViewModels still expose it under their own names (pinned by
 * `MusicListViewModelsTest`).
 */
fun ImageUrlProvider.musicArtUrl(itemId: String): String =
    getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)

/**
 * The item-based variant over the [ArtworkUrls] resolver: same
 * [ImageUrlProvider.MUSIC_MAX_WIDTH] contract, with the item's own Primary
 * tag carried as the `?tag=` cache-buster (own-id target preserved — track
 * rows keep resolving through the track id exactly as before).
 */
fun ArtworkUrls.musicArtUrl(item: MediaItem): String =
    poster(item, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
