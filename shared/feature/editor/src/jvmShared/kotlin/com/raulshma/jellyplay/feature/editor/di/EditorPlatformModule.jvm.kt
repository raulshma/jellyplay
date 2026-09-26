package com.raulshma.jellyplay.feature.editor.di

import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.feature.editor.EditorSubtitleStore
import com.raulshma.jellyplay.feature.editor.ProviderSubtitleSave
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The JVM adapter over core:data's `StreamingSubtitleStore` single — the
 * store's file-backed behavior (Android filesDir / desktop appdata dir) is
 * consumed verbatim; the adapter only bridges the seam types.
 *
 * The attribution call rebuilds a `SavedSubtitle` from the request's identity
 * fields with placeholder disk fields (`fileRelativePath = ""`,
 * `serverStreamIndex = null`): `attributeUploadedSubtitle` matches streams on
 * language/codec/role flags and `markServerStreamIndex` keys the manifest
 * update off provider + providerSubtitleId — neither touches the placeholder
 * fields.
 */
internal class JvmEditorSubtitleStore(
    private val store: StreamingSubtitleStore,
) : EditorSubtitleStore {


    override suspend fun save(save: ProviderSubtitleSave) {
        store.save(
            itemId = save.itemId,
            provider = save.provider,
            providerSubtitleId = save.providerSubtitleId,
            fileName = save.fileName,
            language = save.language,
            codec = save.codec,
            isForced = save.isForced,
            isHearingImpaired = save.isHearingImpaired,
            bytes = save.bytes,
        )
    }

    override suspend fun attributeUploaded(
        save: ProviderSubtitleSave,
        streamsAfterUpload: List<MediaStream>,
        preUploadExternalIndices: Set<Int>,
    ) {
        store.attributeUploadedSubtitle(
            itemId = save.itemId,
            saved = SavedSubtitle(
                provider = save.provider,
                providerSubtitleId = save.providerSubtitleId,
                fileName = save.fileName,
                language = save.language,
                codec = save.codec,
                isForced = save.isForced,
                isHearingImpaired = save.isHearingImpaired,
                fileRelativePath = "",
            ),
            streamsAfterUpload = streamsAfterUpload,
            preUploadExternalIndices = preUploadExternalIndices,
        )
    }

    override suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?) {
        store.purgeDeletedServerStreamCopies(itemId, index, deletedStream)
    }
}

internal actual fun platformEditorModule(): Module = module {
    single<EditorSubtitleStore> { JvmEditorSubtitleStore(get()) }
}
