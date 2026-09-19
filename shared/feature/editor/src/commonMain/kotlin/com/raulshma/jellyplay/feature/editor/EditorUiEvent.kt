package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult

/**
 * Every user intent the editor screen can express. [EditorViewModel.onEvent]
 * is the single command funnel (the HomeUiEvent precedent): the VM exposes no
 * per-action command methods beyond it and the sync image-URL getters, so new
 * intents are added here (and routed once in the `when`) rather than as new
 * public members on the VM.
 *
 * The bytes-level [UploadImage] / [UploadSubtitle] intents are the raw-payload
 * halves the file-picker variants funnel through internally; they stay events
 * so the byte paths remain directly dispatchable.
 */
sealed interface EditorUiEvent {
    /** Initial (or re-) load of an item's editor data. */
    data class LoadEditorData(val itemId: String) : EditorUiEvent

    /** Persists the metadata form (toolbar Save / discard-dialog Save). */
    data object SaveMetadata : EditorUiEvent

    /** Edits the metadata form (typed — the lambda sees only the form). */
    data class UpdateField(val update: (EditableItemMetadataForm) -> EditableItemMetadataForm) : EditorUiEvent

    /** Clears the transient error shown by the screen's error banner. */
    data object ClearError : EditorUiEvent

    /** Uploads raw image bytes (the payload half of [UploadImageFromFile]). */
    data class UploadImage(val imageBytes: ByteArray, val imageType: String) : EditorUiEvent

    /** Uploads a platform-picked image file. */
    data class UploadImageFromFile(val file: EditorPickedFile, val imageType: String) : EditorUiEvent

    /** Downloads a remote image onto the item. */
    data class UploadImageFromUrl(val url: String, val imageType: String) : EditorUiEvent

    /** Deletes one of the item's images (`imageIndex` null = the type's only/first). */
    data class DeleteImage(val imageType: String, val imageIndex: Int? = null) : EditorUiEvent

    /** Fetches remote (provider) images for the browse sheet. */
    data class LoadRemoteImages(
        val imageType: String? = null,
        val provider: String? = null,
        val startIndex: Int? = null,
    ) : EditorUiEvent

    /** Uploads raw subtitle bytes (the payload half of [UploadSubtitleFromFile]). */
    data class UploadSubtitle(
        val fileBytes: ByteArray,
        val fileName: String,
        val language: String?,
        val isForced: Boolean,
        val isHearingImpaired: Boolean,
    ) : EditorUiEvent

    /** Uploads a platform-picked subtitle file. */
    data class UploadSubtitleFromFile(
        val file: EditorPickedFile,
        val fileName: String,
        val language: String?,
        val isForced: Boolean,
        val isHearingImpaired: Boolean,
    ) : EditorUiEvent

    /** Deletes a server subtitle stream by index. */
    data class DeleteSubtitle(val index: Int) : EditorUiEvent

    /** Searches the Jellyfin server's remote subtitles. */
    data class SearchRemoteSubtitles(val language: String) : EditorUiEvent

    /** Downloads a server remote subtitle onto the item. */
    data class DownloadRemoteSubtitle(val subtitleId: String) : EditorUiEvent

    /** Loads the user's configured subtitle providers (chip visibility). */
    data object LoadConfiguredSubtitleProviders : EditorUiEvent

    /** Concurrent cross-provider subtitle search (Jellyfin + external). */
    data class SearchAllSubtitleProviders(val language: String) : EditorUiEvent

    /** Downloads an external-provider subtitle and persists it as a media stream. */
    data class DownloadProviderSubtitle(val result: SubtitleSearchResult) : EditorUiEvent

    /** Triggers a server-side metadata refresh. */
    data class RefreshMetadata(
        val mode: String = "FullRefresh",
        val replaceAllMetadata: Boolean = false,
        val replaceAllImages: Boolean = false,
    ) : EditorUiEvent
}
