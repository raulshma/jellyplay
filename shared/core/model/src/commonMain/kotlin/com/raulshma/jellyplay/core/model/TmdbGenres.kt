package com.raulshma.jellyplay.core.model

/**
 * The static TMDB genre id → name tables for Seerr discover rows. TMDB genre
 * ids are global, stable constants (unchanged for over a decade), so a
 * hardcoded table beats a runtime genre endpoint round-trip the Seerr client
 * would have to add. Names are the canonical English TMDB spellings — the
 * same labels every Seerr/TMDB surface shows.
 */
object TmdbGenres {

    val movie: List<TmdbGenreRef> = listOf(
        TmdbGenreRef(28, "Action"),
        TmdbGenreRef(12, "Adventure"),
        TmdbGenreRef(16, "Animation"),
        TmdbGenreRef(35, "Comedy"),
        TmdbGenreRef(80, "Crime"),
        TmdbGenreRef(99, "Documentary"),
        TmdbGenreRef(18, "Drama"),
        TmdbGenreRef(10751, "Family"),
        TmdbGenreRef(14, "Fantasy"),
        TmdbGenreRef(36, "History"),
        TmdbGenreRef(27, "Horror"),
        TmdbGenreRef(10402, "Music"),
        TmdbGenreRef(9648, "Mystery"),
        TmdbGenreRef(10749, "Romance"),
        TmdbGenreRef(878, "Science Fiction"),
        TmdbGenreRef(10770, "TV Movie"),
        TmdbGenreRef(53, "Thriller"),
        TmdbGenreRef(10752, "War"),
        TmdbGenreRef(37, "Western"),
    )

    val tv: List<TmdbGenreRef> = listOf(
        TmdbGenreRef(10759, "Action & Adventure"),
        TmdbGenreRef(16, "Animation"),
        TmdbGenreRef(35, "Comedy"),
        TmdbGenreRef(80, "Crime"),
        TmdbGenreRef(99, "Documentary"),
        TmdbGenreRef(18, "Drama"),
        TmdbGenreRef(10751, "Family"),
        TmdbGenreRef(10762, "Kids"),
        TmdbGenreRef(9648, "Mystery"),
        TmdbGenreRef(10763, "News"),
        TmdbGenreRef(10764, "Reality"),
        TmdbGenreRef(10765, "Sci-Fi & Fantasy"),
        TmdbGenreRef(10766, "Soap"),
        TmdbGenreRef(10767, "Talk"),
        TmdbGenreRef(10768, "War & Politics"),
        TmdbGenreRef(37, "Western"),
    )

    /** The table for [media]; both lists are small and display-ordered. */
    fun forMedia(media: SeerrRowMedia): List<TmdbGenreRef> = when (media) {
        SeerrRowMedia.MOVIE -> movie
        SeerrRowMedia.TV -> tv
    }
}
