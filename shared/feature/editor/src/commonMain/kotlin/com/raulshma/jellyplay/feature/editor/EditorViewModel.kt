package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderIds
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.model.subtitle.externalSubtitleIndices
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Base64

@Immutable
data class EditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val mediaDetail: MediaDetail? = null,
    val editorInfo: MetadataEditorInfo? = null,
    val imageInfos: List<ImageInfo> = emptyList(),
    val imageProviders: List<ImageProviderInfo> = emptyList(),
    val remoteImages: RemoteImageResult? = null,
    val remoteSubtitleResults: List<RemoteSubtitleInfo> = emptyList(),
    /** Merged cross-provider subtitle search results (Jellyfin + external). */
    val providerSubtitleResults: List<SubtitleSearchResult> = emptyList(),
    /** Per-provider search failure messages for chips. */
    val providerSubtitleErrors: Map<SubtitleProviderKind, String> = emptyMap(),
    /** Providers the user has configured (drives chip visibility). */
    val configuredSubtitleProviders: Set<SubtitleProviderKind> = emptySet(),
    val isSearchingProviderSubtitles: Boolean = false,
    val isDownloadingProviderSubtitle: Boolean = false,
    val error: String? = null,
    val isAdmin: Boolean = false,
    /** The metadata-editing session (live form + loaded original) — the ~30
     *  flat metadata fields collapsed into one value slice, so the load/save
     *  maps and the dirty check have a single field declaration to follow. */
    val metadata: MetadataEditSession = MetadataEditSession(),
) {
    /** Derived from the embedded session — no stored dirty flag (or dirty
     *  hash) to keep in sync with the form fields. */
    val isDirty: Boolean get() = metadata.isDirty
}

class EditorViewModel(
    private val editorRepository: MetadataEditorRepository,
    authRepository: AuthRepository,
    private val subtitleProviderRepository: SubtitleProviderRepository,
    private val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.flow

    private val isAdminFlow: StateFlow<com.raulshma.jellyplay.core.model.UserInfo?> = authRepository.currentUser
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        launch {
            isAdminFlow.collect { user ->
                _uiState.update { it.copy(isAdmin = user?.isAdmin == true) }
            }
        }
    }

    fun loadEditorData(itemId: String) {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val isAdmin = isAdminFlow.value?.isAdmin == true
                val detail: MediaDetail
                val editorInfo: MetadataEditorInfo?
                val imageInfos: List<ImageInfo>
                val providers: List<ImageProviderInfo>
                coroutineScope {
                    val detailDeferred = async { editorRepository.getMediaDetail(itemId) }
                    // Editor metadata / image providers are admin-only endpoints.
                    // Skip them for non-admins instead of firing guaranteed-to-fail
                    // 403s (the server still enforces; this is defense-in-depth).
                    val editorInfoDeferred = async {
                        if (isAdmin) editorRepository.getMetadataEditorInfo(itemId).getOrNull() else null
                    }
                    val imageInfoDeferred = async {
                        if (isAdmin) editorRepository.getItemImageInfo(itemId).getOrNull() else null
                    }
                    val providersDeferred = async {
                        if (isAdmin) editorRepository.getRemoteImageProviders(itemId).getOrNull() else null
                    }

                    detail = detailDeferred.await().getOrThrow()
                    editorInfo = editorInfoDeferred.await()
                    imageInfos = imageInfoDeferred.await() ?: emptyList()
                    providers = providersDeferred.await() ?: emptyList()
                }

                val form = EditableItemMetadataForm.fromDetail(detail)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        mediaDetail = detail,
                        editorInfo = editorInfo,
                        imageInfos = imageInfos,
                        imageProviders = providers,
                        metadata = state.metadata.loaded(form),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Edits the metadata form (typed — the lambda sees only the form, never
     *  the whole UiState; [EditorUiState.isDirty] derives from the session). */
    fun updateField(update: (EditableItemMetadataForm) -> EditableItemMetadataForm) {
        _uiState.update { state -> state.copy(metadata = state.metadata.edit(update)) }
    }

    /** Clears the transient [EditorUiState.error] shown by the screen's error banner. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveMetadata() {
        launch {
            // Clear any stale error from a prior failed save so the red banner
            // doesn't linger while this retry is in flight.
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val state = _uiState.value
                val itemId = state.mediaDetail?.item?.id ?: return@launch

                editorRepository.updateItem(
                    itemId = itemId,
                    metadata = state.metadata.value.toEditable(state.mediaDetail?.dateCreated),
                ).getOrThrow()

                _uiState.update { it.copy(isSaving = false, metadata = it.metadata.saved()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun uploadImage(imageBytes: ByteArray, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.setItemImage(itemId, imageType, imageBytes)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Reads the bytes of a [file] (picked via the platform file-picker seam)
     * and uploads them. The background-thread read moved into the platform
     * actuals of [EditorPickedFile.readBytes] (contentResolver on Android,
     * java.io.File on desktop); failures surface through
     * [EditorUiState.error] exactly as the legacy contentResolver idiom did.
     */
    fun uploadImageFromFile(file: EditorPickedFile, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            runCatching { file.readBytes() }
                .onSuccess { bytes -> uploadImage(bytes, imageType) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun uploadImageFromUrl(url: String, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.downloadRemoteImage(itemId, imageType, url)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteImage(imageType: String, imageIndex: Int? = null) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.deleteItemImage(itemId, imageType, imageIndex)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun loadRemoteImages(imageType: String? = null, provider: String? = null, startIndex: Int? = null) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.getRemoteImages(itemId, imageType, provider, startIndex, 50)
                .onSuccess { result -> _uiState.update { it.copy(remoteImages = result) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun uploadSubtitle(fileBytes: ByteArray, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            val base64Data = Base64.getEncoder().encodeToString(fileBytes)
            editorRepository.uploadSubtitle(itemId, base64Data, fileName, language, isForced, isHearingImpaired)
                .onSuccess { loadEditorData(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Reads the bytes of a [file] (picked via the platform file-picker seam)
     * and uploads them. Falls back to the file name derived by the picker when
     * [fileName] is blank. The background-thread read moved into the platform
     * actuals of [EditorPickedFile.readBytes] (contentResolver on Android,
     * java.io.File on desktop); failures surface through
     * [EditorUiState.error] exactly as the legacy contentResolver idiom did.
     */
    fun uploadSubtitleFromFile(
        file: EditorPickedFile,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            runCatching { file.readBytes() }
                .onSuccess { bytes -> uploadSubtitle(bytes, fileName, language, isForced, isHearingImpaired) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteSubtitle(index: Int) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            // Capture the stream being deleted so legacy local copies (saved
            // before serverStreamIndex linkage existed) can be attribute-matched.
            val deletedStream = currentSubtitleStreams().firstOrNull { it.index == index }
            editorRepository.deleteSubtitle(itemId, index)
                .onSuccess {
                    streamingSubtitleStore.purgeDeletedServerStreamCopies(itemId, index, deletedStream)
                    loadEditorData(itemId)
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    private fun currentSubtitleStreams(): List<MediaStream> =
        _uiState.value.mediaDetail?.mediaSources
            ?.firstOrNull()?.mediaStreams
            ?.filter { it.type == StreamType.SUBTITLE }
            ?: emptyList()

    fun searchRemoteSubtitles(language: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.searchRemoteSubtitles(itemId, language)
                .onSuccess { results -> _uiState.update { it.copy(remoteSubtitleResults = results) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun downloadRemoteSubtitle(subtitleId: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.downloadRemoteSubtitle(itemId, subtitleId)
                .onSuccess { loadEditorData(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // region Multi-provider subtitle search (Jellyfin + Wyzie + OpenSubtitles) ---
    // Editor downloads from external providers are uploaded to the Jellyfin server
    // (via uploadSubtitle) so they persist as media streams for all clients —
    // matching the editor's metadata-management semantics.

    /** Loads the user's configured subtitle providers into UiState (chip visibility). */
    fun loadConfiguredSubtitleProviders() {
        launch {
            val configured = subtitleProviderRepository.configuredProviders().first()
            _uiState.update { it.copy(configuredSubtitleProviders = configured) }
        }
    }

    /**
     * Concurrent cross-provider subtitle search. Jellyfin + external-provider
     * results are merged centrally in the repository; per-provider errors
     * surface as chips.
     */
    fun searchAllSubtitleProviders(language: String) {
        val detail = _uiState.value.mediaDetail ?: return
        val itemId = detail.item.id
        launch {
            _uiState.update {
                it.copy(
                    isSearchingProviderSubtitles = true,
                    providerSubtitleResults = emptyList(),
                    providerSubtitleErrors = emptyMap(),
                )
            }
            val query = SubtitleProviderIds.buildQuery(detail).copy(languages = listOf(language))
            // Streaming: each provider's results/errors land in state the instant
            // it resolves, so a slow/retrying provider can no longer gate its
            // siblings.
            val merged = subtitleProviderRepository.searchAllStreaming(query, itemId, language) { partial ->
                _uiState.update {
                    it.copy(
                        providerSubtitleResults = partial.results,
                        providerSubtitleErrors = partial.errors,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isSearchingProviderSubtitles = false,
                    providerSubtitleResults = merged.results,
                    providerSubtitleErrors = merged.errors,
                )
            }
        }
    }

    /**
     * Downloads an external-provider subtitle and uploads it to the Jellyfin
     * server so it persists as a media stream. Jellyfin rows route through the
     * existing [downloadRemoteSubtitle] server-side path.
     */
    fun downloadProviderSubtitle(result: SubtitleSearchResult) {
        val itemId = _uiState.value.mediaDetail?.item?.id ?: return
        when (result.provider) {
            SubtitleProviderKind.JELLYFIN -> {
                result.jellyfinInfo?.let { downloadRemoteSubtitle(it.id) }
            }
            else -> launch {
                _uiState.update { it.copy(isDownloadingProviderSubtitle = true) }
                subtitleProviderRepository.downloadExternal(result)
                    .onSuccess { file ->
                        // Persist durably on-device first so the subtitle survives
                        // even if the server upload fails (e.g. offline). Mirrors
                        // the player's SubtitleManager provider-download path.
                        val codec = file.format
                        val saved = streamingSubtitleStore.save(
                            itemId = itemId,
                            provider = result.provider,
                            providerSubtitleId = result.id,
                            fileName = file.fileName,
                            language = file.language ?: result.language,
                            codec = codec,
                            isForced = result.isForced,
                            isHearingImpaired = result.isHearingImpaired,
                            bytes = file.bytes,
                        )
                        val base64 = Base64.getEncoder().encodeToString(file.bytes)
                        val preUploadExternalIndices = currentSubtitleStreams().externalSubtitleIndices()
                        editorRepository.uploadSubtitle(
                            itemId,
                            base64,
                            file.fileName,
                            file.language,
                            result.isForced,
                            result.isHearingImpaired,
                        ).onSuccess {
                            loadEditorData(itemId)
                            streamingSubtitleStore.attributeUploadedSubtitle(
                                itemId = itemId,
                                saved = saved,
                                streamsAfterUpload = currentSubtitleStreams(),
                                preUploadExternalIndices = preUploadExternalIndices,
                            )
                        }
                            // Best-effort: the durable on-device copy already backs
                            // this subtitle, so an upload failure (server offline)
                            // is surfaced as an info note rather than a hard error.
                            .onFailure { e ->
                                _uiState.update {
                                    it.copy(error = "Saved to device only: ${e.message}")
                                }
                            }
                    }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                _uiState.update { it.copy(isDownloadingProviderSubtitle = false) }
            }
        }
    }

    // endregion

    fun refreshMetadata(
        mode: String = "FullRefresh",
        replaceAllMetadata: Boolean = false,
        replaceAllImages: Boolean = false,
    ) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.refreshItemMetadata(itemId, mode, mode, replaceAllMetadata, replaceAllImages)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun getImageUrl(itemId: String, imageInfo: com.raulshma.jellyplay.core.model.ImageInfo): String {
        return editorRepository.getItemImageUrl(
            itemId,
            imageInfo.imageType,
            400,
            imageInfo.imageIndex,
            imageInfo.imageTag,
        )
    }

    fun getFullImageUrl(itemId: String, imageInfo: com.raulshma.jellyplay.core.model.ImageInfo): String {
        return editorRepository.getItemImageUrl(
            itemId,
            imageInfo.imageType,
            null,
            imageInfo.imageIndex,
            imageInfo.imageTag,
        )
    }

    private suspend fun reloadImageInfos(itemId: String) {
        editorRepository.getItemImageInfo(itemId)
            .onSuccess { infos -> _uiState.update { it.copy(imageInfos = infos) } }
    }
}
