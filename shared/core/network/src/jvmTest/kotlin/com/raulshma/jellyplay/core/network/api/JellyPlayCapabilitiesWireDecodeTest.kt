package com.raulshma.jellyplay.core.network.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the capabilities probe's wire shape — the version-tolerance contract:
 * the payload the plugin's `GET jellyplay/capabilities` serves must decode
 * on THIS client whether it is current (full payload), old (the optional
 * fields absent), or ahead (unknown keys ignored, out-of-range
 * contractVersion decodes — the refusal is the status store's job, never
 * the parser's). The Json instance is byte-identical to
 * [JellyPlayPluginApiClientImpl]'s private one.
 */
class JellyPlayCapabilitiesWireDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `the full v1 payload decodes`() {
        val payload = """
            {"contractVersion":1,"pluginVersion":"0.11.6.0",
             "features":["settings-sync","transcodes"],
             "serverNow":1700000000000,
             "deviceProfiles":["","desktop","phone","tv"],
             "serverSimilarPipeline":true}
        """.trimIndent()
        val caps = json.decodeFromString<JellyPlayCapabilities>(payload)
        assertEquals(1, caps.contractVersion)
        assertEquals("0.11.6.0", caps.pluginVersion)
        assertEquals(listOf("settings-sync", "transcodes"), caps.features)
        assertEquals(1_700_000_000_000L, caps.serverNow)
        assertEquals(listOf("", "desktop", "phone", "tv"), caps.deviceProfiles)
        assertEquals(true, caps.serverSimilarPipeline)
    }

    @Test
    fun `a minimal payload with only the required pair decodes`() {
        val caps = json.decodeFromString<JellyPlayCapabilities>(
            """{"contractVersion":1,"pluginVersion":"0.11.3.0"}""",
        )
        assertEquals(1, caps.contractVersion)
        assertEquals("0.11.3.0", caps.pluginVersion)
        assertTrue(caps.features.isEmpty())
        assertEquals(0L, caps.serverNow)
        assertTrue(caps.deviceProfiles.isEmpty())
        assertEquals(false, caps.serverSimilarPipeline)
    }

    @Test
    fun `unknown keys are ignored so a future additive field cannot break the decode`() {
        val caps = json.decodeFromString<JellyPlayCapabilities>(
            """{"contractVersion":1,"pluginVersion":"0.12.0.0","someFutureField":42}""",
        )
        assertEquals(1, caps.contractVersion)
        assertEquals("0.12.0.0", caps.pluginVersion)
    }

    @Test
    fun `an out-of-range contractVersion still decodes - refusal belongs to the store`() {
        val caps = json.decodeFromString<JellyPlayCapabilities>(
            """{"contractVersion":99,"pluginVersion":"9.9.9.9","features":["settings-sync"]}""",
        )
        assertEquals(99, caps.contractVersion)
        assertEquals("9.9.9.9", caps.pluginVersion)
        assertEquals(listOf("settings-sync"), caps.features)
    }
}
