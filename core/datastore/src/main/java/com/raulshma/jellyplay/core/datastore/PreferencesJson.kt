package com.raulshma.jellyplay.core.datastore

import kotlinx.serialization.json.Json

/**
 * Shared [Json] configurations for preference import/export. Centralizing them
 * keeps export and import symmetric, gives the full-preferences export its own
 * clearly-named config (rather than borrowing a preset-specific one), and
 * avoids ad-hoc `Json { ... }` instances scattered across ViewModels.
 */
object PreferencesJson {
    /** Pretty-printed export config (also used for the full-preferences export). */
    val export: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Import config — tolerant of missing/new fields so older exports still load. */
    val import: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Config used to serialize the full [com.raulshma.jellyplay.core.model.legacy.UserPreferences]. */
    val fullPreferences: Json = export
}
