package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

/**
 * The last-synced snapshot one adapter's dirty detection diffs against — the
 * mirror idiom all the ADR 0011 state-surface adapters share, extracted once
 * (ADR 0009's hand-written mandate covers settings-row emission, not this
 * plumbing): the entries ride the SAME user-prefs DataStore the prefs adapter
 * mirrors into, under the reserved `jpsync.mirror.<ns>.` prefix (minted by
 * [JpsyncReservation.mirrorPrefix]) — the `jpsync.` reservation keeps every
 * mirror key out of every adapter's synced set, so state can't feed back.
 *
 * The mirror holds, per synced key, the wire value at the moment the server
 * last confirmed it: current != mirrored == dirty ([dirtyValues]); mirrored
 * without current == deleted locally ([deletedKeys]); a server-confirmed
 * write lands via [markSynced]; a server-adopted delete clears via [clear]
 * so it never resurrects or re-reads as a local edit.
 *
 * An adapter whose namespace carries its own exclusion rule (secrets,
 * per-device namespaces — the prefs adapter) passes [shouldMirror] so the
 * mirror operations skip those keys on every face; the mirror comparison
 * itself still has exactly one implementation. Default: mirror everything —
 * the store-backed namespaces' keys are all mirrorable.
 */
class SyncMirror(
    private val mirrorStore: DataStore<Preferences>,
    /** The reserved `jpsync.mirror.<ns>.` prefix this mirror's entries live under. */
    private val prefix: String,
    /** The adapter's exclusion rule — keys failing it never enter, dirty-read, or clear. */
    private val shouldMirror: (String) -> Boolean = { true },
) {

    private fun mirrorKeyOf(key: String) = stringPreferencesKey(prefix + key)

    /**
     * The subset of [current] that changed since the last [markSynced] — the
     * [ProfileSyncAdapter.dirtyValues] body.
     */
    suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> {
        val prefs = mirrorStore.data.first()
        return current.filter { (key, value) ->
            if (!shouldMirror(key)) return@filter false
            val mirrored = prefs[mirrorKeyOf(key)]
            // Never-synced key = dirty by definition (mirror holds no entry).
            mirrored == null || mirrored != value.toString()
        }
    }

    /**
     * The keys [current] no longer holds but the mirror still does — the
     * [ProfileSyncAdapter.deletedKeys] body (mirror held them, the store
     * removed them: the outbound tombstone set).
     */
    suspend fun deletedKeys(current: Map<String, JsonElement>): Set<String> {
        val prefs = mirrorStore.data.first()
        return prefs.asMap().keys
            .filter { it.name.startsWith(prefix) }
            .map { it.name.removePrefix(prefix) }
            .filter { it !in current }
            .toSet()
    }

    /**
     * Records [values] as the last-synced state so they stop reading as
     * dirty — the [ProfileSyncAdapter.markSynced] body.
     */
    suspend fun markSynced(values: Map<String, JsonElement>) {
        if (values.isEmpty()) return
        mirrorStore.edit { prefs ->
            values.forEach { (key, value) ->
                if (!shouldMirror(key)) return@forEach
                prefs[mirrorKeyOf(key)] = value.toString()
            }
        }
    }

    /**
     * Drops the entries for [keys] — the tail every [deleteRemote] override
     * shares: an adopted delete (inbound or the applied outbound tombstone's
     * confirmation) must clear its mirror entry, and it does so REGARDLESS of
     * key shape — an unparseable key must not wedge in the mirror forever
     * re-reading as deleted. Keys failing [shouldMirror] are skipped: the
     * exclusion rule rides every mirror face, so an adapter's excluded names
     * never touch mirror state on the delete path either (they can hold no
     * entry anyway — they never synced).
     */
    suspend fun clear(keys: Set<String>) {
        if (keys.isEmpty()) return
        mirrorStore.edit { prefs ->
            keys.forEach { if (shouldMirror(it)) prefs.remove(mirrorKeyOf(it)) }
        }
    }
}
