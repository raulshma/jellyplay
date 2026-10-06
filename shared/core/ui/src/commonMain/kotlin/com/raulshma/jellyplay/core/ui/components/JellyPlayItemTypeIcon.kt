package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Photo

/**
 * The companion plugin's `itemType` wire string → its icon, loosely matched
 * (the contract's own "loosely matched" idiom): TV-shaped types share the
 * television glyph, the audio spellings the music glyph, unknowns fall back
 * to the movie glyph (the generic video item). One home for every surface
 * that renders a plugin item type — the My-ratings rows, Your-watching's
 * top items, the admin analytics' top items and sessions ledger — so the
 * same wire type never renders two different glyphs across screens.
 */
fun jellyPlayItemTypeIcon(itemType: String): ImageVector = when (itemType.lowercase()) {
    "movie" -> Tabler.Outline.Movie
    "series", "season", "episode" -> Tabler.Outline.DeviceTv
    "audio", "music", "musicalbum", "musicartist", "musictrack", "album", "track" -> Tabler.Outline.Music
    "book", "audiobook" -> Tabler.Outline.Book
    "photo", "photoalbum" -> Tabler.Outline.Photo
    else -> Tabler.Outline.Movie
}
