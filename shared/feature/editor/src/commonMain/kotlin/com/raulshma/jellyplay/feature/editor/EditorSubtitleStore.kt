package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

    /**
     * The provider-subtitle choreography the editor VM used to inline as ~40
     * lines inside `handleDownloadProviderSubtitle` — it has exactly one
     * caller and every half of it (persist/attribute) already lived here, so
     * the sequencing belongs here too:
     *
     * 1. [save] — the durable on-device copy FIRST, so the subtitle survives
     *    even if the server upload fails (e.g. offline; mirrors the player's
     *    SubtitleManager provider-download path).
     * 2. Base64-encode the bytes and snapshot the CURRENT external subtitle
     *    indices through [streams] (the pre-upload set [attributeUploaded]
     *    matches the new stream against).
     * 3. [upload] — the server round trip, handed the encoded payload.
     * 4. On upload success: [onUploaded] (the editor's data reload, so the
     *    new server stream is visible) then [attributeUploaded] against the
     *    post-upload [streams] snapshot — so a later server-side delete can
     *    purge exactly this local copy.
     *
     * The upload Result is returned verbatim; the failure arm stays
     * caller-side (the VM owns the busy/error flags and the
     * "Saved to device only" degradation wording). A default body keeps the
     * seam's implementors (the JVM adapter and the tests' recording fake)
     * untouched by this fold.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun saveUploadAndAttribute(
        save: ProviderSubtitleSave,
        streams: () -> List<MediaStream>,
        upload: suspend (base64: String) -> Result<Unit>,
        onUploaded: suspend () -> Unit,
    ): Result<Unit> {
        save(save)
        val base64 = Base64.Default.encode(save.bytes)
        val preUploadExternalIndices = streams()
            .filter { it.type == StreamType.SUBTITLE && it.isExternal }
            .mapTo(mutableSetOf()) { it.index }
        return upload(base64)
            .onSuccess {
                onUploaded()
                attributeUploaded(
                    save = save,
                    streamsAfterUpload = streams(),
                    preUploadExternalIndices = preUploadExternalIndices,
                )
            }
    }
}
