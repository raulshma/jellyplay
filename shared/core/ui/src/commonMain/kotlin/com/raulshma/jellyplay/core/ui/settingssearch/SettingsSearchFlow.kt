package com.raulshma.jellyplay.core.ui.settingssearch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * Debounce for the local settings-search pipeline. Shorter than the networked
 * media-search debounce (MediaSearchEngine in core:data): the fuzzy matcher is
 * pure and sub-millisecond, so results feel instant while the Jellyfin/Seerr
 * requests are still in flight.
 */
private const val SETTINGS_SEARCH_DEBOUNCE_MS: Long = 120

/**
 * Local settings-search pipeline for the home search bar: debounces the
 * caller's raw query flow, then fuzzy-matches it against the locale-resolved
 * items supplied by [provider] (the settings-search catalog bound at app
 * level). Resolves the catalog's `StringResource`s through the
 * suspend Compose-Resources
 * [resolve][com.raulshma.jellyplay.core.ui.settingssearch.resolve] — no
 * Android [Context] needed.
 *
 * Resolve-once-per-subscription, match-many: the catalog is resolved and
 * [prepared][SettingsSearchMatcher.prepare] lazily on the FIRST non-blank
 * query and every later keystroke re-runs only the matcher against the
 * prepared candidates (the former per-keystroke catalog re-resolve was
 * ~774 `getString` calls + 258 allocations per character typed). The debounce,
 * distinct-until-changed and [flatMapLatest] (a newer query cancels the
 * previous matcher run — no stale emission can land out of order) sit inside
 * one collection, so callers must NOT re-key their producer on the query: the
 * internal debounce owns the cadence.
 *
 * A blank query emits the empty list without touching the catalog. The
 * Appearance-level "settings in home search" gate is the caller's concern:
 * callers not rendering settings results simply don't collect this flow.
 * Matching runs on [Dispatchers.Default] so a long catalog scan never lands
 * on the main thread.
 *
 * Locale caveat: the resolved snapshot lives for the whole subscription. On
 * Android a per-app locale switch recreates the activity, restarting the
 * subscription and re-resolving; on desktop the in-app setter is a no-op
 * (DesktopAppLocaleSetter — the locale is OS-level and needs a process
 * restart to affect `getString` either way), so the snapshot cannot go stale
 * in-process.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun settingsSearchResults(
    queries: Flow<String>,
    provider: SettingsSearchProvider,
): Flow<List<ResolvedSettingsItem>> = flow {
    // The per-subscription memo: null until the first non-blank query pays
    // the resolve, then the prepared catalog for every keystroke after it.
    var prepared: List<SettingsSearchMatcher.PreparedItem>? = null
    queries
        .debounce(SETTINGS_SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            when {
                query.isBlank() -> flowOf(emptyList())
                // Resolve through the provider's platform-filtered funnel
                // (Android-only rows never surface as hits here), then
                // fuzzy-match against the translated text so a user typing
                // in their own language still finds settings.
                else -> flow {
                    val candidates = prepared
                        ?: SettingsSearchMatcher.prepare(provider.resolved())
                            .also { prepared = it }
                    emit(SettingsSearchMatcher.search(query, candidates))
                }
            }
        }
        .collect { emit(it) }
}
    .flowOn(Dispatchers.Default)
