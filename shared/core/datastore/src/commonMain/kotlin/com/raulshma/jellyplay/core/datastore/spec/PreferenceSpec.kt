package com.raulshma.jellyplay.core.datastore.spec

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * The typed-key storage a spec's canonical key name lives under in the owning
 * store's DataStore file. Determines how [PreferenceSpec.typedKey] rebuilds
 * the `Preferences.Key` and how the generic [PreferenceSpec.readStored] read
 * interprets the slot. Specs whose value is encoded by their owning store
 * (JSON blobs, enum-by-name strings, set-membership toggles) ride [STRING]
 * storage and supply their codec at the knob site via [PreferenceSpec.custom]
 * (Stage A) or as a [PreferenceEncoding] row (Stage B); the Stage B plain
 * rows ([PreferenceSpec.plainBoolean] / [PreferenceSpec.plainInt] /
 * [PreferenceSpec.plainFloat] / [PreferenceSpec.plainLong]) declare BOOLEAN /
 * INT / FLOAT / LONG storage so their typed slot — and its legacy string-key
 * fallback — can be rebuilt too.
 */
internal enum class PreferenceStorage { BOOLEAN, STRING, INT, FLOAT, LONG }

/**
 * The settings-search platform rule of a preference's catalog entry, as plain
 * data: which [PlatformKind] binaries offer the row. Exists so
 * core:datastore can declare availability without referencing core/ui (the
 * feature-layer catalog adapter expands [platforms] onto the rendered
 * `SettingsSearchItem`).
 *
 * Only the rules the declarations actually use exist — capability-derived
 * tags (`platformsForCapability`) stay feature-side (the binding's
 * `platforms` override) until their backing capability becomes spec data.
 * TV-only rows stay tagged ANDROID — form factor is the runtime
 * `LocalTvMode` axis, not a platform.
 */
enum class PreferencePlatformRule(val platforms: Set<PlatformKind>) {
    /** Offered on every platform — the catalog default. */
    ALL(PlatformKind.entries.toSet()),

    /**
     * Android-only rows (the TV watch-next row, the TV zoom mode): the
     * backing surface exists only in the Android binary's stack, so the row
     * must never surface as a search hit on desktop.
     */
    ANDROID_ONLY(setOf(PlatformKind.ANDROID)),

    /**
     * Desktop-only rows (the desktop-shell integrations: Discord Rich
     * Presence, the mpv-shim shell hooks): the backing surface exists only
     * in the desktop binary, so the row must never surface as a search hit
     * on Android.
     */
    DESKTOP_ONLY(setOf(PlatformKind.DESKTOP)),
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
 * it: the canonical persisted key name, the value type, the default value, the
 * reset category, the knob's optional settings-search metadata, and — for rows
 * declared with a Stage B encoding ([plainBoolean] / [plainInt] /
 * [plainFloat] / [enumRow] / [derived]) — the read and its inverse write as
 * plain data ([PreferenceEncoding]). Stage B landed: a store that declares its
 * keys as encoded rows derives its Keys members, its read-projection rows, its
 * single-key setters, its restore() rows and its resetKeysFor lists from the
 * declarations instead of maintaining six hand-written sites per preference —
 * and the persisted wire name is declared exactly once (the legacy string-key
 * fallback consults [keyName] itself, so the typed key and the pre-typed-era
 * fallback can never drift apart).
 *
 * A spec remains a declaration, not an accessor: the owning store binds it to
 * its persistence ([Knob.of] for typed access, or [readFrom] / [writeTo] for
 * the Stage B encodings), which guarantees the spec can never drift storage
 * (same key names, same encoding, same file layout).
 *
 * **Accepted Stage B residuals (the no-reflection rule):** the projection
 * bundle copy and the per-field slice-category merges stay hand-written plain
 * accessors (`PreferenceProjections.kt` / `SliceCategoryMergers.kt`,
 * R8-safe), and adding a new preference still means one write-through test
 * line next to its row — the derivation covers key identity, encodings and
 * reset lists, not the slice plumbing or its coverage.
 */
class PreferenceSpec<T> internal constructor(
    internal val storage: PreferenceStorage,
    /**
     * The Stage B encoding (read + optional derived write), or null for a
     * Stage A declaration — [boolean] / [string] / [custom] rows keep their
     * generic [readStored] (or knob-supplied) read and have no derived write.
     */
    internal val encoding: PreferenceEncoding<T>?,
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
    /**
     * The enum constant names when this row is an [enumRow] declaration (the
     * persisted-by-name wire vocabulary), else null. Catalog metadata only —
     * captures `enumValues<E>()` at declaration so the settings-catalog
     * generator can advertise the allowed values without reflection.
     */
    internal val enumOptions: List<String>? = null,
    /**
     * The advertised numeric bounds, or null when the row has none. Catalog
     * metadata only — documentation for the settings-catalog generator and
     * the plugin's admin-defaults validation; nothing here enforces anything
     * at read or write time (the owning store's setter stays the clamp
     * owner). Declare a bound only where the store visibly clamps, so the
     * catalog cannot advertise a range the client does not honor. Bounds
     * travel as Double (exact through 2^53 — fine for every real bound;
     * don't declare epoch-scale ranges).
     */
    internal val min: Double? = null,
    internal val max: Double? = null,
) {

    /**
     * Rebuilds the typed DataStore key for [keyName]. Equal by name to the
     * owning store's hand-declared key for the same slot (`Preferences.Key`
     * equality is name-based), so spec-derived keys interoperate with the
     * store's existing `Keys` object without rewiring it.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun typedKey(): Preferences.Key<T> = typedKeyNamed(keyName)

    /**
     * Rebuilds the row's typed key under an overridden wire [name], keeping
     * the row's declared storage type — the spec-side key-rebuild hook for
     * stores that namespace their keys per active user
     * (`HomeDiscoveryStore`'s `u_<userId>::<canonical>` grammar): the
     * namespaced key is rebuilt from the row's ONE declared [keyName] plus
     * the row's storage, so a namespaced slot can never drift its canonical
     * declaration (same name-based equality guarantee as [typedKey]).
     */
    @Suppress("UNCHECKED_CAST")
    internal fun typedKeyNamed(name: String): Preferences.Key<T> = when (storage) {
        PreferenceStorage.BOOLEAN -> booleanPreferencesKey(name)
        PreferenceStorage.STRING -> stringPreferencesKey(name)
        PreferenceStorage.INT -> intPreferencesKey(name)
        PreferenceStorage.FLOAT -> floatPreferencesKey(name)
        PreferenceStorage.LONG -> longPreferencesKey(name)
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
            PreferenceStorage.INT -> prefs[intPreferencesKey(keyName)]
            PreferenceStorage.FLOAT -> prefs[floatPreferencesKey(keyName)]
            PreferenceStorage.LONG -> prefs[longPreferencesKey(keyName)]
        }
        @Suppress("UNCHECKED_CAST")
        return (stored ?: default) as T
    }

    /**
     * Stage B derived read: the encoding's read of this row from a raw
     * snapshot. Only rows declared with a Stage B encoding support this —
     * Stage A declarations keep their generic [readStored] (or
     * knob-supplied) read.
     */
    internal fun readFrom(prefs: Preferences): T =
        checkNotNull(encoding) {
            "spec '$keyName' declares no Stage B encoding — read it via readStored or its knob"
        }.read(prefs)

    /**
     * Stage B derived write: the encoding's inverse write of [value] into the
     * row's slot (restore rows / single-key setters). A [Custom] encoding opts
     * out by omitting its `encode` lambda — the owning store keeps its
     * hand-written line there.
     */
    internal fun writeTo(prefs: MutablePreferences, value: T) {
        val encoding = checkNotNull(encoding) { "spec '$keyName' declares no Stage B encoding" }
        val write = checkNotNull(encoding.write) {
            "spec '$keyName' declares no derived write (give its Custom encoding an encode lambda)"
        }
        write(prefs, value)
    }

    companion object {
        /** Boolean slot under a typed boolean key. */
        fun boolean(
            keyName: String,
            default: Boolean,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<Boolean> =
            PreferenceSpec(PreferenceStorage.BOOLEAN, null, keyName, default, resetCategory, search)

        /** Raw string slot, absent by default (e.g. a nullable override). */
        fun string(
            keyName: String,
            default: String? = null,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<String?> =
            PreferenceSpec(PreferenceStorage.STRING, null, keyName, default, resetCategory, search)

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
        ): PreferenceSpec<T> =
            PreferenceSpec(PreferenceStorage.STRING, null, keyName, default, resetCategory, search)

        // ------------------------------------------------------------------
        // Stage B rows: the declaration carries the read and its inverse
        // write, so the owning store derives its read projection, restore,
        // single-key setters and reset lists from the rows.
        // ------------------------------------------------------------------

        /**
         * Typed boolean slot read through the shared legacy-string fallback
         * ([PreferenceCodec.readBool]): the pre-typed-era wire name IS
         * [keyName], so the name the fallback consults and the name the typed
         * key is built from are declared exactly once. The derived write is
         * the plain typed slot write.
         */
        internal fun plainBoolean(
            keyName: String,
            default: Boolean,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<Boolean> {
            val key = booleanPreferencesKey(keyName)
            return PreferenceSpec(
                storage = PreferenceStorage.BOOLEAN,
                encoding = PlainCodec(
                    readSlot = { prefs -> PreferenceCodec.readBool(prefs, key, keyName, default) },
                    writeSlot = { prefs, value -> prefs[key] = value },
                ),
                keyName = keyName,
                default = default,
                resetCategory = resetCategory,
                search = search,
            )
        }

        /**
         * Typed int slot read through the shared legacy-string fallback
         * ([PreferenceCodec.readInt]); the wire name IS [keyName] — see
         * [plainBoolean]. The derived write is the plain typed slot write.
         * [min] and [max] are settings-catalog metadata only — no runtime
         * enforcement.
         */
        internal fun plainInt(
            keyName: String,
            default: Int,
            resetCategory: PreferenceResetCategory? = null,
            min: Int? = null,
            max: Int? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<Int> {
            val key = intPreferencesKey(keyName)
            return PreferenceSpec(
                storage = PreferenceStorage.INT,
                encoding = PlainCodec(
                    readSlot = { prefs -> PreferenceCodec.readInt(prefs, key, keyName, default) },
                    writeSlot = { prefs, value -> prefs[key] = value },
                ),
                keyName = keyName,
                default = default,
                resetCategory = resetCategory,
                search = search,
                min = min?.toDouble(),
                max = max?.toDouble(),
            )
        }

        /**
         * Typed long slot read through the shared legacy-string fallback
         * ([PreferenceCodec.readLong]); the wire name IS [keyName] — see
         * [plainBoolean]. The derived write is the plain typed slot write.
         */
        internal fun plainLong(
            keyName: String,
            default: Long,
            resetCategory: PreferenceResetCategory? = null,
            min: Long? = null,
            max: Long? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<Long> {
            val key = longPreferencesKey(keyName)
            return PreferenceSpec(
                storage = PreferenceStorage.LONG,
                encoding = PlainCodec(
                    readSlot = { prefs -> PreferenceCodec.readLong(prefs, key, keyName, default) },
                    writeSlot = { prefs, value -> prefs[key] = value },
                ),
                keyName = keyName,
                default = default,
                resetCategory = resetCategory,
                search = search,
                min = min?.toDouble(),
                max = max?.toDouble(),
            )
        }

        /**
         * Typed float slot read through the shared legacy-string fallback
         * ([PreferenceCodec.readFloat]); the wire name IS [keyName] — see
         * [plainBoolean]. [transform] post-processes the decoded value (the
         * clamp-style read policies some slots carry); the derived write
         * stores the raw value, leaving the setter as the clamp owner.
         */
        internal fun plainFloat(
            keyName: String,
            default: Float,
            resetCategory: PreferenceResetCategory? = null,
            transform: (Float) -> Float = { it },
            min: Float? = null,
            max: Float? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<Float> {
            val key = floatPreferencesKey(keyName)
            return PreferenceSpec(
                storage = PreferenceStorage.FLOAT,
                encoding = PlainCodec(
                    readSlot = { prefs -> transform(PreferenceCodec.readFloat(prefs, key, keyName, default)) },
                    writeSlot = { prefs, value -> prefs[key] = value },
                ),
                keyName = keyName,
                default = default,
                resetCategory = resetCategory,
                search = search,
                min = min?.toDouble(),
                max = max?.toDouble(),
            )
        }

        /**
         * Enum-by-name string slot: the stored constant name parsed against
         * [E], the declared [default] served on absence or corruption (the
         * repo-wide [toEnumOrNull] parse seam). The derived write persists
         * `value.name`.
         */
        internal inline fun <reified E : Enum<E>> enumRow(
            keyName: String,
            default: E,
            resetCategory: PreferenceResetCategory? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<E> {
            val key = stringPreferencesKey(keyName)
            return PreferenceSpec(
                storage = PreferenceStorage.STRING,
                encoding = EnumRow(key, default) { raw -> raw.toEnumOrNull<E>() },
                keyName = keyName,
                default = default,
                resetCategory = resetCategory,
                search = search,
                enumOptions = enumValues<E>().map { it.name },
            )
        }

        /**
         * String-keyed slot whose value the owning store DERIVES from the
         * slot (plus, when the value depends on them, sibling keys it reads
         * off [Preferences]): [read] receives the raw snapshot AND the row's
         * own prefetched raw slot value ([raw], null when the key is absent),
         * so the lambda never re-declares the key — the wire name stays
         * declared exactly once, in [keyName].
         *
         * [encode], when present, derives the inverse write (the encoded
         * string is stored to the same slot — restore rows / single-key
         * setters); leaving it null keeps the owning store's hand-written
         * line, the documented opt-out for slots whose write carries extra
         * semantics. Stage A's [custom] is the declaration-only variant of
         * this row (knob-supplied read, no derived write).
         */
        internal fun <T> derived(
            keyName: String,
            default: T,
            resetCategory: PreferenceResetCategory? = null,
            read: (prefs: Preferences, raw: String?) -> T,
            encode: ((value: T) -> String)? = null,
            search: PreferenceSearchSpec? = null,
        ): PreferenceSpec<T> {
            val key = stringPreferencesKey(keyName)
            return PreferenceSpec(
                storage = PreferenceStorage.STRING,
                encoding = Custom(key, read, encode),
                keyName = keyName,
                default = default,
                resetCategory = resetCategory,
                search = search,
            )
        }
    }
}
