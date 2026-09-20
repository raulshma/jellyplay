package com.raulshma.jellyplay.feature.book.di

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.feature.book.BookContentResolver
import com.raulshma.jellyplay.feature.book.BookDocumentOpener
import com.raulshma.jellyplay.feature.book.BookReaderViewModel
import com.raulshma.jellyplay.feature.book.ReaderPreferences
import com.raulshma.jellyplay.feature.book.BookSpeechEngine
import com.raulshma.jellyplay.feature.book.NoopBookFormatProbe
import com.raulshma.jellyplay.feature.book.NoopBookSpeechEngine
import com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.feature.book.PdfOutlineParser
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the book reader. The ViewModel's content/opener
 * deps ([BookContentResolver], [BookDocumentOpener], [PdfOutlineParser]) bind
 * in the platform modules (androidBookPlayerModule / desktopBookPlayerModule);
 * every repository (including the marks-owner [ReaderAnnotationsRepository]),
 * the ReaderStore and the application scope resolve from the shared core
 * graph. The format probe and the speech engine bind where their platform
 * exists (jvmShared / android / desktop); elsewhere `getOrNull()` degrades to
 * the neutral [NoopBookFormatProbe] / [NoopBookSpeechEngine].
 */
val playerBookModule: Module = module {
    viewModel {
        BookReaderViewModel(
            mediaRepository = get(),
            playbackRepository = get(),
            preferences = ReaderPreferences(store = get(), scope = get(DatastoreQualifiers.applicationScope)),
            annotationsRepository = get(),
            contentResolver = get(),
            documentOpener = get(),
            pdfOutlineParser = get(),
            formatProbe = getOrNull() ?: NoopBookFormatProbe,
            speechEngine = getOrNull() ?: NoopBookSpeechEngine,
            playbackFocus = getOrNull() ?: NoopPlaybackFocus,
            tocCacheRepository = get(),
            flushScope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
