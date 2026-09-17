package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The ONE corrupt-read arm for preference stores, replacing the three drifted
 * `.catch` spellings the hand-copied read chains had accumulated. A failed
 * `data` read degrades to [emptyPreferences] — i.e. every downstream read
 * projection sees the all-defaults snapshot and keeps its per-key
 * clamping / derivation semantics on the degraded path too.
 *
 * **Declared policy:** a corrupt read never throws at collectors, never
 * silently skips the read projection, and never leaves a store frozen at its
 * `stateIn` seed (the former `.catch { _ -> emptyPreferences() }` spelling
 * coerced the lambda value to `Unit`, so the flow completed empty and the
 * eager StateFlow froze at its seed without the projection ever running).
 * The degraded upstream stays quiescent after the failure — one
 * defaults-shaped emission, no retry storm, no completion of StateFlow
 * subscribers.
 */
internal fun DataStore<Preferences>.dataDegradingToDefaults(): Flow<Preferences> =
    data.catch { _ -> emit(emptyPreferences()) }

/**
 * The ONE read chassis for preference stores: eagerly-shared [StateFlow] of a
 * store's slice, replacing the per-store
 * `data.catch{…}.map{read}.distinctUntilChanged().stateIn(Eagerly, seed)`
 * hand copies (sharing policy was `Eagerly` at every site; the seed is always
 * the slice's defaults).
 *
 * The corrupt-read policy is [dataDegradingToDefaults]'s: a failed read
 * degrades to the all-defaults snapshot run through the same [read]
 * projection. Callers pass the slice projection they already own — the
 * chassis owns only the catch semantics and the sharing shape.
 */
internal fun <T> DataStore<Preferences>.sliceStateFlow(
    scope: CoroutineScope,
    seed: T,
    read: (Preferences) -> T,
): StateFlow<T> = dataDegradingToDefaults()
    .map(read)
    .distinctUntilChanged()
    .stateIn(scope, SharingStarted.Eagerly, seed)
