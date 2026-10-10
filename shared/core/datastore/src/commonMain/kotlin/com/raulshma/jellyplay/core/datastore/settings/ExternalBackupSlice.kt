package com.raulshma.jellyplay.core.datastore.settings

import kotlinx.serialization.json.JsonElement

/**
 * A backup slice contributed OUTSIDE the domain-store fan-out — Room-backed
 * config (item prefs, playlists) and stores that predate/escape the slice
 * pattern (the integration stores' sync surfaces, the widget config keys).
 * [com.raulshma.jellyplay.core.datastore.UserPreferencesStore] cannot depend
 * on Room DAOs, so the concrete sources live in core:data and are injected
 * here through this seam (Koin `getAll`).
 *
 * SEMANTICS — WHOLESALE REPLACE: an incoming slice REPLACES the domain's rows.
 * A row whose identity key is absent from the incoming element is DELETED (or
 * reset to its default), the rest are upserted. A missing slice key in the
 * backup means "slice omitted" (an older v2 export that predates it) — the
 * store is left untouched, never emptied. Identity keying and row tolerance
 * (a malformed entry is skipped, not fatal) belong to the source; the store
 * only routes by [key].
 *
 * CATEGORY FILTERING DOES NOT APPLY: external slices have no
 * [com.raulshma.jellyplay.core.model.PreferenceResetCategory] — they restore
 * on the Import-All-grade paths (`restoreV2`, and `restoreV2Categories` with
 * `includeExtras = true`) and through the import preview's own per-slice
 * cards, never as a side effect of a category import.
 */
interface ExternalBackupSlice {
    /** The [com.raulshma.jellyplay.core.datastore.BackupSliceKey] this slice rides. */
    val key: String

    /**
     * The live slice state, or `null` when there is nothing to back up (the
     * slice is omitted from the export entirely — an empty object would read
     * as an explicit "wipe on import").
     */
    suspend fun read(): JsonElement?

    /**
     * Applies the incoming slice wholesale (see the class KDoc). Lenient by
     * contract: a malformed entry is skipped; the slice never throws for
     * forward-compat reasons.
     */
    suspend fun restore(element: JsonElement)
}
