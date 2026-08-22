package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class AudioNormalizationMode(val displayName: String) {
    NONE("None"),
    DYNAMIC("Dynamic Compression"),
    TRACK("Track Normalization"),
    ALBUM("Album Normalization"),
}

@Immutable
@Serializable
enum class ChannelMixMode(val displayName: String) {
    AUTO("Auto"),
    STEREO_DOWNMIX("Stereo Downmix"),
    SURROUND_UPMIX("Surround Upmix"),
    MONO("Mono"),
}

@Immutable
@Serializable
enum class EqualizerPreset(val displayName: String) {
    FLAT("Flat"),
    BASS_BOOST("Bass Boost"),
    TREBLE_BOOST("Treble Boost"),
    ROCK("Rock"),
    POP("Pop"),
    JAZZ("Jazz"),
    CLASSICAL("Classical"),
    ELECTRONIC("Electronic"),
    HIP_HOP("Hip Hop"),
    VOCAL("Vocal"),
    ACOUSTIC("Acoustic"),
    PODCAST("Podcast"),
    LATIN("Latin"),
    CUSTOM("Custom");

    fun bandLevels(): List<Int> = when (this) {
        FLAT -> List(10) { 0 }
        BASS_BOOST -> listOf(600, 500, 400, 200, 0, 0, 0, 0, 0, 0)
        TREBLE_BOOST -> listOf(0, 0, 0, 0, 0, 200, 400, 500, 600, 600)
        ROCK -> listOf(400, 300, 100, 0, -100, 100, 300, 400, 400, 400)
        POP -> listOf(-100, 100, 300, 400, 300, 100, -100, -100, 100, 200)
        JAZZ -> listOf(300, 200, 100, 200, -100, -100, 0, 100, 200, 300)
        CLASSICAL -> listOf(400, 300, 200, 100, -100, -100, 0, 200, 300, 400)
        ELECTRONIC -> listOf(500, 400, 100, 0, -200, 0, 100, 300, 400, 500)
        HIP_HOP -> listOf(500, 400, 300, 100, -100, -100, 100, 100, 200, 300)
        VOCAL -> listOf(-200, -100, 0, 300, 500, 500, 400, 200, 0, -100)
        ACOUSTIC -> listOf(300, 200, 100, 200, 100, 200, 200, 300, 300, 200)
        PODCAST -> listOf(-300, 0, 100, 500, 500, 400, 200, 0, -200, -300)
        LATIN -> listOf(400, 300, 100, 0, 100, 200, 200, 300, 300, 300)
        CUSTOM -> List(10) { 0 }
    }

    companion object {
        fun fromGenre(genre: String): EqualizerPreset? {
            val normalized = genre.lowercase().trim()
            return when {
                normalized.contains("rock") -> ROCK
                normalized.contains("pop") -> POP
                normalized.contains("jazz") -> JAZZ
                normalized.contains("classical") -> CLASSICAL
                normalized.contains("electronic") || normalized.contains("edm")
                        || normalized.contains("techno") || normalized.contains("house")
                        || normalized.contains("trance") || normalized.contains("dnb")
                        || normalized.contains("drum and bass") || normalized.contains("dubstep") -> ELECTRONIC
                normalized.contains("hip") || normalized.contains("hop")
                        || normalized.contains("rap") || normalized.contains("r&b")
                        || normalized.contains("rnb") -> HIP_HOP
                normalized.contains("vocal") || normalized.contains("acapella")
                        || normalized.contains("choral") -> VOCAL
                normalized.contains("acoustic") || normalized.contains("folk")
                        || normalized.contains("singer-songwriter") || normalized.contains("country") -> ACOUSTIC
                normalized.contains("podcast") || normalized.contains("spoken")
                        || normalized.contains("speech") || normalized.contains("audiobook")
                        || normalized.contains("talk") -> PODCAST
                normalized.contains("latin") || normalized.contains("reggaeton")
                        || normalized.contains("salsa") || normalized.contains("bachata")
                        || normalized.contains("reggae") -> LATIN
                normalized.contains("metal") || normalized.contains("punk") -> ROCK
                normalized.contains("soul") || normalized.contains("funk")
                        || normalized.contains("disco") -> POP
                normalized.contains("blues") -> JAZZ
                normalized.contains("soundtrack") || normalized.contains("score")
                        || normalized.contains("instrumental") -> CLASSICAL
                normalized.contains("bass") -> BASS_BOOST
                else -> null
            }
        }
    }
}

@Immutable
@Serializable
enum class ReverbPreset(val displayName: String, val androidPreset: Short) {
    // Values mirror android.media.audiofx.PresetReverb.PRESET_* constants
    // (verified via javap against android-37): SMALLROOM=1 ... PLATE=6.
    // NONE keeps the historical -1 sentinel meaning `no reverb set`.
    NONE("None", -1),
    SMALL_ROOM("Small Room", 1),
    MEDIUM_ROOM("Medium Room", 2),
    LARGE_ROOM("Large Room", 3),
    MEDIUM_HALL("Medium Hall", 4),
    LARGE_HALL("Large Hall", 5),
    PLATE("Plate", 6),
}
