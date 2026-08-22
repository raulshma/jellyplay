package com.raulshma.jellyplay.core.ui.settingssearch

/**
 * The seam through which consumers (the home header search, the in-settings
 * search) obtain the settings-search catalog. The item lists themselves live
 * in `:feature:settings`, co-located with the screens they deep-link into
 * (see `SettingsSearchCatalog` there); core/ui only defines the shape.
 *
 * Declared here — not in feature/settings — so feature/home can depend on
 * core/ui alone and still reach the items: it injects this interface, and the
 * Hilt binding to the real catalog (provided by feature/settings's
 * `SettingsSearchModule`) resolves at app level, keeping the Gradle star
 * topology intact.
 */
interface SettingsSearchProvider {
    /** All searchable settings items; implementors keep a stable, curated order. */
    val items: List<SettingsSearchItem>
}
