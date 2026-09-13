package com.raulshma.jellyplay.feature.book.di

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.feature.book.BookContentResolver
import com.raulshma.jellyplay.feature.book.BookDocumentOpener
import com.raulshma.jellyplay.feature.book.BookReaderViewModel
import com.raulshma.jellyplay.feature.book.NoopBookFormatProbe
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the book reader. The ViewModel's content/opener
 * deps ([BookContentResolver], [BookDocumentOpener]) bind in the platform
 * modules (androidBookPlayerModule / desktopBookPlayerModule); every
 * repository, the ReaderStore and the application scope resolve from the
 * shared core graph. The format probe binds where an HTTP stack exists
 * (jvmShared platforms); elsewhere `getOrNull()` degrades to the neutral
 * [NoopBookFormatProbe].
 */
val playerBookModule: Module = module {
    viewModel {
        BookReaderViewModel(
            mediaRepository = get(),
            playbackRepository = get(),
            readerStore = get(),
            contentResolver = get(),
            documentOpener = get(),
            formatProbe = getOrNull() ?: NoopBookFormatProbe,
            flushScope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
