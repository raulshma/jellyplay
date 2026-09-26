package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind

/**
 * One external-provider subtitle's durable-save request — the parameter
 * bundle for [EditorSubtitleStore]. Carries exactly the identity the
 * core:data store keys its manifest entries and upload attribution off; the
 * JVM adapter reconstructs core:model's jvmShared `SavedSubtitle` from it
 * (safe: attribution matches on language/codec/role flags and the manifest
 * update keys off provider + providerSubtitleId — the on-disk relative path
 * never crosses this seam).
 */
internal class ProviderSubtitleSave(
    val itemId: String,
    val provider: SubtitleProviderKind,
    val providerSubtitleId: String,
    val fileName: String,
    val language: String?,
    val codec: String?,
    val isForced: Boolean,
    val isHearingImpaired: Boolean,
    val bytes: ByteArray,
)

/**
 * Common seam over core:data's jvmShared
 * `StreamingSubtitleStore` (its `fileFor(): java.io.File` surface keeps the
 * interface JVM-bound, so commonMain cannot name it — same treatment as
 * search/library's quick-download seam over `MediaDownloadActions`).
 */
internal interface EditorSubtitleStore {

    /** Persists the subtitle bytes durably on-device (see the seam KDoc). */
    suspend fun save(save: ProviderSubtitleSave)

    /**
     * Records the server `MediaStream.index` the just-completed upload of
     * [save] produced, by locating it among [streamsAfterUpload] — so a later
     * server-side delete can purge exactly this local copy.
     */
    suspend fun attributeUploaded(
        save: ProviderSubtitleSave,
        streamsAfterUpload: List<MediaStream>,
        preUploadExternalIndices: Set<Int>,
    )

    /** Deletes local copies of the server subtitle stream just removed at [index]. */
    suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?)
}
