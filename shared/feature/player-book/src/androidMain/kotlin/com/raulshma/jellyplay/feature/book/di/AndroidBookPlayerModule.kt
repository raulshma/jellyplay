package com.raulshma.jellyplay.feature.book.di

import android.content.Context
import com.raulshma.jellyplay.core.data.book.BookTocProber
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.feature.book.AndroidBookDocumentOpener
import com.raulshma.jellyplay.feature.book.AndroidBookSpeechEngine
import com.raulshma.jellyplay.feature.book.BookContentResolver
import com.raulshma.jellyplay.feature.book.BookDocumentOpener
import com.raulshma.jellyplay.feature.book.BookFormatProbe
import com.raulshma.jellyplay.feature.book.BookHttpFetcher
import com.raulshma.jellyplay.feature.book.BookSpeechEngine
import com.raulshma.jellyplay.feature.book.LocalBookTocProber
import com.raulshma.jellyplay.feature.book.OkHttpBookContentResolver
import com.raulshma.jellyplay.feature.book.OkHttpBookFetcher
import com.raulshma.jellyplay.feature.book.OkHttpBookFormatProbe
import com.raulshma.jellyplay.feature.book.PdfOutlineParser
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android wiring for the book reader: the OkHttp fetcher rides the shared
 * streaming client (NetworkQualifiers.streamingHttpClient), the reader cache
 * roots at the app cacheDir, and PDF pages render through the platform
 * PdfRenderer. Lazy singles — nothing touches disk or the network until the
 * reader screen first resolves them.
 */
fun androidBookPlayerModule(context: Context): Module = module {
    single<BookHttpFetcher> { OkHttpBookFetcher(get(NetworkQualifiers.streamingHttpClient)) }
    single<BookFormatProbe> { OkHttpBookFormatProbe(get(NetworkQualifiers.streamingHttpClient)) }
    single<BookContentResolver> {
        OkHttpBookContentResolver(
            playbackSourceResolver = get(),
            fetcher = get(),
            cacheRoot = context.cacheDir.absolutePath.toPath(),
        )
    }
    single<BookDocumentOpener> { AndroidBookDocumentOpener() }
    // Read-aloud TTS — the engine connects its service lazily on first
    // speak, so this binding stays cheap until the reader actually speaks.
    single<BookSpeechEngine> { AndroidBookSpeechEngine(context.applicationContext) }
    // pdfbox-android needs one PDFBoxResourceLoader.init(context) before its
    // first call — the parser lazy-inits from this context (see its KDoc).
    single<PdfOutlineParser> { PdfOutlineParser(context.applicationContext) }
    // The detail screen's TOC probe (never-opened books) — same PDFBox
    // binding, plus the jvmShared EPUB/comic parsers.
    single<BookTocProber> { LocalBookTocProber(get()) }
}
