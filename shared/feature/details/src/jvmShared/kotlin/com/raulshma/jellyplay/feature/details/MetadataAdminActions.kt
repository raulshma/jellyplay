package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.model.IdentifyItemType
import com.raulshma.jellyplay.core.model.IdentifyQuery
import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataRefreshOption
import com.raulshma.jellyplay.core.model.toRefreshParams
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_identify_applied
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_identify_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_refresh_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_refresh_started
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the admin metadata-maintenance concerns for the detail screen (the ⋮
 * menu's "Refresh metadata" + "Identify" entries). One helper for both: they
 * share the collaborator set (editor repository, auth stream, session,
 * message channel) and the ownership ratchet keeps the VM at one seam for
 * the feature.
 *
 * A plain helper constructed by the VM via [Factory], after the
 * [WatchPartyActions] template — user-facing messages push through the shared
 * [messages] channel so the helper owns no channel of its own.
 *
 * The server enforces admin rights on these endpoints (403 for non-admins),
 * so the entry-point gate is [isAdmin] resolved off [AuthRepository.currentUser]
 * — the same seam the metadata editor uses — rather than a client capability.
 */
internal class MetadataAdminActions(
    private val scope: CoroutineScope,
    private val session: StateFlow<DetailSession?>,
    private val messages: MutableSharedFlow<DetailMessage>,
    private val strings: DetailStrings,
    private val editorRepository: MetadataEditorRepository,
    authRepository: AuthRepository,
) {
    /**
     * Bundles this helper's exclusive collaborators (the metadata-editor
     * repository + the auth stream) so they never appear in the
     * [DetailViewModel] constructor.
     */
    class Factory constructor(
        private val editorRepository: MetadataEditorRepository,
        private val authRepository: AuthRepository,
    ) {
        fun create(
            scope: CoroutineScope,
            session: StateFlow<DetailSession?>,
            messages: MutableSharedFlow<DetailMessage>,
            strings: DetailStrings,
        ): MetadataAdminActions = MetadataAdminActions(
            scope = scope,
            session = session,
            messages = messages,
            strings = strings,
            editorRepository = editorRepository,
            authRepository = authRepository,
        )
    }

    /**
     * Whether the signed-in user may run server-side metadata actions. A cold
     * flow — the VM folds it into the [DetailUiState] gate field in its init
     * collector; keeping a stateIn on the helper's scope would pin a
     * permanently-active sharing child to it.
     */
    val isAdmin: Flow<Boolean> = authRepository.currentUser
        .map { it?.isAdmin == true }
        .distinctUntilChanged()

    // ── Refresh metadata ─────────────────────────────────────────────────

    /**
     * Refreshes the session's current item with [option] on the server.
     * Fire-and-forget from the UI's standpoint — success/failure flow back as
     * one-shot messages. Refreshing a SERIES cascades to its episodes
     * server-side, which is the per-series bulk path.
     */
    fun refreshScreenItem(option: MetadataRefreshOption) {
        val itemId = session.value?.detail?.item?.id ?: return
        scope.launch {
            val params = option.toRefreshParams()
            editorRepository.refreshItemMetadata(
                itemId = itemId,
                metadataRefreshMode = params.metadataRefreshMode,
                imageRefreshMode = params.imageRefreshMode,
                replaceAllMetadata = params.replaceAllMetadata,
                replaceAllImages = params.replaceAllImages,
            ).onSuccess {
                messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_refresh_started)))
            }.onFailure {
                messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_refresh_failed)))
            }
        }
    }

    // ── Identify (provider re-match, jellyfin-web parity) ────────────────

    private val _identifyState = MutableStateFlow(IdentifyUiState())

    /** Sheet-visible Identify state. `query == null` means the sheet is closed. */
    val identifyState: StateFlow<IdentifyUiState> = _identifyState.asStateFlow()

    /**
     * Opens the Identify sheet prefilled from the session's loaded item: name,
     * year, and the current provider ids (lowercase keys). Only Series and
     * Movie items can be identified in this version — other types no-op.
     */
    fun openIdentifyScreenItem() {
        val detail = session.value?.detail ?: return
        val item = detail.item
        val itemType = when (item.mediaType) {
            MediaType.SERIES -> IdentifyItemType.SERIES
            MediaType.MOVIE -> IdentifyItemType.MOVIE
            else -> return
        }
        _identifyState.value = IdentifyUiState(
            query = IdentifyQuery(
                itemId = item.id,
                itemType = itemType,
                name = item.name.orEmpty(),
                year = item.year,
                providerIds = detail.providerIds,
            ),
        )
    }

    /** Closes the Identify sheet and drops the search/apply ephemera. */
    fun dismissIdentify() {
        _identifyState.value = IdentifyUiState()
    }

    /** Applies an edit to the Identify prefill (name / year / provider ids). */
    fun updateIdentifyQuery(update: (IdentifyQuery) -> IdentifyQuery) {
        _identifyState.update { current ->
            current.query?.let { current.copy(query = update(it)) } ?: current
        }
    }

    /** Runs the provider search with the current prefill. */
    fun searchIdentify() {
        val query = _identifyState.value.query ?: return
        if (_identifyState.value.isSearching) return
        scope.launch {
            _identifyState.update { it.copy(isSearching = true) }
            editorRepository.identifyRemoteSearch(query)
                .onSuccess { results ->
                    _identifyState.update { it.copy(isSearching = false, results = results, hasSearched = true) }
                }
                .onFailure {
                    _identifyState.update { it.copy(isSearching = false, hasSearched = true) }
                    messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_identify_failed)))
                }
        }
    }

    /**
     * Applies [result] onto the item. On success the sheet closes, a
     * confirmation message is emitted and [IdentifyUiState.appliedCount]
     * increments so the screen reloads the item's (now server-refreshed)
     * metadata.
     */
    fun applyIdentify(result: IdentifyResult, replaceAllImages: Boolean) {
        val query = _identifyState.value.query ?: return
        if (_identifyState.value.isApplying) return
        scope.launch {
            _identifyState.update { it.copy(isApplying = true) }
            editorRepository.applyIdentifyResult(query.itemId, result, replaceAllImages)
                .onSuccess {
                    messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_identify_applied)))
                    _identifyState.update { it.copy(isApplying = false) }
                    dismissIdentify()
                    _identifyState.update { it.copy(appliedCount = it.appliedCount + 1) }
                }
                .onFailure {
                    _identifyState.update { it.copy(isApplying = false) }
                    messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_identify_failed)))
                }
        }
    }
}

/** Identify-sheet visible state. `query == null` means the sheet is closed. */
@Immutable
internal data class IdentifyUiState(
    val query: IdentifyQuery? = null,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<IdentifyResult> = emptyList(),
    val isApplying: Boolean = false,
    /** Monotonic counter of successful applies; the screen reloads on each bump. */
    val appliedCount: Int = 0,
)
