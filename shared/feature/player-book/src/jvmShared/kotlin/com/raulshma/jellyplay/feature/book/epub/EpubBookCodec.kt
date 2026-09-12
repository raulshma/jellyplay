package com.raulshma.jellyplay.feature.book.epub

import okio.Path
import java.io.File
import java.util.Base64

/**
 * EPUB bytes for the WebView. Both EPUB hosts are JVM (Android WebView /
 * desktop CEF), so this lives in jvmShared. The book travels base64-inlined
 * into the reader page — the WebView gets no file:// access.
 */
internal object EpubBookCodec {
    fun encodeBase64(path: Path): String =
        Base64.getEncoder().encodeToString(File(path.toString()).readBytes())
}
