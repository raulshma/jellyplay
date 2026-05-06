package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class MoodPlaylist(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val genreKeywords: List<String>,
    val excludedGenres: List<String> = emptyList(),
    val minRating: Float? = null,
    val sortBy: MoodPlaylistSort = MoodPlaylistSort.RANDOM,
    val maxItems: Int = 50,
    val themeColorHex: String? = null,
)

@Immutable
@Serializable
enum class MoodPlaylistSort {
    RANDOM,
    RATING,
    YEAR_DESC,
    TITLE,
}

object MoodPlaylistsPreset {
    val all = listOf(
        MoodPlaylist(
            id = "happy",
            name = "Happy Vibes",
            emoji = "\uD83C\uDF1F",
            description = "Upbeat and cheerful tracks to brighten your day",
            genreKeywords = listOf("pop", "dance", "disco", "funk", "soul", "happy", "upbeat", "feel-good"),
            minRating = 3.5f,
            themeColorHex = "#FFD700",
        ),
        MoodPlaylist(
            id = "chill",
            name = "Chill Out",
            emoji = "\uD83C\uDF24️",
            description = "Relaxed tunes for unwinding",
            genreKeywords = listOf("chill", "lo-fi", "ambient", "downtempo", "trip-hop", "lounge", "smooth jazz", "acoustic"),
            minRating = 3.5f,
            themeColorHex = "#87CEEB",
        ),
        MoodPlaylist(
            id = "energetic",
            name = "Energetic",
            emoji = "\u26A1",
            description = "High-energy tracks to fuel your activity",
            genreKeywords = listOf("rock", "metal", "punk", "edm", "electronic", "dance", "techno", "drum and bass", "hardcore"),
            minRating = 3.5f,
            themeColorHex = "#FF4500",
        ),
        MoodPlaylist(
            id = "focus",
            name = "Deep Focus",
            emoji = "\uD83E\uDDD8",
            description = "Concentration-enhancing instrumental music",
            genreKeywords = listOf("classical", "ambient", "instrumental", "minimal", "post-rock", "soundtrack", "study", "focus"),
            excludedGenres = listOf("metal", "punk", "hardcore"),
            minRating = 3.5f,
            themeColorHex = "#9370DB",
        ),
        MoodPlaylist(
            id = "workout",
            name = "Workout",
            emoji = "\uD83D\uDCAA",
            description = "Pump-up tracks for the gym",
            genreKeywords = listOf("hip-hop", "trap", "edm", "rock", "metal", "pop", "dance", "workout", "gym"),
            minRating = 3.5f,
            themeColorHex = "#DC143C",
        ),
        MoodPlaylist(
            id = "sad",
            name = "Melancholy",
            emoji = "\uD83C\uDFB5",
            description = "Emotional and reflective tracks",
            genreKeywords = listOf("blues", "sad", "melancholic", "indie folk", "ballad", "piano", "acoustic", "emo"),
            minRating = 3.5f,
            themeColorHex = "#4682B4",
        ),
        MoodPlaylist(
            id = "romantic",
            name = "Romantic",
            emoji = "\uD83D\uDC95",
            description = "Love songs and romantic melodies",
            genreKeywords = listOf("r&b", "soul", "romantic", "love", "ballad", "jazz", "neo-soul", "soft rock"),
            minRating = 3.5f,
            themeColorHex = "#FF69B4",
        ),
        MoodPlaylist(
            id = "party",
            name = "Party Time",
            emoji = "\uD83C\uDF89",
            description = "Crowd-pleasers for celebrations",
            genreKeywords = listOf("pop", "dance", "hip-hop", "disco", "funk", "house", "party", "club"),
            minRating = 3.5f,
            themeColorHex = "#FF8C00",
        ),
        MoodPlaylist(
            id = "sleep",
            name = "Sleep",
            emoji = "\uD83C\uDF19",
            description = "Gentle sounds for rest",
            genreKeywords = listOf("ambient", "classical", "new age", "sleep", "meditation", "nature", "piano", "calm"),
            excludedGenres = listOf("rock", "metal", "punk", "hip-hop", "trap"),
            minRating = 3.0f,
            themeColorHex = "#191970",
        ),
        MoodPlaylist(
            id = "driving",
            name = "Late Night Drive",
            emoji = "\uD83D\uDE97",
            description = "Perfect soundtrack for the road",
            genreKeywords = listOf("synthwave", "indie", "alternative", "rock", "electronic", "dream pop", "shoegaze"),
            minRating = 3.5f,
            themeColorHex = "#4B0082",
        ),
    )
}
