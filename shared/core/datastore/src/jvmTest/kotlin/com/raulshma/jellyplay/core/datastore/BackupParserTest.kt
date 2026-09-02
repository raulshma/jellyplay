package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the pure [BackupParser] version-sniffing: each backup shape must
 * select its sealed [BackupParser.Parsed] variant, the `hasSecuritySensitive`
 * flag must come from the security slice / legacy lock fields, and unknown or
 * malformed payloads must fail loudly rather than decode silently.
 */
class BackupParserTest {

    private fun securitySliceJson(slice: SecuritySlice) =
        PreferencesJson.export.encodeToJsonElement(SecuritySlice.serializer(), slice)

    // isLegacy/isFuture/schemaVersion are extensions declared inside the
    // BackupParser object — resolve them in its receiver scope.
    private fun isLegacy(p: BackupParser.Parsed) = with(BackupParser) { p.isLegacy() }
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
        assertFalse(isLegacy(parsed))
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
    // v1 — legacy enveloped aggregate
    // ------------------------------------------------------------------

    @Test
    fun `v1 aggregate backup parses to V1 with locked preferences flagged`() {
        val legacy = LegacySettingsBackup(
            preferences = UserPreferences(pinLockEnabled = true, pinHash = "hash"),
        )
        val json = PreferencesJson.export.encodeToString(LegacySettingsBackup.serializer(), legacy)

        val parsed = BackupParser.parse(json)

        val v1 = assertIs<BackupParser.Parsed.V1>(parsed)
        assertTrue(v1.hasSecuritySensitive)
        assertTrue(isLegacy(parsed))
        assertEquals(SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION, schemaVersionOf(parsed))
        assertEquals(PlayerType.EXO_PLAYER, v1.preferences.preferredPlayer)
    }

    @Test
    fun `v1 aggregate backup without lock config is not security sensitive`() {
        val legacy = LegacySettingsBackup(preferences = UserPreferences(preferredPlayer = PlayerType.MPV))
        val json = PreferencesJson.export.encodeToString(LegacySettingsBackup.serializer(), legacy)

        val parsed = BackupParser.parse(json)

        val v1 = assertIs<BackupParser.Parsed.V1>(parsed)
        assertFalse(v1.hasSecuritySensitive)
        assertEquals(PlayerType.MPV, v1.preferences.preferredPlayer)
    }

    // ------------------------------------------------------------------
    // v0 — bare (un-enveloped) aggregate
    // ------------------------------------------------------------------

    @Test
    fun `v0 bare backup parses to V0`() {
        val json = PreferencesJson.export.encodeToString(
            UserPreferences.serializer(),
            UserPreferences(biometricLockEnabled = true),
        )

        val parsed = BackupParser.parse(json)

        val v0 = assertIs<BackupParser.Parsed.V0>(parsed)
        assertTrue(v0.hasSecuritySensitive)
        assertTrue(isLegacy(parsed))
        assertEquals(SettingsBackup.LEGACY_UNENVELOPED_SCHEMA_VERSION, schemaVersionOf(parsed))
    }

    @Test
    fun `explicit schemaVersion zero decodes as v0`() {
        val json = buildJsonObject {
            put("schemaVersion", 0)
            put("preferredPlayer", "MPV")
        }.toString()

        val parsed = BackupParser.parse(json)

        val v0 = assertIs<BackupParser.Parsed.V0>(parsed)
        assertFalse(v0.hasSecuritySensitive)
        assertEquals(PlayerType.MPV, v0.preferences.preferredPlayer)
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
        assertFalse(isLegacy(parsed))
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
        assertEquals("Unknown backup schemaVersion=-5", exception.message)
    }

    @Test
    fun `malformed JSON fails with SerializationException`() {
        assertFailsWith<SerializationException> { BackupParser.parse("{not json") }
        assertFailsWith<SerializationException> { BackupParser.parse("null") }
    }

    @Test
    fun `non-object JSON fails instead of decoding as a bare aggregate`() {
        // A JSON array has no schemaVersion, so the parser falls through to the
        // bare v0 decode — which must reject the array rather than yield a
        // phantom default UserPreferences.
        assertFailsWith<SerializationException> { BackupParser.parse("[1,2,3]") }
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

    @Test
    fun `legacy v1 aggregate maps runtime fields toAppRuntimeState`() {
        val legacy = LegacySettingsBackup(
            preferences = UserPreferences(
                favoriteChannels = setOf("c1"),
                watchLaterPlaylistId = "wl",
                onboardingCompleted = true,
            ),
        )
        val json = PreferencesJson.export.encodeToString(LegacySettingsBackup.serializer(), legacy)

        val parsed = BackupParser.parse(json)

        val runtime = runtimeStateOf(parsed)
        assertEquals(setOf("c1"), runtime.favoriteChannels)
        assertEquals("wl", runtime.watchLaterPlaylistId)
        assertTrue(runtime.onboardingCompleted)
        // live-TV channel is a v2-extras-only field: legacy never carries it.
        assertNull(runtime.liveTvLastChannelId)
    }

    @Test
    fun `v0 bare aggregate maps runtime fields toAppRuntimeState`() {
        val json = PreferencesJson.export.encodeToString(
            UserPreferences.serializer(),
            UserPreferences(onboardingCompleted = true),
        )

        val parsed = BackupParser.parse(json)

        val runtime = runtimeStateOf(parsed)
        assertTrue(runtime.onboardingCompleted)
        assertEquals(emptySet(), runtime.favoriteChannels)
        assertNull(runtime.watchLaterPlaylistId)
    }
}
