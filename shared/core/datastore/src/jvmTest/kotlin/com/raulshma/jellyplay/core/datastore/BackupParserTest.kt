package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises the pure [BackupParser] version-sniffing: the v2 per-slice shape
 * (and forward-compatible future versions) select their sealed
 * [BackupParser.Parsed] variant, the `hasSecuritySensitive` flag must come
 * from the security slice, and legacy (v0/v1), unknown or malformed payloads
 * must fail loudly rather than decode silently (legacy imports sunset v0.11).
 */
class BackupParserTest {

    private fun securitySliceJson(slice: SecuritySlice) =
        PreferencesJson.export.encodeToJsonElement(SecuritySlice.serializer(), slice)

    private fun isFuture(p: BackupParser.Parsed) = with(BackupParser) { p.isFuture() }
    private fun schemaVersionOf(p: BackupParser.Parsed) = with(BackupParser) { p.schemaVersion() }
    private fun runtimeStateOf(p: BackupParser.Parsed) = with(BackupParser) { p.toAppRuntimeState() }

    private fun v2BackupJson(
        slices: Map<String, kotlinx.serialization.json.JsonElement>,
        extras: AppRuntimeState = AppRuntimeState(),
    ): String {
        val backup = SettingsBackup(slices = slices, extras = extras)
        return PreferencesJson.export.encodeToString(SettingsBackup.serializer(), backup)
    }

    // ------------------------------------------------------------------
    // v2 — current per-slice envelope
    // ------------------------------------------------------------------

    @Test
    fun `v2 backup parses to V2 with security slice present`() {
        val json = v2BackupJson(
            slices = mapOf(BackupSliceKey.SECURITY to securitySliceJson(SecuritySlice(pinLockEnabled = true))),
        )

        val parsed = BackupParser.parse(json)

        val v2 = assertIs<BackupParser.Parsed.V2>(parsed)
        assertTrue(v2.hasSecuritySensitive)
        assertFalse(isFuture(parsed))
        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION, schemaVersionOf(parsed))
    }

    @Test
    fun `v2 backup with unlocked security slice is not security sensitive`() {
        val json = v2BackupJson(
            slices = mapOf(BackupSliceKey.SECURITY to securitySliceJson(SecuritySlice())),
        )

        val parsed = BackupParser.parse(json)

        assertTrue(parsed is BackupParser.Parsed.V2)
        assertFalse(parsed.hasSecuritySensitive)
    }

    @Test
    fun `v2 backup without a security slice is not security sensitive`() {
        val json = v2BackupJson(
            slices = mapOf(
                BackupSliceKey.PLAYBACK to buildJsonObject { put("preferredPlayer", "MPV") },
            ),
        )

        val parsed = BackupParser.parse(json)

        assertTrue(parsed is BackupParser.Parsed.V2)
        assertFalse(parsed.hasSecuritySensitive)
    }

    @Test
    fun `v2 pinHash alone flags security sensitivity`() {
        val json = v2BackupJson(
            slices = mapOf(BackupSliceKey.SECURITY to securitySliceJson(SecuritySlice(pinHash = "h"))),
        )

        val parsed = BackupParser.parse(json)

        assertTrue(parsed is BackupParser.Parsed.V2)
        assertTrue(parsed.hasSecuritySensitive)
    }

    // ------------------------------------------------------------------
    // Legacy v0/v1 — rejected at parse since the v0.11 sunset
    // ------------------------------------------------------------------

    @Test
    fun `v1 enveloped aggregate is rejected with a clear error`() {
        val json = buildJsonObject {
            put("schemaVersion", 1)
            put("exportedAt", 0)
            put("preferences", buildJsonObject { put("preferredPlayer", "MPV") })
        }.toString()

        val exception = assertFailsWith<IllegalArgumentException> { BackupParser.parse(json) }
        assertTrue(
            exception.message!!.contains("Unsupported backup schemaVersion=1"),
            "unexpected message: ${exception.message}",
        )
    }

    @Test
    fun `v0 bare aggregate (no envelope) is rejected`() {
        val json = buildJsonObject { put("preferredPlayer", "MPV") }.toString()

        assertFailsWith<IllegalArgumentException> { BackupParser.parse(json) }
    }

    @Test
    fun `explicit schemaVersion zero is rejected`() {
        val json = buildJsonObject {
            put("schemaVersion", 0)
            put("preferredPlayer", "MPV")
        }.toString()

        assertFailsWith<IllegalArgumentException> { BackupParser.parse(json) }
    }

    // ------------------------------------------------------------------
    // Future — forward-compat (> CURRENT)
    // ------------------------------------------------------------------

    @Test
    fun `future schema version parses to Future and keeps extras`() {
        val futureJson = buildJsonObject {
            put("schemaVersion", SettingsBackup.CURRENT_SCHEMA_VERSION + 97)
            put("exportedAt", 123L)
            put("slices", buildJsonObject {
                put(BackupSliceKey.SECURITY, securitySliceJson(SecuritySlice(usePinForPlayerLock = true)))
            })
            put("extras", buildJsonObject { put("onboardingCompleted", true) })
        }.toString()

        val parsed = BackupParser.parse(futureJson)

        val future = assertIs<BackupParser.Parsed.Future>(parsed)
        assertTrue(future.hasSecuritySensitive)
        assertTrue(isFuture(parsed))
        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION + 97, schemaVersionOf(parsed))
        val runtime = runtimeStateOf(parsed)
        assertTrue(runtime.onboardingCompleted)
    }

    @Test
    fun `future backup without security slice is not security sensitive`() {
        val futureJson = buildJsonObject {
            put("schemaVersion", SettingsBackup.CURRENT_SCHEMA_VERSION + 1)
            put("slices", buildJsonObject {})
            put("extras", buildJsonObject {})
        }.toString()

        val parsed = BackupParser.parse(futureJson)

        assertTrue(parsed is BackupParser.Parsed.Future)
        assertFalse(parsed.hasSecuritySensitive)
    }

    // ------------------------------------------------------------------
    // Unknown versions and malformed payloads
    // ------------------------------------------------------------------

    @Test
    fun `unknown old schema version fails with a clear error`() {
        val json = buildJsonObject {
            put("schemaVersion", -5)
            put("preferredPlayer", "MPV")
        }.toString()

        val exception = assertFailsWith<IllegalArgumentException> { BackupParser.parse(json) }
        assertTrue(
            exception.message!!.contains("Unsupported backup schemaVersion=-5"),
            "unexpected message: ${exception.message}",
        )
    }

    @Test
    fun `malformed JSON fails loudly`() {
        assertFailsWith<SerializationException> { BackupParser.parse("{not json") }
        // A JSON null has no schemaVersion — rejected as an unsupported backup.
        assertFailsWith<IllegalArgumentException> { BackupParser.parse("null") }
    }

    @Test
    fun `non-object JSON is rejected as an unsupported backup`() {
        // A JSON array has no schemaVersion — the parser rejects it instead of
        // yielding a phantom default aggregate.
        assertFailsWith<IllegalArgumentException> { BackupParser.parse("[1,2,3]") }
    }

    // ------------------------------------------------------------------
    // toAppRuntimeState mapping
    // ------------------------------------------------------------------

    @Test
    fun `v2 extras map straight through toAppRuntimeState`() {
        val extras = AppRuntimeState(
            favoriteChannels = setOf("ch1", "ch2"),
            watchLaterPlaylistId = "playlist-7",
            onboardingCompleted = true,
        )
        val json = v2BackupJson(slices = emptyMap(), extras = extras)

        val parsed = BackupParser.parse(json)

        assertEquals(extras, runtimeStateOf(parsed))
    }
}
