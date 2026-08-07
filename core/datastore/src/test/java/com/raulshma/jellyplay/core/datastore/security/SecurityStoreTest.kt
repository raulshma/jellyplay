package com.raulshma.jellyplay.core.datastore.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * Exercises the security &amp; access-control preference store, focusing on the
 * storage invariants (nullable pin-hash removal, round-trips) that previously
 * lived inline in the `UserPreferencesStore` god object with **no** unit
 * coverage. PIN *hashing* / verification logic is covered by `PinHasherTest`,
 * not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecurityStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: SecurityStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = SecurityStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.security.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.security.first()
        assertFalse(slice.pinLockEnabled)
        assertNull(slice.pinHash)
        assertFalse(slice.biometricLockEnabled)
        assertFalse(slice.usePinForPlayerLock)
        assertEquals(30_000L, slice.autoLockTimerMs)
        assertTrue(slice.remoteControlEnabled)
    }

    @Test
    fun `setPinHash null removes the stored hash`() = runTest {
        dataStore.edit { it[stringPreferencesKey("pin_hash")] = "legacy-hash" }
        // Drain so the seeded hash is observed before the clear.
        assertEquals("legacy-hash", store.security.first().pinHash)

        store.setPinHash(null)
        assertNull(store.security.first().pinHash)
    }

    @Test
    fun `setPinHash writes the hash verbatim`() = runTest {
        store.setPinHash("v2\$310000\$deadbeef\$cafebabe")
        assertEquals("v2\$310000\$deadbeef\$cafebabe", store.security.first().pinHash)
    }

    @Test
    fun `pinLockEnabled round-trips`() = runTest {
        store.setPinLockEnabled(true)
        assertTrue(store.security.first().pinLockEnabled)
        store.setPinLockEnabled(false)
        assertFalse(store.security.first().pinLockEnabled)
    }

    @Test
    fun `autoLockTimer round-trips`() = runTest {
        store.setAutoLockTimerMs(120_000L)
        assertEquals(120_000L, store.security.first().autoLockTimerMs)
        store.setAutoLockTimerMs(0L)
        assertEquals(0L, store.security.first().autoLockTimerMs)
    }

    @Test
    fun `legacy pin_lock_enabled string migrates via typed-key fallback`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("pin_lock_enabled")] = "true"
            it[booleanPreferencesKey("biometric_lock_enabled")] = true
        }
        val slice = store.security.first()
        assertTrue(slice.pinLockEnabled)
        assertTrue(slice.biometricLockEnabled)
    }

    @Test
    fun `setPin hashes the pin and enables the lock atomically`() = runTest {
        store.setPin("1234")
        val slice = store.security.first()
        assertTrue(slice.pinLockEnabled)
        assertNotNull(slice.pinHash)
        assertTrue(slice.pinHash!!.startsWith("v2$"))
        assertTrue(store.verifyPinOffMainThread("1234"))
        assertFalse(store.verifyPinOffMainThread("0000"))
    }

    @Test
    fun `setPin ignores blank pins`() = runTest {
        store.setPin("   ")
        val slice = store.security.first()
        assertFalse(slice.pinLockEnabled)
        assertNull(slice.pinHash)
    }

    @Test
    fun `clearPin disables the lock and removes the hash`() = runTest {
        store.setPin("1234")
        assertTrue(store.security.first().pinLockEnabled)

        store.clearPin()
        val slice = store.security.first()
        assertFalse(slice.pinLockEnabled)
        assertNull(slice.pinHash)
        assertFalse(store.verifyPinOffMainThread("1234"))
    }

    @Test
    fun `verifyPinOffMainThread returns false when no pin is set`() = runTest {
        assertFalse(store.verifyPinOffMainThread("1234"))
    }

    @Test
    fun `verifyPinOffMainThread upgrades a legacy hash on successful unlock`() = runTest {
        val pin = "9876"
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        dataStore.edit {
            it[stringPreferencesKey("pin_hash")] = legacy
            it[booleanPreferencesKey("pin_lock_enabled")] = true
        }
        assertEquals(legacy, store.security.first().pinHash)
        assertTrue(store.pinHashNeedsMigration(legacy))

        assertTrue(store.verifyPinOffMainThread(pin))

        val upgraded = store.security.first().pinHash
        assertNotNull(upgraded)
        assertTrue(upgraded!!.startsWith("v2$"))
        assertFalse(store.pinHashNeedsMigration(upgraded))
        // The wrong PIN must not trigger an upgrade.
        assertFalse(store.verifyPinOffMainThread("0000"))
        assertEquals(upgraded, store.security.first().pinHash)
    }

    @Test
    fun `upgradePinHashIfLegacy is a no-op for v2 hashes and blank pins`() = runTest {
        store.setPin("1234")
        val current = store.security.first().pinHash
        assertFalse(store.upgradePinHashIfLegacy("1234"))
        assertFalse(store.upgradePinHashIfLegacy(""))
        assertEquals(current, store.security.first().pinHash)
    }

    @Test
    fun `restore(slice) round-trips remote control and leaves lock config untouched`() = runTest {
        // Seed an existing lock config that must survive the restore.
        store.setPin("1234")
        val before = store.security.first()
        assertTrue(before.pinLockEnabled)

        val slice = SecuritySlice(remoteControlEnabled = false)
        store.restore(slice)

        val after = store.security.first()
        // Only remoteControlEnabled is the slice restore's responsibility.
        assertFalse(after.remoteControlEnabled)
        // Lock config seeded before the restore is preserved.
        assertTrue(after.pinLockEnabled)
        assertEquals(before.pinHash, after.pinHash)
    }

    @Test
    fun `restoreSecuritySensitive round-trips the full lock config`() = runTest {
        val slice = SecuritySlice(
            pinLockEnabled = true,
            pinHash = "v2\$310000\$deadbeef\$cafebabe",
            biometricLockEnabled = true,
            usePinForPlayerLock = true,
            autoLockTimerMs = 120_000L,
            remoteControlEnabled = false,
        )

        store.restoreSecuritySensitive(slice)

        val after = store.security.first()
        assertEquals(true, after.pinLockEnabled)
        assertEquals("v2\$310000\$deadbeef\$cafebabe", after.pinHash)
        assertTrue(after.biometricLockEnabled)
        assertTrue(after.usePinForPlayerLock)
        assertEquals(120_000L, after.autoLockTimerMs)
        // restoreSecuritySensitive deliberately does not touch remoteControlEnabled.
        assertTrue(after.remoteControlEnabled)
    }
}
