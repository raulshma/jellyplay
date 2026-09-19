package com.raulshma.jellyplay.core.datastore.spec

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * The typed-key storage a spec's canonical key name lives under in the owning
 * store's DataStore file. Determines how [PreferenceSpec.typedKey] rebuilds
 * the `Preferences.Key` and how the generic [PreferenceSpec.readStored] read
 * interprets the slot. Specs whose value is encoded by their owning store
 * (JSON blobs, enum-by-name strings, set-membership toggles) ride [STRING]
 * storage and supply their codec at the knob site via
 * [PreferenceSpec.custom].
 */
internal enum class PreferenceStorage { BOOLEAN, STRING }

/**
 * The settings-search platform rule of a preference's catalog entry, as plain
 * data: which [PlatformKind] binaries offer the row. Exists so
 * core:datastore can declare availability without referencing core/ui (the
 * feature-layer catalog adapter expands [platforms] onto the rendered
 * `SettingsSearchItem`).
 *
 * Only the rules the declarations actually use exist — capability-derived
 * tags (`platformsForCapability`) stay feature-side until their backing
 * capability becomes spec data, and `ANDROID_ONLY` returns with its first
 * Android-only declaration (TV-only rows will want it when they migrate:
 * form factor is the runtime `LocalTvMode` axis, not a platform).
 */
enum class PreferencePlatformRule(val platforms: Set<PlatformKind>) {
    /** Offered on every platform — the catalog default. */
    ALL(PlatformKind.entries.toSet()),
}

/**
 * A catalog entry's search metadata as PLAIN DATA — string resource KEYS, not
 * resource objects; a route KIND id, not a Route. Lives with the preference
 * declaration in core:datastore so a knob and its searchability are declared
 * once, at the owner; the settings feature binds the keys to real resources
 * (`StringResource` / `ImageVector` / `Route`) in its per-domain
 * `*SearchItems.kt` mapping table.
 *
 * Standalone entries (a screen's own navigation row, a cross-screen entry
 * point like the *arr settings hub) are declared as bare specs with no
 * backing [PreferenceSpec] — they are catalog facts, not knobs.
 */
data class PreferenceSearchSpec(
    /** The stable catalog id (the deep-link `highlightSettingId` target). */
    val id: String,
    /** Resource key of the row title, e.g. `"ss_HOME_CARD_CLIPPING_title"`. */
    val titleKey: String,
    /** Resource key of the row subtitle. */
    val subtitleKey: String,
    /** Resource key of the category chip, e.g. `"ss_cat_experimental"`. */
    val categoryKey: String,
    /** Fuzzy-match keywords, lowercase. */
    val keywords: List<String>,
    /**
     * The route KIND this entry deep-links into, e.g.
     * `"experimental_settings"` — a plain id the feature layer maps to the
     * actual `Route` in its per-domain routeKind → Route map.
     */
    val routeKind: String,
    /** Whether the row hides behind the advanced-settings gate. */
    val isAdvanced: Boolean = false,
    /** Where the row is offered; see [PreferencePlatformRule]. */
    val platformRule: PreferencePlatformRule = PreferencePlatformRule.ALL,
)

/**
 * One preference knob's single declaration, owned by the store that persists
 * it (Stage A pilot of the spec machinery): the canonical persisted key name,
 * the value type, the default value, the reset category, and the knob's
 * optional settings-search metadata — all plain data, no Compose / resource /
 * Route types.
 *
 * A spec is a declaration, not an accessor: the owning store binds it to its
 * existing key / read / setter machinery via [Knob.of], which guarantees the
 * spec can never drift persistence (same key names, same encoding, same file
 * layout). Derived facts (reset key lists, slice reads) are Stage B; today
 * the spec is the searchable, testable contract and the knob the typed
 * access built on top of the store's own paths.
 */
class PreferenceSpec<T> internal constructor(
    internal val storage: PreferenceStorage,
    /** The canonical persisted key name — must match the owning store's key. */
    val keyName: String,
    /** The value served when the key is absent (or holds an undecodable slot). */
    val default: T,
    /**
     * The [PreferenceResetCategory] this knob resets under, or null for
     * one-time / runtime state that never resets. Declares the logical
     * category; which store's reset list actually carries the key remains the
     * reset-owner ledger documented per store.
     */
    val resetCategory: PreferenceResetCategory?,
    /** The knob's settings-search entry, when the knob is searchable. */
    val search: PreferenceSearchSpec?,
) {

    /**
     * Rebuilds the typed DataStore key for [keyName]. Equal by name to the
     * owning store's hand-declared key for the same slot (`Preferences.Key`
     * equality is name-based), so spec-derived keys interoperate with the
     * store's existing `Keys` object without rewiring it.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun typedKey(): Preferences.Key<T> = when (storage) {
        PreferenceStorage.BOOLEAN -> booleanPreferencesKey(keyName)
        PreferenceStorage.STRING -> stringPreferencesKey(keyName)
    } as Preferences.Key<T>

    /**
     * Generic typed read for the plain storages: the stored slot or
     * [default]. Specs created with [custom] must NOT use this read (their
     * value is derived from the raw slot by the store's codec) — their knob
     * supplies the read instead.
     */
    internal fun readStored(prefs: Preferences): T {
        val stored: Any? = when (storage) {
            PreferenceStorage.BOOLEAN -> prefs[booleanPreferencesKey(keyName)]
            PreferenceStorage.STRING -> prefs[stringPreferencesKey(keyName)]
        }
        @Suppress("UNCHECKED_CAST")
        return (stored ?: default) as T
    }

    companion object {
        /** Boolean slot under a typed boolean key. */
        fun boolean(
            keyName: String,
            default: Boolean,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<Boolean> = PreferenceSpec(PreferenceStorage.BOOLEAN, keyName, default, resetCategory, search)

        /** Raw string slot, absent by default (e.g. a nullable override). */
        fun string(
            keyName: String,
            default: String? = null,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<String?> = PreferenceSpec(PreferenceStorage.STRING, keyName, default, resetCategory, search)

        /**
         * String-keyed slot whose value the owning store encodes/derives
         * (JSON blob, enum-by-name, set-membership toggle, …): the spec
         * declares the slot and the default; the knob site supplies the
         * read (and the store's setter stays the write).
         */
        fun <T> custom(
            keyName: String,
            default: T,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<T> = PreferenceSpec(PreferenceStorage.STRING, keyName, default, resetCategory, search)
    }
}
