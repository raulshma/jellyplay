package com.raulshma.jellyplay.feature.editor.di

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.feature.editor.EditorSubtitleStore
import com.raulshma.jellyplay.feature.editor.ProviderSubtitleSave
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The wasmJs actual of the editor subtitle seam: a no-op archive. The browser
 * has no durable app-data directory for subtitle files, so nothing is
 * persisted — the editor's external provider download still uploads to the
 * Jellyfin server (pure repository traffic), but no device-local copy exists,
 * so there is nothing to attribute after an upload or purge after a delete.
 * See [EditorSubtitleStore]'s KDoc.
 */
internal object WasmEditorSubtitleStore : EditorSubtitleStore {
    override suspend fun save(save: ProviderSubtitleSave) = Unit
    override suspend fun attributeUploaded(
        save: ProviderSubtitleSave,
        streamsAfterUpload: List<MediaStream>,
        preUploadExternalIndices: Set<Int>,
    ) = Unit
    override suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?) = Unit
}

internal actual fun platformEditorModule(): Module = module {
    single<EditorSubtitleStore> { WasmEditorSubtitleStore }
}
