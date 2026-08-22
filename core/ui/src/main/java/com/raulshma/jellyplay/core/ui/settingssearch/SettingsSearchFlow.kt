package com.raulshma.jellyplay.core.ui.settingssearch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Debounce for the local settings-search pipeline. Shorter than the networked
 * media-search debounce (MediaSearchEngine in core:data): the fuzzy matcher is
 * pure and sub-millisecond, so results feel instant while the Jellyfin/Seerr
 * requests are still in flight.
 */
private const val SETTINGS_SEARCH_DEBOUNCE_MS: Long = 120

/**
 * Local settings-search pipeline for the home search bar:
 * debounces the caller's raw query flow, then fuzzy-matches it against the
 * locale-resolved items supplied by [provider] (the settings-search catalog
 * bound at app level). Lives in the UI layer because it needs an Android
 * [Context] to resolve the catalog's `@StringRes` ids.
 *
 * A blank query emits the empty list without touching the catalog. The
 * Appearance-level "settings in home search" gate is the caller's concern:
 * callers not rendering settings results simply don't collect this flow.
 * Matching runs on [Dispatchers.Default] so a long catalog scan never lands
 * on the main thread.
 */
@OptIn(FlowPreview::class)
fun settingsSearchResults(
    queries: Flow<String>,
    context: Context,
    provider: SettingsSearchProvider,
): Flow<List<ResolvedSettingsItem>> =
    queries
        .debounce(SETTINGS_SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .map { query ->
            if (query.isBlank()) {
                emptyList()
            } else {
                // Resolve the catalog's @StringRes ids to the current locale
                // once per query, then fuzzy-match against the translated text
                // so a user typing in their own language still finds settings.
                val resolved = provider.items.resolve(context::getString)
                SettingsSearchMatcher.search(query, resolved)
            }
        }
        .flowOn(Dispatchers.Default)
