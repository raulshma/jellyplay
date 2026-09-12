package com.raulshma.jellyplay.feature.book.di

import android.content.Context
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.feature.book.AndroidBookDocumentOpener
import com.raulshma.jellyplay.feature.book.BookContentResolver
import com.raulshma.jellyplay.feature.book.BookDocumentOpener
import com.raulshma.jellyplay.feature.book.BookHttpFetcher
import com.raulshma.jellyplay.feature.book.OkHttpBookContentResolver
import com.raulshma.jellyplay.feature.book.OkHttpBookFetcher
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
    single<BookContentResolver> {
        OkHttpBookContentResolver(
            playbackSourceResolver = get(),
            fetcher = get(),
            cacheRoot = context.cacheDir.absolutePath.toPath(),
        )
    }
    single<BookDocumentOpener> { AndroidBookDocumentOpener() }
}
