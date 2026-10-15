package com.raulshma.jellyplay.core.datastore.spec

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.tools.SettingsCatalogGenerator
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The key-level sync-exclusion pin (JpsyncReservationTest-style): the one
 * assembled set the sync adapter's registration and the catalog denylist
 * both consume is asserted against the four owning stores' exports AND the
 * literal key names the field stores hold — a rename or a dropped set here
 * must break this test, not silently leak a secret onto the sync wire.
 */
class PreferenceSyncPolicyTest {

    @Test
    fun excludedKeys_sumsTheFourOwningStores() {
        assertEquals(
            SecurityStore.SyncExcludedKeys +
                PinRateLimiter.SyncExcludedKeys +
                ServerIdentityStore.SyncExcludedKeys +
                WidgetDataStore.SyncExcludedKeys,
            PreferenceSyncPolicy.EXCLUDED_KEYS,
        )
        // No overlaps, so the sum is the union (a dropped set would shrink
        // the exclusion, never hide behind a duplicate).
        val fourSets = listOf(
            SecurityStore.SyncExcludedKeys,
            PinRateLimiter.SyncExcludedKeys,
            ServerIdentityStore.SyncExcludedKeys,
            WidgetDataStore.SyncExcludedKeys,
        )
        assertEquals(fourSets.sumOf { it.size }, PreferenceSyncPolicy.EXCLUDED_KEYS.size)
    }

    @Test
    fun excludedKeys_coversTheLiteralShapes() {
        // Byte-compat pins for the highest-stakes names (a rename in a store
        // renames its export with it; these catch a policy-side regression).
        assertTrue("pin_hash" in PreferenceSyncPolicy.EXCLUDED_KEYS)
        assertTrue("pin_failed_attempts" in PreferenceSyncPolicy.EXCLUDED_KEYS)
        assertTrue("pin_lockout_until_ms" in PreferenceSyncPolicy.EXCLUDED_KEYS)
        assertTrue("active_server_id" in PreferenceSyncPolicy.EXCLUDED_KEYS)
        assertTrue("active_user_id" in PreferenceSyncPolicy.EXCLUDED_KEYS)
        assertTrue("device_id" in PreferenceSyncPolicy.EXCLUDED_KEYS)
    }

    @Test
    fun catalogDenylist_isThePolicySet() {
        // The generator and the DI registration consume the SAME set object:
        // the catalog denylists exactly what sync excludes.
        assertEquals(
            PreferenceSyncPolicy.EXCLUDED_KEYS,
            SettingsCatalogGenerator.DENYLIST,
        )
    }
}
