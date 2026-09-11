package com.raulshma.jellyplay.feature.music.collection

import com.raulshma.jellyplay.core.data.util.ImageUrlProvider

/**
 * The music screens' one image-url seam: dense-list artwork always resolves
 * at [ImageUrlProvider.MUSIC_MAX_WIDTH]. Collapses the per-ViewModel
 * `getImageUrl` copies (artists/albums/tracks/browse) onto one declaration;
 * ViewModels still expose it under their own names (pinned by
 * `MusicListViewModelsTest`).
 */
fun ImageUrlProvider.musicArtUrl(itemId: String): String =
    getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
