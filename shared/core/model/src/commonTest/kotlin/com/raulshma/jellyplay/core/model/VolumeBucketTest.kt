package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exhaustive bucket-classification matrix: every [MediaType] entry
 * maps to exactly one [VolumeBucket] — the exhaustive form so a newly added
 * MediaType fails here instead of silently falling into a bucket.
 */
class VolumeBucketTest {

    @Test
    fun `every video-playable type classifies VIDEO`() {
        listOf(
            MediaType.MOVIE,
            MediaType.EPISODE,
            MediaType.MUSIC_VIDEO,
            MediaType.LIVE_TV,
            MediaType.CHANNEL,
        ).forEach { type ->
            assertEquals(VolumeBucket.VIDEO, volumeBucketFor(type), "expected VIDEO for $type")
        }
    }

    @Test
    fun `music types classify MUSIC`() {
        listOf(
            MediaType.MUSIC,
            MediaType.AUDIO,
            MediaType.ALBUM,
            MediaType.ARTIST,
        ).forEach { type ->
            assertEquals(VolumeBucket.MUSIC, volumeBucketFor(type), "expected MUSIC for $type")
        }
    }

    @Test
    fun `books classify AUDIOBOOK`() {
        assertEquals(VolumeBucket.AUDIOBOOK, volumeBucketFor(MediaType.BOOK))
    }

    @Test
    fun `containers and unknown types classify OTHER`() {
        listOf(
            MediaType.SERIES,
            MediaType.SEASON,
            MediaType.COLLECTION,
            MediaType.PHOTO,
            MediaType.PHOTO_FOLDER,
            MediaType.FOLDER,
            MediaType.UNKNOWN,
        ).forEach { type ->
            assertEquals(VolumeBucket.OTHER, volumeBucketFor(type), "expected OTHER for $type")
        }
    }

    @Test
    fun `classification is exhaustive over the enum`() {
        // The matrix must stay total: no MediaType may be added without a
        // bucket decision (the `when` without else already enforces this at
        // compile time); the counts pin the distribution.
        val perBucket = MediaType.entries.groupBy(::volumeBucketFor)
        assertEquals(5, perBucket[VolumeBucket.VIDEO]!!.size)
        assertEquals(4, perBucket[VolumeBucket.MUSIC]!!.size)
        assertEquals(1, perBucket[VolumeBucket.AUDIOBOOK]!!.size)
        assertEquals(7, perBucket[VolumeBucket.OTHER]!!.size)
        assertEquals(MediaType.entries.size, perBucket.values.sumOf { it.size })
    }
}
