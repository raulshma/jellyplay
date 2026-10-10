package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.data.IntegrationsStoreFan
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The [ProfileSyncAdapter] that roams the NON-SECRET integration settings
 * (the settings-backup wave's `integrations` namespace): the Seerr connection
 * config and feature toggles ([SeerrPreferencesStore.SyncKeys]), the *arr
 * polling config ([ArrPreferencesStore.SyncKeys]), and the subtitle-provider
 * enable toggles ([SubtitleProviderPreferencesStore.SyncKeys]) — the stores'
 * sync-only surfaces are the single source of truth for which raw key names
 * may ever ride the wire. SECRETS NEVER SYNC: the *arr/Seerr/subtitle
 * credentials live in the encrypted stores, outside these allowlists, and no
 * key outside an allowlist can enter the snapshot or the apply path even if a
 * stale or hostile row names one.
 *
 * VALUE-ONLY, like prefs: [deletedKeys] stays the SPI default — a local
 * reset/disconnect means "back to defaults", never "deleted everywhere". A
 * remote `JsonNull` (or a tombstone routed through [deleteRemote]) RESETS the
 * key locally — the owning store WRITES that key's default value (never a
 * removal: an absent key could never roam back, so the server's
 * still-standing row would re-adopt on a later cycle and undo the reset; a
 * written default roams as an ordinary value write).
 *
 * The store surfaces speak RAW STRINGS (raw key → raw stored value or null);
 * this adapter translates them into JSON primitives with the prefs adapter's
 * encoding convention. The wire kind is inferred from the string's strict
 * parse (`true`/`false` → boolean, an integer → int, anything else → string):
 * harmless even for a string value that parses as a bool/int, because the
 * receiving side re-kinds by its own allowlist — the raw string is what
 * round-trips, the JSON kind is presentation.
 *
 * The mirror — the last-synced snapshot dirty detection diffs against — rides
 * the user-prefs DataStore under the reserved `jpsync.mirror.integrations.`
 * prefix (the shared [SyncMirror] idiom; the `jpsync.` reservation keeps every
 * mirror key out of every adapter's synced set, so state can't feed back).
 */
class JellyPlayIntegrationsSyncAdapter(
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val arrPreferencesStore: ArrPreferencesStore,
    private val subtitleProviderPreferencesStore: SubtitleProviderPreferencesStore,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    private val mirror = SyncMirror(mirrorStore, JpsyncReservation.mirrorPrefix(NAMESPACE))

    /** The three stores' shared snapshot/apply fan-out (the backup slice source rides the same one). */
    private val fan = IntegrationsStoreFan(
        seerrPreferencesStore = seerrPreferencesStore,
        arrPreferencesStore = arrPreferencesStore,
        subtitleProviderPreferencesStore = subtitleProviderPreferencesStore,
    )

    override suspend fun snapshot(): Map<String, JsonElement> = fan.snapshot()

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        for ((key, value) in entries) {
            when (value) {
                // Defensive: the engine routes tombstones through
                // [deleteRemote]; a null here is treated identically — the
                // key resets in its OWNING store, fanned out with the same
                // routing [apply] uses for values (each allowlist ignores
                // strangers).
                is JsonNull -> fan.apply(key, null)
                is JsonPrimitive -> fan.apply(key, value.content)
                // Objects/arrays unsupported in this namespace — skipped.
                else -> {}
            }
        }
    }

    /**
     * Applies remote tombstones as resets: the local entries AND their mirror
     * entries go, so an adopted namespace reset neither resurrects a value
     * nor re-reads as a local edit. The per-store allowlist ignores any key
     * that is not one of theirs.
     */
    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        for (key in keys) {
            fan.apply(key, null)
        }
        // The mirror entries go REGARDLESS of key shape: an unknown key must
        // not wedge in the mirror forever re-reading as deleted.
        mirror.clear(keys)
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    private companion object {
        const val NAMESPACE = "integrations"
    }
}
