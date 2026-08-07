package com.raulshma.jellyplay.core.model

import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCacheModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `AudioCacheNetworkPolicy has three entries with display names`() {
        val entries = AudioCacheNetworkPolicy.entries
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.displayName.isNotBlank() })
        assertEquals(AudioCacheNetworkPolicy.WIFI_ONLY, AudioCacheNetworkPolicy.DEFAULT)
    }

    @Test
    fun `AudioCacheNetworkPolicy serializes and deserializes by name`() {
        AudioCacheNetworkPolicy.entries.forEach { policy ->
            val encoded = json.encodeToString(AudioCacheNetworkPolicy.serializer(), policy)
            val decoded = json.decodeFromString(AudioCacheNetworkPolicy.serializer(), encoded)
            assertEquals(policy, decoded)
        }
    }

    @Test
    fun `UserPreferences has audio cache fields with correct defaults`() {
        val prefs = UserPreferences()
        assertTrue(prefs.audioCachingEnabled)
        assertEquals(1024, prefs.audioCacheSizeMb)
        assertEquals(3, prefs.audioPrefetchLookahead)
        assertEquals(5, prefs.audioPrefetchBackfill)
        assertEquals(AudioCacheNetworkPolicy.WIFI_ONLY, prefs.audioCacheNetworkPolicy)
        assertEquals(500, prefs.audioCacheCellularMonthlyCapMb)
    }

    @Test
    fun `UserPreferences round-trips audio cache fields through serialization`() {
        val original = UserPreferences(
            audioCachingEnabled = false,
            audioCacheSizeMb = 2048,
            audioPrefetchLookahead = 5,
            audioPrefetchBackfill = 10,
            audioCacheNetworkPolicy = AudioCacheNetworkPolicy.ANY_NETWORK,
            audioCacheCellularMonthlyCapMb = 1000,
        )
        val encoded = json.encodeToString(UserPreferences.serializer(), original)
        val decoded = json.decodeFromString(UserPreferences.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
