package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.model.MetadataRefreshOption
import com.raulshma.jellyplay.core.model.toRefreshParams
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_refresh_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_refresh_started
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the admin metadata actions on the detail screen (the ⋮ menu's
 * "Refresh metadata"). A plain helper constructed by the VM via [Factory],
 * after the [WatchPartyActions] template: user-facing messages push through
 * the shared [messages] channel so the helper owns no channel of its own.
 *
 * The server enforces admin rights on the refresh endpoint (403 for
 * non-admins), so the entry-point gate is [isAdmin] resolved off
 * [AuthRepository.currentUser] — the same seam the metadata editor uses —
 * rather than a client-side capability.
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
     * flow — the VM turns it into a StateFlow on its own scope (the
     * `canManageSeries` pattern); keeping the sharing on the helper's scope
     * would pin a permanently-active stateIn child to it.
     */
    val isAdmin: Flow<Boolean> = authRepository.currentUser
        .map { it?.isAdmin == true }
        .distinctUntilChanged()

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
}
