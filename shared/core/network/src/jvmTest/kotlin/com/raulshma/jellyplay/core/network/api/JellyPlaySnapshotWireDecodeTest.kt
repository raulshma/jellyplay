package com.raulshma.jellyplay.core.network.api

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the snapshot payloads' wire shape against the plugin's serializer: the
 * C# `SnapshotDto`/`SnapshotCreateResponse`/`SnapshotContentDto` records carry
 * `long Id`, which serializes as an UNQUOTED JSON number — a String id here
 * would fail the strict decode (the bug this pins), so the ids decode as
 * [Long] and the route-building callers stringify them. The Json instance is
 * byte-identical to [JellyPlayPluginApiClientImpl]'s private one.
 */
class JellyPlaySnapshotWireDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `snapshot list decodes the numeric id`() {
        val payload = """[{"id":12,"createdAt":1700000000000,"origin":"manual","keys":4,"bytes":900}]"""
        val snapshots = json.decodeFromString(ListSerializer(JellyPlaySnapshot.serializer()), payload)
        assertEquals(1, snapshots.size)
        assertEquals(12L, snapshots[0].id)
        assertEquals(1_700_000_000_000L, snapshots[0].createdAt)
        assertEquals("manual", snapshots[0].origin)
    }

    @Test
    fun `create-snapshot response decodes the numeric id`() {
        assertEquals(7L, json.decodeFromString<JellyPlaySnapshotCreated>("""{"id":7}""").id)
    }

    @Test
    fun `snapshot content decodes the numeric id and the profile grouping`() {
        val payload = """
            {"id":31,"createdAt":1700000000000,"origin":"manual",
             "profiles":[{"profile":"tv","settings":[
                {"ns":"prefs","key":"theme_mode","schemaVersion":1,"updatedAt":1700000000001,"deviceId":"d1","value":"dark"}]}]}
        """.trimIndent()
        val content = json.decodeFromString<JellyPlaySnapshotContent>(payload)
        assertEquals(31L, content.id)
        assertEquals("manual", content.origin)
        assertEquals("tv", content.profiles.single().profile)
        assertEquals("theme_mode", content.profiles.single().settings.single().key)
    }
}
