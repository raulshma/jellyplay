package com.raulshma.jellyplay.core.datastore.spec

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Generic typed access to one [PreferenceSpec]-declared preference knob: a
 * cold [value] flow plus a [set] write command. Created only by the owning
 * store via [of], which binds the spec to the store's own persistence — the
 * read rides the store's preferences flow (same catch-to-empty policy) and
 * the write IS (or routes through) the store's setter, so a knob can never
 * become a second write path around existing storage semantics.
 *
 * Knobs are additive access for spec consumers (Stage A pilot); the store's
 * slice projection and setters remain the machinery the knobs are built
 * from, unchanged.
 */
class Knob<T> internal constructor(
    /** The declaration this knob is bound to. */
    val spec: PreferenceSpec<T>,
    /** Cold flow of the knob's current value; distinct until changed. */
    val value: Flow<T>,
    private val write: suspend (T) -> Unit,
) {
    /** Write command — delegates to the owning store's setter logic. */
    suspend fun set(value: T) = write(value)

    companion object {
        /**
         * Binds [spec] to a store's persistence: [prefs] is the store's
         * shared preferences flow, [write] the store's setter (or a command
         * built on it), and [read] the value projection — defaulting to the
         * spec's generic stored read, overridden by stores whose value is
         * derived from the raw slot by their own codec.
         */
        internal fun <T> of(
            spec: PreferenceSpec<T>,
            prefs: Flow<Preferences>,
            write: suspend (T) -> Unit,
            read: (Preferences) -> T = spec::readStored,
        ): Knob<T> = Knob(spec, prefs.map(read).distinctUntilChanged(), write)
    }
}
