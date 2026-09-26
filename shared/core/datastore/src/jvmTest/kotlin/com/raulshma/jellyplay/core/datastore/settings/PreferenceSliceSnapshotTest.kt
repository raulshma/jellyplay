package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the v2 backup → [PreferenceSliceSnapshot] decode
 * ([buildPreferenceSliceSnapshotFromBackup]): defaults for absent slices,
 * skip-on-malformed forward-compat, value propagation, and the extras/PIN
 * handling — the semantics the import-preview diff is built on.
 */
class PreferenceSliceSnapshotTest {

    /** Encodes a slice value as the [JsonElement] stored in the v2 slices map. */
    private fun <T> sliceElement(serializer: KSerializer<T>, value: T): JsonElement =
        PreferencesJson.import.parseToJsonElement(
            PreferencesJson.import.encodeToString(serializer, value),
        )

    private fun backup(slices: Map<String, JsonElement>, extras: AppRuntimeState = AppRuntimeState()) =
        SettingsBackup(schemaVersion = 2, slices = slices, extras = extras)

    @Test
    fun `absent slices decode to defaults`() {
        val snapshot = buildPreferenceSliceSnapshotFromBackup(backup(slices = emptyMap()))

        assertEquals(PreferenceSliceSnapshot.FACTORY.copy(runtime = AppRuntimeState()), snapshot)
    }

    @Test
    fun `present slice values land in the snapshot`() {
        val snapshot = buildPreferenceSliceSnapshotFromBackup(
            backup(
                slices = mapOf(
                    BackupSliceKey.APPEARANCE to sliceElement(
                        AppearanceSlice.serializer(),
                        AppearanceSlice(themeMode = ThemeMode.DARK),
                    ),
                    BackupSliceKey.SECURITY to sliceElement(
                        SecuritySlice.serializer(),
                        SecuritySlice(pinLockEnabled = true, pinHash = "incoming-hash"),
                    ),
                ),
                extras = AppRuntimeState(favoriteChannels = setOf("chan-1")),
            ),
        )

        assertEquals(ThemeMode.DARK, snapshot.appearance.themeMode)
        assertTrue(snapshot.security.pinLockEnabled)
        assertEquals("incoming-hash", snapshot.security.pinHash)
        assertEquals(setOf("chan-1"), snapshot.runtime.favoriteChannels)
    }

    @Test
    fun `malformed slice element is skipped forward-compatibly`() {
        val snapshot = buildPreferenceSliceSnapshotFromBackup(
            backup(
                slices = mapOf(
                    BackupSliceKey.APPEARANCE to PreferencesJson.import.parseToJsonElement("""{"themeMode":"NOT_A_THEME"}"""),
                ),
            ),
        )

        assertEquals(ThemeMode.SYSTEM, snapshot.appearance.themeMode, "undecodable slice falls back to defaults")
    }

    @Test
    fun `pin lockout defaults to not-locked and can be preserved`() {
        val preserved = PinLockoutState(failedAttempts = 3, lockoutUntilEpochMs = 42L)
        val snapshot = buildPreferenceSliceSnapshotFromBackup(backup(slices = emptyMap()), pinLockout = preserved)

        assertEquals(preserved, snapshot.pinLockout)
        assertEquals(PinLockoutState.NOT_LOCKED, buildPreferenceSliceSnapshotFromBackup(backup(slices = emptyMap())).pinLockout)
    }
}
