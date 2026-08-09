package com.raulshma.jellyplay.feature.editor

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderIds
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@Immutable
data class EditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
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

    val name: String = "",
    val originalTitle: String = "",
    val sortName: String = "",
    val overview: String = "",
    val tagline: String = "",
    val communityRating: String = "",
    val criticRating: String = "",
    val officialRating: String = "",
    val customRating: String = "",
    val productionYear: String = "",
    val premiereDate: String = "",
    val endDate: String = "",
    val runtimeMinutes: String = "",
    val indexNumber: String = "",
    val parentIndexNumber: String = "",
    val displayOrder: String = "",
    val status: String = "",
    val airTime: String = "",
    val airDays: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val people: List<EditorPerson> = emptyList(),
    val providerIds: Map<String, String> = emptyMap(),
    val taglines: List<String> = emptyList(),
    val productionLocations: List<String> = emptyList(),
    val lockData: Boolean = false,
    val lockedFields: List<String> = emptyList(),
    val preferredMetadataLanguage: String = "",
    val preferredMetadataCountryCode: String = "",
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
    authRepository: AuthRepository,
    private val subtitleProviderRepository: SubtitleProviderRepository,
    private val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
    @ApplicationContext private val context: Context,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.flow

    private val isAdminFlow: StateFlow<com.raulshma.jellyplay.core.model.UserInfo?> = authRepository.currentUser
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private var originalHash: Int = 0

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
                    val detailDeferred = async { apiClient.getMediaDetail(itemId) }
                    // Editor metadata / image providers are admin-only endpoints.
                    // Skip them for non-admins instead of firing guaranteed-to-fail
                    // 403s (the server still enforces; this is defense-in-depth).
                    val editorInfoDeferred = async {
                        if (isAdmin) apiClient.getMetadataEditorInfo(itemId).getOrNull() else null
                    }
                    val imageInfoDeferred = async {
                        if (isAdmin) apiClient.getItemImageInfo(itemId).getOrNull() else null
                    }
                    val providersDeferred = async {
                        if (isAdmin) apiClient.getRemoteImageProviders(itemId).getOrNull() else null
                    }

                    detail = detailDeferred.await().getOrThrow()
                    editorInfo = editorInfoDeferred.await()
                    imageInfos = imageInfoDeferred.await() ?: emptyList()
                    providers = providersDeferred.await() ?: emptyList()
                }

                val item = detail.item
                val runtimeMinutes = detail.item.runTimeTicks?.let { (it / 600_000_000).toString() } ?: ""

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        mediaDetail = detail,
                        editorInfo = editorInfo,
                        imageInfos = imageInfos,
                        imageProviders = providers,
                        name = item.name,
                        originalTitle = item.originalTitle ?: "",
                        sortName = detail.sortName ?: "",
                        overview = item.overview ?: "",
                        tagline = detail.taglines.firstOrNull() ?: "",
                        communityRating = item.communityRating?.toString() ?: "",
                        criticRating = detail.criticRating?.toString() ?: "",
                        officialRating = item.officialRating ?: "",
                        customRating = detail.customRating ?: "",
                        productionYear = item.year?.toString() ?: "",
                        premiereDate = item.premiereDate ?: "",
                        endDate = "",
                        runtimeMinutes = runtimeMinutes,
                        indexNumber = item.indexNumber?.toString() ?: "",
                        parentIndexNumber = item.seasonNumber?.toString() ?: "",
                        displayOrder = detail.displayOrder ?: "",
                        status = detail.status ?: "",
                        airTime = detail.airTime ?: "",
                        airDays = detail.airDays,
                        genres = item.genres,
                        tags = item.tags,
                        studios = item.studios,
                        people = detail.people.map { person ->
                            EditorPerson(
                                id = person.id,
                                name = person.name,
                                role = person.role,
                                type = person.type,
                                primaryImageTag = person.primaryImageTag,
                            )
                        },
                        providerIds = detail.providerIds,
                        taglines = detail.taglines,
                        productionLocations = detail.productionLocations,
                        lockData = detail.lockData,
                        lockedFields = detail.lockedFields,
                        preferredMetadataLanguage = detail.preferredMetadataLanguage ?: "",
                        preferredMetadataCountryCode = detail.preferredMetadataCountryCode ?: "",
                    )
                }
                originalHash = computeDirtyHash(_uiState.value)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateField(update: (EditorUiState) -> EditorUiState) {
        _uiState.update { state ->
            val newState = update(state)
            newState.copy(isDirty = computeDirtyHash(newState) != originalHash)
        }
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

                apiClient.updateItem(
                    itemId = itemId,
                    name = state.name,
                    originalTitle = state.originalTitle.ifBlank { null },
                    sortName = state.sortName.ifBlank { null },
                    overview = state.overview.ifBlank { null },
                    tagline = state.tagline.ifBlank { null },
                    genres = state.genres,
                    tags = state.tags,
                    studios = state.studios,
                    communityRating = state.communityRating.toFloatOrNull(),
                    criticRating = state.criticRating.toFloatOrNull(),
                    officialRating = state.officialRating.ifBlank { null },
                    customRating = state.customRating.ifBlank { null },
                    productionYear = state.productionYear.toIntOrNull(),
                    premiereDate = state.premiereDate.ifBlank { null },
                    endDate = state.endDate.ifBlank { null },
                    runtimeTicks = state.runtimeMinutes.toLongOrNull()?.let { it * 600_000_000 },
                    indexNumber = state.indexNumber.toIntOrNull(),
                    parentIndexNumber = state.parentIndexNumber.toIntOrNull(),
                    displayOrder = state.displayOrder.ifBlank { null },
                    status = state.status.ifBlank { null },
                    airDays = state.airDays,
                    airTime = state.airTime.ifBlank { null },
                    people = state.people,
                    providerIds = state.providerIds,
                    lockData = state.lockData,
                    lockedFields = state.lockedFields,
                    preferredMetadataLanguage = state.preferredMetadataLanguage.ifBlank { null },
                    preferredMetadataCountryCode = state.preferredMetadataCountryCode.ifBlank { null },
                    taglines = if (state.tagline.isNotBlank()) listOf(state.tagline) else emptyList(),
                    productionLocations = state.productionLocations,
                    dateCreated = state.mediaDetail?.dateCreated,
                ).getOrThrow()

                originalHash = computeDirtyHash(_uiState.value)
                _uiState.update { it.copy(isSaving = false, isDirty = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun uploadImage(imageBytes: ByteArray, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.setItemImage(itemId, imageType, imageBytes)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Reads image bytes from a [uri] (picked via the SAF picker) on a background thread
     * and uploads them. See [SettingsViewModel.importSettings] for the established
     * contentResolver idiom used elsewhere in the app.
     */
    fun uploadImageFromUri(uri: Uri, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Cannot open input stream for selected image")
                }
            }.onSuccess { bytes -> uploadImage(bytes, imageType) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun uploadImageFromUrl(url: String, imageType: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.downloadRemoteImage(itemId, imageType, url)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteImage(imageType: String, imageIndex: Int? = null) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.deleteItemImage(itemId, imageType, imageIndex)
                .onSuccess { reloadImageInfos(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun loadRemoteImages(imageType: String? = null, provider: String? = null, startIndex: Int? = null) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.getRemoteImages(itemId, imageType, provider, startIndex, 50)
                .onSuccess { result -> _uiState.update { it.copy(remoteImages = result) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun uploadSubtitle(fileBytes: ByteArray, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
            apiClient.uploadSubtitle(itemId, base64Data, fileName, language, isForced, isHearingImpaired)
                .onSuccess { loadEditorData(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Reads subtitle bytes from a [uri] (picked via the SAF picker) on a background thread
     * and uploads them. Falls back to the file name derived from the picker when [fileName]
     * is blank.
     */
    fun uploadSubtitleFromUri(
        uri: Uri,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Cannot open input stream for selected subtitle")
                }
            }.onSuccess { bytes -> uploadSubtitle(bytes, fileName, language, isForced, isHearingImpaired) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteSubtitle(index: Int) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.deleteSubtitle(itemId, index)
                .onSuccess { loadEditorData(itemId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun searchRemoteSubtitles(language: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.searchRemoteSubtitles(itemId, language)
                .onSuccess { results -> _uiState.update { it.copy(remoteSubtitleResults = results) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun downloadRemoteSubtitle(subtitleId: String) {
        launch {
            val itemId = _uiState.value.mediaDetail?.item?.id ?: return@launch
            apiClient.downloadRemoteSubtitle(itemId, subtitleId)
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
            val merged = subtitleProviderRepository.searchAll(query, itemId, language)
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
                        streamingSubtitleStore.save(
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
                        val base64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
                        apiClient.uploadSubtitle(
                            itemId,
                            base64,
                            file.fileName,
                            file.language,
                            result.isForced,
                            result.isHearingImpaired,
                        ).onSuccess { loadEditorData(itemId) }
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
            apiClient.refreshItemMetadata(itemId, mode, mode, replaceAllMetadata, replaceAllImages)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun getImageUrl(itemId: String, imageInfo: com.raulshma.jellyplay.core.model.ImageInfo): String {
        return apiClient.getImageUrl(
            itemId,
            imageInfo.imageType,
            400,
            imageInfo.imageIndex,
            imageInfo.imageTag,
        )
    }

    fun getFullImageUrl(itemId: String, imageInfo: com.raulshma.jellyplay.core.model.ImageInfo): String {
        return apiClient.getImageUrl(
            itemId,
            imageInfo.imageType,
            null,
            imageInfo.imageIndex,
            imageInfo.imageTag,
        )
    }

    private suspend fun reloadImageInfos(itemId: String) {
        apiClient.getItemImageInfo(itemId)
            .onSuccess { infos -> _uiState.update { it.copy(imageInfos = infos) } }
    }

    private fun computeDirtyHash(state: EditorUiState = _uiState.value): Int {
        val s = state
        return listOf(
            s.name, s.originalTitle, s.sortName, s.overview, s.tagline,
            s.communityRating, s.criticRating, s.officialRating, s.customRating,
            s.productionYear, s.premiereDate, s.endDate, s.runtimeMinutes,
            s.indexNumber, s.parentIndexNumber, s.displayOrder, s.status, s.airTime,
            s.genres.joinToString(), s.tags.joinToString(), s.studios.joinToString(),
            s.airDays.joinToString(), s.taglines.joinToString(), s.productionLocations.joinToString(),
            s.lockData.toString(), s.lockedFields.joinToString(),
            s.preferredMetadataLanguage, s.preferredMetadataCountryCode,
            s.people.joinToString { "${it.name}:${it.role}:${it.type}" },
            s.providerIds.entries.joinToString { "${it.key}=${it.value}" },
        ).hashCode()
    }
}
