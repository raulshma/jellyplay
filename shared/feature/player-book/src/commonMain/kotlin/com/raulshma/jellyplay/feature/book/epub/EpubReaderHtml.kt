package com.raulshma.jellyplay.feature.book.epub

import com.raulshma.jellyplay.feature.book.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

/**
 * Builds the self-contained reader page: the `reader.html` shell with the
 * vendored `jszip.min.js`, `epub.min.js` and `reader.js` inlined as script
 * bodies, so the WebView needs no file/network access at all.
 */
internal object EpubReaderHtml {

    private const val HTML_RESOURCE = "files/epubjs/reader.html"
    private const val JSZIP_RESOURCE = "files/epubjs/jszip.min.js"
    private const val EPUB_RESOURCE = "files/epubjs/epub.min.js"
    private const val READER_RESOURCE = "files/epubjs/reader.js"

    private fun marker(of: String) = "<!--JELLYPLAY-INJECT:$of-->"

    suspend fun build(): String = withContext(Dispatchers.IO) {
        val template = Res.readBytes(HTML_RESOURCE).decodeToString()
        template
            .replace(marker("jszip"), script(JSZIP_RESOURCE))
            .replace(marker("epub"), script(EPUB_RESOURCE))
            .replace(marker("reader"), script(READER_RESOURCE))
    }

    /**
     * Deterministic artifact name for a given document (desktop writes the
     * built page to `<data>/epub-reader`): a resource change produces a new
     * file name, so the writer only creates a file when content changed.
     */
    fun fileName(html: String): String {
        // FNV-1a over the UTF-8 bytes — commonMain-safe artifact identity
        // (collision-free for this use: same doc bytes → same name).
        var hash = -0x340d631b7bdddcdbL
        for (b in html.encodeToByteArray()) {
            hash = (hash xor (b.toLong() and 0xFF)) * 0x100000001b3
        }
        return "reader-${hash.toString(16)}.html"
    }

    /**
     * Minified bundles may embed `</script>` inside string literals, which
     * would end the inline tag early; `<\/script>` is a no-op escape for JS
     * strings/regexes/templates, so it is safe to substitute blindly.
     */
    private suspend fun script(resource: String): String {
        val js = Res.readBytes(resource).decodeToString().replace("</script", "<\\/script")
        return "<script>$js</script>"
    }
}
