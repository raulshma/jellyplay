package com.raulshma.jellyplay.core.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the [PreferenceCodec] typed-read/migration primitives: pre-migration
 * legacy-string fallback reads (including the ClassCastException-tolerant path),
 * the one-shot [PreferenceCodec.TYPED_MIGRATION_DONE] gate, and the post-migration
 * behavior where a typed value wins and stale legacy strings are ignored.
 *
 * Pure reads use [preferencesOf] directly; the one-shot migration needs a real
 * DataStore, so it shares the module's single [TestDataStoreProvider] instance
 * (cleared in [BeforeTest] per the datastore test convention).
 */
class PreferenceCodecTest {

    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
        }
    }

    // ------------------------------------------------------------------
    // Pre-migration legacy-string fallback reads
    // ------------------------------------------------------------------

    @Test
    fun `readBool falls back to the legacy string pre-migration`() {
        val prefs = preferencesOf(stringPreferencesKey("pref_x") to "true")

        assertTrue(PreferenceCodec.readBool(prefs, booleanPreferencesKey("pref_x"), "pref_x", default = false))
    }

    @Test
    fun `readBool returns the default when nothing is present`() {
        val prefs = preferencesOf()

        assertFalse(PreferenceCodec.readBool(prefs, booleanPreferencesKey("pref_x"), "pref_x", default = false))
        assertTrue(PreferenceCodec.readBool(prefs, booleanPreferencesKey("pref_y"), "pref_y", default = true))
    }

    @Test
    fun `readBool honors a typed false pre-migration`() {
        // A Preferences map is keyed by name, so a slot holds EITHER the typed
        // value OR the legacy string — a typed false must be returned as-is.
        val prefs = preferencesOf(booleanPreferencesKey("pref_x") to false)

        assertFalse(PreferenceCodec.readBool(prefs, booleanPreferencesKey("pref_x"), "pref_x", default = true))
    }

    @Test
    fun `readInt falls back to the legacy string and tolerates non-numeric values`() {
        val numeric = preferencesOf(stringPreferencesKey("pref_i") to "42")
        val garbage = preferencesOf(stringPreferencesKey("pref_i") to "abc")

        assertEquals(42, PreferenceCodec.readInt(numeric, intPreferencesKey("pref_i"), "pref_i", default = 0))
        assertEquals(0, PreferenceCodec.readInt(garbage, intPreferencesKey("pref_i"), "pref_i", default = 0))
    }

    @Test
    fun `readInt reads a string slot through the ClassCastException guard`() {
        // A string living under the typed key's name makes prefs[intKey] throw
        // ClassCastException — the guard must treat it as "no typed value" so
        // the legacy string fallback still resolves it.
        val prefs = preferencesOf(stringPreferencesKey("pref_i") to "7")

        assertEquals(7, PreferenceCodec.readInt(prefs, intPreferencesKey("pref_i"), "pref_i", default = 0))
    }

    @Test
    fun `readFloat falls back to the legacy string`() {
        val prefs = preferencesOf(stringPreferencesKey("pref_f") to "1.5")

        assertEquals(1.5f, PreferenceCodec.readFloat(prefs, floatPreferencesKey("pref_f"), "pref_f", default = 0f))
    }

    @Test
    fun `readLong falls back to the legacy string`() {
        val prefs = preferencesOf(stringPreferencesKey("pref_l") to "123456789")

        assertEquals(123456789L, PreferenceCodec.readLong(prefs, longPreferencesKey("pref_l"), "pref_l", default = 0L))
    }

    @Test
    fun `legacyString tolerates a typed value living under the same name`() = runTest {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("pref_x")] = true
            assertNull(with(PreferenceCodec) { prefs.legacyString("pref_x") })
        }
    }

    // ------------------------------------------------------------------
    // One-shot legacy → typed migration
    // ------------------------------------------------------------------

    @Test
    fun `runTypedKeyMigration rewrites legacy strings and flips the gate flag`() = runTest {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("m_bool")] = "true"
            prefs[stringPreferencesKey("m_int")] = "7"
            prefs[stringPreferencesKey("m_bad")] = "xyz"
            prefs[stringPreferencesKey("m_float")] = "0.5"
            prefs[stringPreferencesKey("m_long")] = "99"
        }

        PreferenceCodec.runTypedKeyMigration(
            dataStore,
            booleans = arrayOf("m_bool"),
            ints = arrayOf("m_int", "m_bad"),
            floats = arrayOf("m_float"),
            longs = arrayOf("m_long"),
        )

        val prefs = dataStore.data.first()
        // The gate flag flipped exactly once for the whole batch...
        assertEquals(true, prefs[PreferenceCodec.TYPED_MIGRATION_DONE])
        // ...every parseable legacy slot was rewritten to its typed key...
        assertEquals(true, prefs[booleanPreferencesKey("m_bool")])
        assertEquals(7, prefs[intPreferencesKey("m_int")])
        assertEquals(0.5f, prefs[floatPreferencesKey("m_float")]!!)
        assertEquals(99L, prefs[longPreferencesKey("m_long")]!!)
        // ...and a non-parseable legacy slot was skipped: its slot still holds
        // the legacy string (an int write would have replaced it).
        assertEquals("xyz", prefs[stringPreferencesKey("m_bad")])
    }

    @Test
    fun `runTypedKeyMigration preserves a slot that already holds a typed value`() = runTest {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("m_int")] = 5
        }

        PreferenceCodec.runTypedKeyMigration(dataStore, booleans = arrayOf(), ints = arrayOf("m_int"), floats = arrayOf(), longs = arrayOf())

        val prefs = dataStore.data.first()
        // Gate flipped, but the typed value was left untouched (the legacy
        // read sees a non-string slot and skips).
        assertEquals(true, prefs[PreferenceCodec.TYPED_MIGRATION_DONE])
        assertEquals(5, prefs[intPreferencesKey("m_int")])
    }

    @Test
    fun `runTypedKeyMigration is idempotent — the gate blocks a second rewrite`() = runTest {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("m_int")] = "1"
        }
        PreferenceCodec.runTypedKeyMigration(dataStore, booleans = arrayOf(), ints = arrayOf("m_int"), floats = arrayOf(), longs = arrayOf())
        assertEquals(1, PreferenceCodec.readInt(dataStore.data.first(), intPreferencesKey("m_int"), "m_int", default = 0))

        // Simulate a legacy string slot re-appearing under the same name after
        // migration (same Preferences slot — writing the string replaces the
        // typed int), then run migration again: the gate must short-circuit the
        // whole batch so the string is NOT rewritten into a typed int.
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("m_int")] = "999"
        }
        PreferenceCodec.runTypedKeyMigration(dataStore, booleans = arrayOf(), ints = arrayOf("m_int"), floats = arrayOf(), longs = arrayOf())

        assertEquals("999", dataStore.data.first()[stringPreferencesKey("m_int")])
    }

    // ------------------------------------------------------------------
    // Post-migration: typed value wins, legacy ignored
    // ------------------------------------------------------------------

    @Test
    fun `typed value wins once the migration flag is set`() {
        // Post-migration a slot can only hold the typed value or be absent —
        // a present typed value must be returned even when it differs from the
        // default.
        val prefs = preferencesOf(
            PreferenceCodec.TYPED_MIGRATION_DONE to true,
            booleanPreferencesKey("pref_b") to false,
        )

        // Default=true proves the TYPED value (false) was read, not the default.
        assertFalse(PreferenceCodec.readBool(prefs, booleanPreferencesKey("pref_b"), "pref_b", default = true))
    }

    @Test
    fun `legacy string is ignored post-migration when no typed value exists`() {
        val prefs = preferencesOf(
            PreferenceCodec.TYPED_MIGRATION_DONE to true,
            stringPreferencesKey("pref_i") to "42",
        )

        // Post-migration the fallback is skipped: a typed value is always
        // rewritten in place by the migration, so absence means "default".
        assertEquals(0, PreferenceCodec.readInt(prefs, intPreferencesKey("pref_i"), "pref_i", default = 0))
    }

    @Test
    fun `post-migration typed reads win for every type`() {
        // One Preferences instance per type (slots are name-keyed, so a typed
        // value and a legacy string cannot coexist under one name).
        val intPrefs = preferencesOf(
            PreferenceCodec.TYPED_MIGRATION_DONE to true,
            intPreferencesKey("p_i") to 3,
        )
        val floatPrefs = preferencesOf(
            PreferenceCodec.TYPED_MIGRATION_DONE to true,
            floatPreferencesKey("p_f") to 2.5f,
        )
        val longPrefs = preferencesOf(
            PreferenceCodec.TYPED_MIGRATION_DONE to true,
            longPreferencesKey("p_l") to 42L,
        )

        assertEquals(3, PreferenceCodec.readInt(intPrefs, intPreferencesKey("p_i"), "p_i", default = 0))
        assertEquals(2.5f, PreferenceCodec.readFloat(floatPrefs, floatPreferencesKey("p_f"), "p_f", default = 0f))
        assertEquals(42L, PreferenceCodec.readLong(longPrefs, longPreferencesKey("p_l"), "p_l", default = 0L))
    }
}
