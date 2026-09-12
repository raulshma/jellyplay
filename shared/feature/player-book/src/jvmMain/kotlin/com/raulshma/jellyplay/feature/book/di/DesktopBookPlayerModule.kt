package com.raulshma.jellyplay.feature.book.di

import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.feature.book.BookContentResolver
import com.raulshma.jellyplay.feature.book.BookDocumentOpener
import com.raulshma.jellyplay.feature.book.BookHttpFetcher
import com.raulshma.jellyplay.feature.book.DesktopBookDocumentOpener
import com.raulshma.jellyplay.feature.book.OkHttpBookContentResolver
import com.raulshma.jellyplay.feature.book.OkHttpBookFetcher
import com.raulshma.jellyplay.feature.book.epub.EpubDesktopEnv
import com.raulshma.jellyplay.feature.book.epub.KcefRuntime
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop wiring for the book reader. Self-contained EXCEPT for the cache
 * root: the desktop shell owns the data-dir seam (DesktopPaths), so this
 * factory takes the dir as a parameter — the registration site must call
 * `desktopBookPlayerModule(DesktopPaths.dataDir)`. PDF pages render through
 * PDFBox (catalog `pdfbox`); EPUB renders through KCEF, whose bundle/cache
 * dirs and reader-page scratch dir also derive from the data dir.
 */
fun desktopBookPlayerModule(dataDir: okio.Path): Module = module {
    single<BookHttpFetcher> { OkHttpBookFetcher(get(NetworkQualifiers.streamingHttpClient)) }
    single<BookContentResolver> {
        OkHttpBookContentResolver(
            playbackSourceResolver = get(),
            fetcher = get(),
            cacheRoot = dataDir,
        )
    }
    single<BookDocumentOpener> { DesktopBookDocumentOpener() }
    single {
        EpubDesktopEnv(
            kcefDir = (dataDir / "kcef").toFile(),
            cacheDir = (dataDir / "kcef-cache").toFile(),
            readerHtmlDir = (dataDir / "epub-reader").toFile(),
        )
    }
    single { KcefRuntime(get()) }
}
