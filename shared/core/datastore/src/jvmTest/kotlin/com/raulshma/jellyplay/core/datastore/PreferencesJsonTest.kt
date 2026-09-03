package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the shared preference import/export [PreferencesJson] configurations:
 * the export config pretty-prints and encodes defaults (a stable, human-readable
 * backup that carries every field even at its default), the import config is
 * forward-compatible (unknown fields ignored so older exports still load) while
 * still encoding defaults, and [PreferencesJson.fullPreferences] aliases the
 * export config so the two never drift.
 */
class PreferencesJsonTest {

    @Test
    fun `export config encodes defaults`() {
        // AppRuntimeState's defaults (empty channels, no ids, onboarding=false)
        // must still appear in the exported JSON.
        val element = PreferencesJson.export.encodeToJsonElement(
            serializer<AppRuntimeState>(),
            AppRuntimeState(),
        )

        val obj = element.jsonObject
        assertTrue("favoriteChannels" in obj, "defaults must be encoded: $obj")
        assertTrue("onboardingCompleted" in obj, "defaults must be encoded: $obj")
        assertTrue("watchLaterPlaylistId" in obj, "defaults must be encoded: $obj")
    }

    @Test
    fun `export config pretty-prints the payload`() {
        val encoded = PreferencesJson.export.encodeToString(
            serializer<AppRuntimeState>(),
            AppRuntimeState(),
        )

        // Pretty-printing inserts a newline + indentation between members.
        assertTrue('\n' in encoded, "export JSON must be pretty-printed: $encoded")
    }

    @Test
    fun `import config tolerates unknown fields from newer exports`() {
        val json = """
            {
              "favoriteChannels": ["ch-1"],
              "liveTvLastChannelId": "live-9",
              "watchLaterPlaylistId": "pl-1",
              "onboardingCompleted": true,
              "recentDlnaDevices": [],
              "someFutureField": {"nested": [1, 2, 3]}
            }
        """.trimIndent()

        val restored = PreferencesJson.import.decodeFromString(
            serializer<AppRuntimeState>(),
            json,
        )

        assertEquals(setOf("ch-1"), restored.favoriteChannels)
        assertEquals("live-9", restored.liveTvLastChannelId)
        assertEquals("pl-1", restored.watchLaterPlaylistId)
        assertTrue(restored.onboardingCompleted)
    }

    @Test
    fun `import config round-trips a value it produced`() {
        val state = AppRuntimeState(
            favoriteChannels = setOf("a", "b"),
            watchLaterPlaylistId = "pl-7",
            onboardingCompleted = true,
        )

        val encoded = PreferencesJson.import.encodeToString(serializer<AppRuntimeState>(), state)
        val decoded = PreferencesJson.import.decodeFromString(serializer<AppRuntimeState>(), encoded)

        assertEquals(state, decoded)
    }

    @Test
    fun `fullPreferences aliases the export config`() {
        // Same instance, not a copy: a config tweak to export must apply to the
        // full-preferences export too.
        assertTrue(PreferencesJson.fullPreferences === PreferencesJson.export)
        assertNotNull(PreferencesJson.import)
    }
}
