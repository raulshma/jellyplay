package com.raulshma.jellyplay.feature.music.collection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The list-sourced twin of [SortedPagedCollection] for the music collections
 * whose server read returns a plain list (genres, playlists) rather than a
 * paged query. Owns the whole "load → items/isLoading/error, refresh" seam in
 * one place — the load ladder the browse tab's genres/playlists pages never
 * had and the standalone screens each hand-rolled. [load] receives the force
 * flag (`refresh()` forces, the initial load does not) and returns the
 * repository result. [error] carries the failure itself (null = success) —
 * the ladder renders `error.message` with the kind's
 * [MusicCollectionKind.errorFallbackRes] as the null-message fallback, so no
 * message string is baked here.
 *
 * [refresh] is supersession-safe: only the newest refresh's result is applied,
 * so an older in-flight load can neither clear the loading flag early nor
 * overwrite a newer refresh's items.
 *
 * The owning ViewModel is a thin adapter exposing [items]/[isLoading]/[error]
 * and aliasing [refresh] under its own name.
 */
class SimpleListCollection<T>(
    private val scope: CoroutineScope,
    private val load: suspend (force: Boolean) -> Result<List<T>>,
) {

    private val itemsFlow = MutableStateFlow<List<T>>(emptyList())

    /** Currently loaded items — empty until the first successful load. */
    val items: StateFlow<List<T>> = itemsFlow.asStateFlow()

    private val isLoadingFlow = MutableStateFlow(true)

    /** True while a load/refresh is in flight (drives the ladder + pull-to-refresh). */
    val isLoading: StateFlow<Boolean> = isLoadingFlow.asStateFlow()

    private val errorFlow = MutableStateFlow<Throwable?>(null)

    /** Last load failure, or null when the last load succeeded. */
    val error: StateFlow<Throwable?> = errorFlow.asStateFlow()

    /** Monotonic refresh id — only the newest refresh may write state back. */
    private var refreshGeneration = 0

    init {
        refresh(force = false)
    }

    /**
     * Reloads the list. The initial load runs with `force = false` (cache-honouring);
     * explicit calls — pull-to-refresh, error retry — default to forced reads.
     */
    fun refresh(force: Boolean = true) {
        val generation = ++refreshGeneration
        scope.launch {
            isLoadingFlow.value = true
            errorFlow.value = null
            load(force)
                .onSuccess {
                    if (generation == refreshGeneration) {
                        itemsFlow.value = it
                        errorFlow.value = null
                    }
                }
                .onFailure {
                    if (generation == refreshGeneration) errorFlow.value = it
                }
            if (generation == refreshGeneration) {
                isLoadingFlow.value = false
            }
        }
    }
}
