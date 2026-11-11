package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.MetadataRefreshOption
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.toRefreshParams
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderIds
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The editor screen's single state snapshot. MIGRATED (v0.11.10) from the
 * repo's worst boolean soup — `isLoading` / `isSaving` /
 * `isSearchingProviderSubtitles` / `isDownloadingProviderSubtitle` plus ONE
 * shared `error: String?` carrying load, save, image, subtitle, and refresh
 * failures alike — to the StateManagementConventions §1 sealed-phase shape:
 *
 *  - [loadPhase] owns the load lifecycle AND the load failure (ex-`isLoading`
 *    + the load half of `error`);
 *  - [savePhase] owns the save-in-flight dimension (ex-`isSaving`);
 *  - [providerSubtitleSearchPhase] / [providerSubtitleDownloadPhase] own the
 *    two provider-subtitle op machines (ex-`isSearchingProviderSubtitles` /
 *    `isDownloadingProviderSubtitle` — kept as TWO machines because the ops
 *    genuinely overlap, see [ProviderSubtitleSearchPhase]);
 *  - [actionError] is the one banner channel for every ACTION failure
 *    (save/image/subtitle/refresh — ex- the action half of `error`), with the
 *    file-level `bannerError` extension below the banner's precedence fold;
 *  - the content fields ([mediaDetail], [editorInfo], [remoteImages], …) stay
 *    plain nullable/list fields for this pass — folding them into phase
 *    payloads is the deferred catalog-row work, not part of the de-souping.
 */
@Immutable
data class EditorUiState(
    val loadPhase: EditorLoadPhase = EditorLoadPhase.Loading,
    val savePhase: EditorSavePhase = EditorSavePhase.Idle,
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
    val providerSubtitleSearchPhase: ProviderSubtitleSearchPhase = ProviderSubtitleSearchPhase.Idle,
    val providerSubtitleDownloadPhase: ProviderSubtitleDownloadPhase = ProviderSubtitleDownloadPhase.Idle,
    /** The banner channel for ACTION failures (save / image / subtitle /
     *  refresh / provider-download degradation note). Load failures do NOT
     *  land here — they are [EditorLoadPhase.Failed]. One carrier, not one
     *  per action concern, because the screen renders every failure in ONE
     *  banner with last-write-wins semantics (see the bannerError fold
     *  below). */
    val actionError: String? = null,
    val isAdmin: Boolean = false,
    /** The metadata-editing session (live form + loaded original) — the ~30
     *  flat metadata fields live in one value slice, so the load/save
     *  maps and the dirty check have a single field declaration to follow. */
    val metadata: MetadataEditSession = MetadataEditSession(),
) {
    /** Derived from the embedded session — no stored dirty flag (or dirty
     *  hash) to keep in sync with the form fields. */
    val isDirty: Boolean get() = metadata.isDirty
}

/**
 * The message the screen's ONE error banner renders (EditorScreen renders it
 * via `ErrorBanner`, dismissible via `EditorUiEvent.ClearError`).
 *
 * Precedence: [EditorUiState.actionError] FIRST, a load [EditorLoadPhase.Failed]
 * second. This fold preserves the former single-`error` field's
 * last-write-wins behavior in every reachable combination:
 *
 *  - load fails, then an action fails → the action write landed last, and
 *    this fold shows the action message (the old field would have been
 *    overwritten the same way);
 *  - an action fails, then a load runs → `loadEditorData`'s start arm clears
 *    [EditorUiState.actionError] (the old `start` cleared `error` just the
 *    same), so the banner shows only the load failure;
 *  - the reverse ordering (action error surviving into a load failure) is
 *    unreachable for the same reason — no reader ever saw it either.
 *
 * Null when nothing should render; [EditorLoadPhase.Failed.message] can
 * itself be null (a message-less Throwable used to publish `error = null`
 * and the banner skipped it), so a null here always means no banner.
 *
 * A file-level extension rather than a UiState member: it adds no STATE, only
 * the banner's precedence decision over the two existing error carriers, and
 * the EditorViewModelOwnershipTest ratchet counts declarations sharing the
 * ViewModel's file at member indentation (isDirty is grandfathered; this fold
 * refuses to spend that budget — the DetailUiState.kt top-level derived-folds
 * precedent: pickBackdropTrailerEntry).
 */
internal val EditorUiState.bannerError: String? get() =
    actionError ?: (loadPhase as? EditorLoadPhase.Failed)?.message

/**
 * Sealed load phase for the editor's ONE load ladder (`loadEditorData`,
 * riding the core/ui `loadInto` choreography) — the migration that collapsed
 * the former `isLoading: Boolean` plus the load half of the shared
 * `error: String?` into one mutually-exclusive state (the
 * StateManagementConventions §1 shape, following the `DetailUiLoadState`
 * exemplar).
 *
 * The old boolean shape could represent states the screen never meant:
 * `isLoading && isSaving` (a save spinner under the load veil),
 * `error != null && isLoading` (a stale banner over a fresh spinner), and a
 * stale image grid drawn over an in-flight load. Those are unrepresentable
 * now — a phase is one value at a time. The concerns that genuinely run
 * CONCURRENTLY with the ladder (saves while Ready; a provider-subtitle
 * download's spinner across a reload's Loading veil) stay separate phase
 * fields precisely so those overlaps remain representable — they are today's
 * real behavior, not soup.
 */
sealed interface EditorLoadPhase {

    /** A load is in flight. Both the cold-load state (the state's initial
     *  value, ex-`isLoading = true`) AND the reload state: the screen never
     *  distinguished the two — all three tabs veil their content behind the
     *  same spinner whenever the old flag was up, including on the
     *  subtitle-op / refresh-triggered reloads of already-shown content — so
     *  a single arm is the exact shape. A `Refreshing` arm would be a state
     *  the old booleans could not express and no reader consumed. */
    data object Loading : EditorLoadPhase

    /** A load succeeded. Content fields stay the single authority for WHAT
     *  renders; Ready only says the ladder settled green. This is also where
     *  a DISMISSED failure lands (`clearError` maps Failed here — the
     *  dismissed banner must stop rendering, and the old dismissal left
     *  `isLoading = false` with whatever content fields survived, which is
     *  exactly Ready-with-possibly-null-content), matching the
     *  DetailUiLoadState.Loaded precedent of a load state only surfaced
     *  while it has something to say. */
    data object Ready : EditorLoadPhase

    /** The most recent load failed; content fields are untouched (the old
     *  failure arm never cleared them — a reload failure keeps the stale
     *  content visible). [message] is the exception's `message` VERBATIM and
     *  deliberately NULLABLE: the former `error = e.message` write could
     *  publish null for a message-less Throwable, and the banner skips
     *  nulls — that failure rendered NO banner, which `Failed("")` would
     *  have silently changed. */
    data class Failed(val message: String?) : EditorLoadPhase
}

/**
 * Sealed save phase for `saveMetadata` (ex-`isSaving: Boolean`). Only the
 * in-flight dimension is modeled: a save FAILURE lands in
 * [EditorUiState.actionError], not in a Failed arm here — the screen renders
 * every action failure in the one shared banner (see
 * [EditorUiState.bannerError]), so a phase arm would fork the "where does an
 * error surface" answer per concern without changing a single rendered pixel.
 *
 * KNOWN BUG PRESERVED: the no-item early return in `saveMetadata` happens
 * AFTER [Saving] is raised and nothing resets it, so the phase sticks — the
 * old `isSaving` pin (EditorViewModelImageAndSaveGapsTest) documents this as
 * a suspected bug and asserts the stick; the migration preserves the stick
 * verbatim. Flipping that test is part of FIXING the bug, not of migrating.
 */
sealed interface EditorSavePhase {
    data object Idle : EditorSavePhase
    data object Saving : EditorSavePhase
}

/**
 * Sealed in-flight phase for the multi-provider subtitle SEARCH fan-out
 * (`handleSearchAllSubtitleProviders`, ex-`isSearchingProviderSubtitles`).
 *
 * Deliberately its OWN machine, not one merged `providerSubtitleOpPhase` with
 * the download: the two ops GENUINELY OVERLAP today. The search streams
 * partial rows live (ProviderResultsSection) and its per-row download buttons
 * stay ENABLED while the Searching spinner is up, so "downloading during an
 * active search" is a reachable state the old booleans both represented.
 * One merged phase would let the download's settle-to-Idle extinguish the
 * search spinner mid-fan-out — a visible regression — and a combined
 * `SearchingAndDownloading` arm would just re-derive two booleans at the
 * render site. Orthogonal machines, one phase field each; the overlap stays
 * representable as [Searching] and
 * [ProviderSubtitleDownloadPhase.Downloading] on their own fields, and the
 * EditorViewModelPhasePinsTest pins that combination.
 */
sealed interface ProviderSubtitleSearchPhase {
    data object Idle : ProviderSubtitleSearchPhase
    data object Searching : ProviderSubtitleSearchPhase
}

/**
 * Sealed in-flight phase for the external-provider subtitle DOWNLOAD
 * choreography (`handleDownloadProviderSubtitle`, ex-`isDownloadingProviderSubtitle`;
 * download → persist → upload → reload → attribute). Jellyfin-provider rows
 * route through the server-side download path and never touch this machine —
 * exactly as they never touched the old flag.
 *
 * Kept separate from [ProviderSubtitleSearchPhase] — see that type's KDoc for
 * why the merge was rejected (the ops genuinely overlap mid-search).
 */
sealed interface ProviderSubtitleDownloadPhase {
    data object Idle : ProviderSubtitleDownloadPhase
    data object Downloading : ProviderSubtitleDownloadPhase
}

internal class EditorViewModel(
    private val editorRepository: MetadataEditorRepository,
    authRepository: AuthRepository,
    private val subtitleProviderRepository: SubtitleProviderRepository,
    private val subtitleStore: EditorSubtitleStore,
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

    /**
     * The single command funnel (the HomeViewModel `onEvent` pattern):
     * every user intent arrives as an [EditorUiEvent] and is routed once
     * here — the tabs (Metadata/Images/Subtitles) dispatch the events
     * directly. The `handle*` arms are the private handlers behind the
     * file-picker / provider events; the remaining events route to the
     * private command funs (their names are unchanged for the
     * internal reload callers).
     */
    fun onEvent(event: EditorUiEvent) {
        when (event) {
            EditorUiEvent.ClearError -> clearError()
            is EditorUiEvent.LoadEditorData -> loadEditorData(event.itemId)
            EditorUiEvent.SaveMetadata -> saveMetadata()
            is EditorUiEvent.UpdateField -> updateField(event.update)
            is EditorUiEvent.UploadImage -> uploadImage(event.imageBytes, event.imageType)
            is EditorUiEvent.UploadImageFromFile -> handleUploadImageFromFile(event.file, event.imageType)
            is EditorUiEvent.UploadImageFromUrl -> handleUploadImageFromUrl(event.url, event.imageType)
            is EditorUiEvent.DeleteImage -> handleDeleteImage(event.imageType, event.imageIndex)
            is EditorUiEvent.LoadRemoteImages -> handleLoadRemoteImages(event.imageType, event.provider, event.startIndex)
            is EditorUiEvent.UploadSubtitle -> uploadSubtitle(
                event.fileBytes,
                event.fileName,
                event.language,
                event.isForced,
                event.isHearingImpaired,
            )
            is EditorUiEvent.UploadSubtitleFromFile -> handleUploadSubtitleFromFile(
                event.file,
                event.fileName,
                event.language,
                event.isForced,
                event.isHearingImpaired,
            )
            is EditorUiEvent.DeleteSubtitle -> handleDeleteSubtitle(event.index)
            is EditorUiEvent.SearchRemoteSubtitles -> handleSearchRemoteSubtitles(event.language)
            is EditorUiEvent.DownloadRemoteSubtitle -> handleDownloadRemoteSubtitle(event.subtitleId)
            EditorUiEvent.LoadConfiguredSubtitleProviders -> handleLoadConfiguredSubtitleProviders()
            is EditorUiEvent.SearchAllSubtitleProviders -> handleSearchAllSubtitleProviders(event.language)
            is EditorUiEvent.DownloadProviderSubtitle -> handleDownloadProviderSubtitle(event.result)
            is EditorUiEvent.RefreshMetadata -> refreshMetadata(event.option)
        }
    }

    private fun loadEditorData(itemId: String) {
        launch {
            // The editor's ONE load ladder (core/ui loadInto, ex-EditorLoad).
            // Only the editor-data load is a genuine single-fetch ladder: the
            // other fetches are declared non-ladders — `saveMetadata` raises
            // `savePhase` (a save, not a load, and its success arm re-baselines
            // the form session), `searchAllSubtitleProviders` streams partial
            // provider results instead of dispatching one Result, the
            // provider-subtitle download is a multi-leg choreography
            // (download → persist → upload → reload → attribute), and the
            // image/subtitle mutations use the bare `.onSuccess`/`.onFailure`
            // idiom with no loading guard at all — folding any of them
            // would change behaviour, not centralize it.
            //
            // Phase mapping (ex-`isLoading` + the load half of `error`): start
            // raises [EditorLoadPhase.Loading] and clears the ACTION error (the
            // old `error = null` — a fresh load dismisses any lingering action
            // banner), the success arm settles [EditorLoadPhase.Ready], and
            // the failure arm settles [EditorLoadPhase.Failed] with the
            // exception's message verbatim (nullable — see the arm's KDoc).
            loadInto(
                start = {
                    _uiState.update {
                        it.copy(loadPhase = EditorLoadPhase.Loading, actionError = null)
                    }
                },
                fetch = { fetchEditorData(itemId) },
                onSuccess = { loaded ->
                    _uiState.update { state ->
                        state.copy(
                            loadPhase = EditorLoadPhase.Ready,
                            mediaDetail = loaded.detail,
                            editorInfo = loaded.editorInfo,
                            imageInfos = loaded.imageInfos,
                            imageProviders = loaded.imageProviders,
                            metadata = state.metadata.loaded(loaded.form),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(loadPhase = EditorLoadPhase.Failed(e.message)) }
                },
            )
        }
    }

    /**
     * The load ladder's one fetch: the detail (required) plus the three
     * admin-gated side fetches, resolved concurrently into a single
     * [Result]. Catches [Exception] — not [Throwable] — so a fatal `Error`
     * still propagates; [CancellationException] rethrows so a cancelled
     * load reports as cancelled, not as a load failure.
     */
    private suspend fun fetchEditorData(itemId: String): Result<EditorLoadedData> = try {
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

        Result.success(
            EditorLoadedData(
                detail = detail,
                editorInfo = editorInfo,
                imageInfos = imageInfos,
                imageProviders = providers,
                form = EditableItemMetadataForm.fromDetail(detail),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Edits the metadata form (typed — the lambda sees only the form, never
     *  the whole UiState; [EditorUiState.isDirty] derives from the session). */
    private fun updateField(update: (EditableItemMetadataForm) -> EditableItemMetadataForm) {
        _uiState.update { state -> state.copy(metadata = state.metadata.edit(update)) }
    }

    /**
     * Dismisses the screen's ONE error banner: clears every source
     * [EditorUiState.bannerError] can render — the [EditorUiState.actionError]
     * channel AND a load [EditorLoadPhase.Failed] (which settles to
     * [EditorLoadPhase.Ready]; the old dismissal left `isLoading = false` with
     * the content fields untouched, which is exactly Ready-with-possibly-null
     * content). Nothing else is cleared: content, save phase, provider-op
     * phases, and the dirty session all survive a dismissal, as they survived
     * the old single-field clear.
     */
    private fun clearError() {
        _uiState.update { state ->
            state.copy(
                actionError = null,
                loadPhase = if (state.loadPhase is EditorLoadPhase.Failed) {
                    EditorLoadPhase.Ready
                } else {
                    state.loadPhase
                },
            )
        }
    }

    private fun saveMetadata() {
        launch {
            // Clear any stale action error from a prior failed save so the red
            // banner doesn't linger while this retry is in flight.
            _uiState.update { it.copy(savePhase = EditorSavePhase.Saving, actionError = null) }
            try {
                val state = _uiState.value
                val itemId = state.mediaDetail?.item?.id ?: return@launch

                editorRepository.updateItem(
                    itemId = itemId,
                    metadata = state.metadata.value.toEditable(state.mediaDetail?.dateCreated),
                ).getOrThrow()

                _uiState.update {
                    it.copy(savePhase = EditorSavePhase.Idle, metadata = it.metadata.saved())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(savePhase = EditorSavePhase.Idle, actionError = e.message)
                }
            }
        }
    }

    private fun uploadImage(imageBytes: ByteArray, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.setItemImage(itemId, imageType, imageBytes)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    /**
     * Reads the bytes of a [file] (picked via the platform file-picker seam)
     * and uploads them. The background-thread read lives in the platform
     * actuals of [EditorPickedFile.readBytes] (contentResolver on Android,
     * java.io.File on desktop); failures surface through
     * [EditorUiState.actionError].
     */
    private fun handleUploadImageFromFile(file: EditorPickedFile, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            runCatchingRethrowingCancellation { file.readBytes() }
                .onSuccess { bytes -> uploadImage(bytes, imageType) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun handleUploadImageFromUrl(url: String, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.downloadRemoteImage(itemId, imageType, url)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun handleDeleteImage(imageType: String, imageIndex: Int?) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.deleteItemImage(itemId, imageType, imageIndex)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun handleLoadRemoteImages(imageType: String?, provider: String?, startIndex: Int?) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.getRemoteImages(itemId, imageType, provider, startIndex, 50)
                .onSuccess { result -> _uiState.update { it.copy(remoteImages = result) } }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun uploadSubtitle(fileBytes: ByteArray, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            val base64Data = Base64.Default.encode(fileBytes)
            editorRepository.uploadSubtitle(itemId, base64Data, fileName, language, isForced, isHearingImpaired)
                .onSuccess { loadEditorData(itemId) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    /**
     * Reads the bytes of a [file] (picked via the platform file-picker seam)
     * and uploads them. Falls back to the file name derived by the picker when
     * [fileName] is blank. The background-thread read lives in the platform
     * actuals of [EditorPickedFile.readBytes] (contentResolver on Android,
     * java.io.File on desktop); failures surface through
     * [EditorUiState.actionError].
     */
    private fun handleUploadSubtitleFromFile(
        file: EditorPickedFile,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            runCatchingRethrowingCancellation { file.readBytes() }
                .onSuccess { bytes -> uploadSubtitle(bytes, fileName, language, isForced, isHearingImpaired) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun handleDeleteSubtitle(index: Int) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            // Capture the stream being deleted so local copies saved before
            // serverStreamIndex linkage existed can be attribute-matched.
            val deletedStream = currentSubtitleStreams().firstOrNull { it.index == index }
            editorRepository.deleteSubtitle(itemId, index)
                .onSuccess {
                    subtitleStore.purgeDeletedServerStreamCopies(itemId, index, deletedStream)
                    loadEditorData(itemId)
                }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun currentSubtitleStreams(): List<MediaStream> =
        _uiState.value.mediaDetail?.mediaSources
            ?.firstOrNull()?.mediaStreams
            ?.filter { it.type == StreamType.SUBTITLE }
            ?: emptyList()

    private fun handleSearchRemoteSubtitles(language: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.searchRemoteSubtitles(itemId, language)
                .onSuccess { results -> _uiState.update { it.copy(remoteSubtitleResults = results) } }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    private fun handleDownloadRemoteSubtitle(subtitleId: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.downloadRemoteSubtitle(itemId, subtitleId)
                .onSuccess { loadEditorData(itemId) }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
        }
    }

    // region Multi-provider subtitle search (Jellyfin + Wyzie + OpenSubtitles) ---
    // Editor downloads from external providers are uploaded to the Jellyfin server
    // (via uploadSubtitle) so they persist as media streams for all clients —
    // matching the editor's metadata-management semantics.

    /** Loads the user's configured subtitle providers into UiState (chip visibility). */
    private fun handleLoadConfiguredSubtitleProviders() {
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
    private fun handleSearchAllSubtitleProviders(language: String) {
        val detail = _uiState.value.mediaDetail ?: return
        val itemId = detail.item.id
        launch {
            _uiState.update {
                it.copy(
                    providerSubtitleSearchPhase = ProviderSubtitleSearchPhase.Searching,
                    providerSubtitleResults = emptyList(),
                    providerSubtitleErrors = emptyMap(),
                )
            }
            val query = SubtitleProviderIds.buildQuery(detail).copy(languages = listOf(language))
            // Streaming: each provider's results/errors land in state the instant
            // it resolves, so a slow/retrying provider does not gate its
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
                    providerSubtitleSearchPhase = ProviderSubtitleSearchPhase.Idle,
                    providerSubtitleResults = merged.results,
                    providerSubtitleErrors = merged.errors,
                )
            }
        }
    }

    /**
     * Downloads an external-provider subtitle and uploads it to the Jellyfin
     * server so it persists as a media stream. Jellyfin rows route through the
     * server-side [handleDownloadRemoteSubtitle] path; external rows ride the
     * store's [EditorSubtitleStore.saveUploadAndAttribute] choreography (the
     * save → encode → upload → reload → attribute sequencing). This side
     * keeps only the download phase and the
     * "Saved to device only" degradation wording.
     */
    private fun handleDownloadProviderSubtitle(result: SubtitleSearchResult) {
        val itemId = _uiState.value.mediaDetail?.item?.id ?: return
        when (result.provider) {
            SubtitleProviderKind.JELLYFIN -> {
                result.jellyfinInfo?.let { handleDownloadRemoteSubtitle(it.id) }
            }
            else -> launch {
                _uiState.update { it.copy(providerSubtitleDownloadPhase = ProviderSubtitleDownloadPhase.Downloading) }
                subtitleProviderRepository.downloadExternal(result)
                    .onSuccess { file ->
                        val saved = ProviderSubtitleSave(
                            itemId = itemId,
                            provider = result.provider,
                            providerSubtitleId = result.id,
                            fileName = file.fileName,
                            language = file.language ?: result.language,
                            codec = file.format,
                            isForced = result.isForced,
                            isHearingImpaired = result.isHearingImpaired,
                            bytes = file.bytes,
                        )
                        subtitleStore.saveUploadAndAttribute(
                            save = saved,
                            streams = ::currentSubtitleStreams,
                            upload = { base64 ->
                                editorRepository.uploadSubtitle(
                                    itemId,
                                    base64,
                                    file.fileName,
                                    file.language,
                                    result.isForced,
                                    result.isHearingImpaired,
                                )
                            },
                            onUploaded = { loadEditorData(itemId) },
                        )
                            // Best-effort: the durable on-device copy already backs
                            // this subtitle, so an upload failure (server offline)
                            // is surfaced as an info note rather than a hard error.
                            .onFailure { e ->
                                _uiState.update {
                                    it.copy(actionError = "Saved to device only: ${e.message}")
                                }
                            }
                    }
                    .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
                _uiState.update { it.copy(providerSubtitleDownloadPhase = ProviderSubtitleDownloadPhase.Idle) }
            }
        }
    }

    // endregion

    /**
     * The server-side refresh is async — the request is ACCEPTED (204), not
     * finished — so an immediate reload would read pre-refresh state. On
     * acceptance, schedule one delayed reload of the whole editor data
     * (metadata + image infos) once the server has had time to apply it; a
     * new refresh or the ViewModel being cleared cancels the pending one.
     */
    private var refreshReloadJob: Job? = null

    private fun refreshMetadata(option: MetadataRefreshOption) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            editorRepository.refreshItemMetadata(itemId, option.toRefreshParams())
                .onSuccess {
                    refreshReloadJob?.cancel()
                    refreshReloadJob = launch {
                        delay(MetadataEditorRepository.REFRESH_APPLY_DELAY_MS)
                        loadEditorData(itemId)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(actionError = e.message) } }
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

/** The single fetch's payload for the load ladder behind `loadEditorData`. */
private class EditorLoadedData(
    val detail: MediaDetail,
    val editorInfo: MetadataEditorInfo?,
    val imageInfos: List<ImageInfo>,
    val imageProviders: List<ImageProviderInfo>,
    val form: EditableItemMetadataForm,
)
