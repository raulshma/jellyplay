package com.raulshma.jellyplay.core.datastore.spec

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

/**
 * Stage B of the spec machinery: HOW a preference row's value is derived from
 * a raw DataStore snapshot (the read) and — when the inverse is mechanical —
 * written back into the slot (the write), as plain data on the [PreferenceSpec]
 * row. A store that declares its keys as Stage B rows derives its read
 * projection, its `restore` rows, its single-key setters and (from the rows'
 * reset categories) its reset-key lists from these encodings, instead of
 * hand-maintaining a second copy of every encoding beside the declaration.
 * The persisted wire name is declared exactly once per row: the legacy
 * string-key fallback consults `keyName` itself, so the typed key and the
 * pre-typed-era fallback can never drift apart.
 *
 * Three encodings exist, one per read shape preference stores actually have:
 *
 *  - [PlainCodec] — the typed slot + legacy string-key fallback dance of
 *    `PreferenceCodec.readBool/readInt/readFloat` (booleans / ints / floats
 *    that took part in the one-shot typed-key migration).
 *  - [EnumRow] — the enum-by-name string slot: parse the stored constant
 *    name, fall back to the declared default on absence or corruption; the
 *    derived write persists `value.name`.
 *  - [Custom] — a store-derived read over the row's own string slot (plus any
 *    sibling keys the value depends on); `encode` opts into the derived write
 *    (encode the value to its stored string). Omitting `encode` keeps the
 *    store's hand-written restore/setter line — the documented opt-out for
 *    slots whose write carries extra semantics.
 *
 * Deliberately out of scope (the accepted no-reflection residual): slice-to-
 * slice moves — the projection-bundle copy and the per-field category merges
 * stay hand-written plain accessors (`PreferenceProjections.kt` /
 * `SliceCategoryMergers.kt`, R8-safe). Encodings derive single-slot reads and
 * writes only.
 */
internal sealed interface PreferenceEncoding<T> {

    /** Derives the row's value from a raw snapshot. */
    fun read(prefs: Preferences): T

    /**
     * The derived inverse write of a value into its slot, or null when the
     * row opts out and the owning store keeps its hand-written line.
     */
    val write: ((MutablePreferences, T) -> Unit)?
}

/**
 * The [PreferenceSpec.plainBoolean] / [PreferenceSpec.plainInt] /
 * [PreferenceSpec.plainFloat] encoding: the typed-slot read (with the legacy
 * string-key fallback already baked into the factory-built read lambda) and
 * the plain typed-slot write.
 */
internal class PlainCodec<T> internal constructor(
    private val readSlot: (Preferences) -> T,
    private val writeSlot: (MutablePreferences, T) -> Unit,
) : PreferenceEncoding<T> {
    override fun read(prefs: Preferences): T = readSlot(prefs)
    override val write: (MutablePreferences, T) -> Unit = writeSlot
}

/**
 * The [PreferenceSpec.enumRow] encoding: parse the stored constant name —
 * null on absence or corruption means the declared default — and the derived
 * write persists `value.name`.
 */
internal class EnumRow<T : Enum<T>> internal constructor(
    private val key: Preferences.Key<String>,
    private val default: T,
    private val parse: (String?) -> T?,
) : PreferenceEncoding<T> {
    override fun read(prefs: Preferences): T = parse(prefs[key]) ?: default
    override val write: (MutablePreferences, T) -> Unit = { prefs, value -> prefs[key] = value.name }
}

/**
 * The [PreferenceSpec.derived] encoding: the store derives the value from the
 * snapshot plus the row's prefetched raw string slot ([raw], null when the
 * key is absent — prefetched so the lambda never re-declares the key);
 * [encode] derives the inverse write when given.
 */
internal class Custom<T> internal constructor(
    private val key: Preferences.Key<String>,
    private val readSlot: (prefs: Preferences, raw: String?) -> T,
    private val encode: ((T) -> String)?,
) : PreferenceEncoding<T> {
    override fun read(prefs: Preferences): T = readSlot(prefs, prefs[key])
    override val write: ((MutablePreferences, T) -> Unit)? =
        encode?.let { enc -> { prefs: MutablePreferences, value: T -> prefs[key] = enc(value) } }
}
