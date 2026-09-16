package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

/**
 * The quick-action download/remove seam every host surface shares (library,
 * favorites, search, studio, home). Originally shipped as byte-identical
 * `internal` twins in feature:library and feature:search (one landed by copy,
 * the other by paste); hoisted here so the contract has one home beside the
 * [MediaDownloadActions] delegate it wraps and the
 * [DownloadRequestResult] vocabulary it returns.
 *
 * The JVM actual — [JvmQuickDownloadActions] in jvmShared, bound in
 * dataJvmModule — delegates to the process-wide `MediaDownloadActions`
 * single (same messenger routing, same shared downloadedIds flow —
 * android/desktop behavior unchanged). The wasmJs actual —
 * [WasmQuickDownloadActions] in wasmJsMain, bound in dataWasmModule — is an
 * honest no-op.
 *
 * IDIOM RULE (declared with the download-actions seam consolidation): a seam
 * that exists to cross the commonMain↔jvmShared visibility wall — like this
 * one, whose natural impls close over the JVM download engine — is DECLARED,
 * IMPLEMENTED AND BOUND by core:data on both platforms. Features never grow
 * their own wall-crossing template (the deleted feature twins this replaced
 * were exactly that mistake); a feature needing a download read either
 * consumes one of core:data's download seams or the seam moves here.
 *
 * Web behavior: the browser has no local download pipeline, so the wasm
 * actual reports [isSupported] = false — web hosts should hide/disable the
 * download CTAs — while `downloadedIds` stays empty and download/remove calls
 * are inert (download resolves to [DownloadRequestResult.Failed]).
 */
interface QuickDownloadActions {

    /** Whether this platform has a download pipeline; gates download CTAs. */
    val isSupported: Boolean

    /**
     * Ids whose quick actions flip to "Remove download" — completed downloads
     * ∪ series ids (the union contract of MediaDownloadActions.downloadedIds).
     */
    val downloadedIds: StateFlow<Set<String>>

    /** Start a download for [item] (see [MediaDownloadActions.download]). */
    suspend fun download(item: MediaItem): DownloadRequestResult

    /**
     * [download] plus the shared outcome handling: Started/Failed surface via
     * the platform messenger, both navigation outcomes route to [onOpenDetail].
     */
    suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit)

    /** Remove [item]'s local download (artifacts + offline rows). Fire-and-forget. */
    fun removeDownload(item: MediaItem)
}
