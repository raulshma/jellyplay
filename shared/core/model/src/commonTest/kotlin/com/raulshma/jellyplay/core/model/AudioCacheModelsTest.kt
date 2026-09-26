package com.raulshma.jellyplay.core.model

import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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
}
