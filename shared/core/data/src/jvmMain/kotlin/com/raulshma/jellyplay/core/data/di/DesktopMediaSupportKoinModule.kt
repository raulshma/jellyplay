package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.DesktopLocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Media-surface support family of the desktopDataModule split: the shared
 * LinkedHashMap-based image-URL memoiser, the (unsupported, badge-less)
 * desktop stream probe, and the file-backed StreamingSubtitleStore (the
 * same adjacency androidDataModule uses for its memoiser + metadata probe).
 * Binding bodies moved verbatim from the pre-split single-module layout —
 * see [desktopDataModule] for the aggregate and the family map.
 */
internal fun desktopMediaSupportModule(dataDir: Path): Module = module {
    single<ImageUrlProvider> {
        // The shared jvmShared impl — the desktop twin (DesktopImageUrlProvider)
        // was deleted once the policy lived in one class next to the interface.
        ImageUrlProviderImpl(
            playbackRepository = get(),
            appearanceStore = get(),
        )
    }

    single<LocalStreamProbe> { DesktopLocalStreamProbe() }

    // ── Streaming-subtitle store (promotion) ────────────────────
    // The impl moved out of the legacy Android-Hilt-owned :core:data shim
    // into jvmShared, so desktop gets the real file-backed store, not a
    // stub. baseDir is the appdata dir — the desktop twin of Android's
    // `filesDir`; the impl appends its own "streaming-subtitles" root
    // (same subtree name on both platforms). Backs the metadata editor's
    // external-provider subtitle downloads AND the player's
    // SubtitleManager provider-download path on desktop.
    single<StreamingSubtitleStore> {
        StreamingSubtitleStoreImpl(
            baseDir = dataDir.toFile(),
            json = get(),
        )
    }
}
