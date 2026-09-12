package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

/**
 * Comic-archive (CBZ/CBR) pager. Entries keep only image extensions
 * (jpg/jpeg/png/gif/bmp/webp — DJVU-style sidecars and metadata files are
 * dropped) and order them with a digit-aware natural sort, so `page2.jpg`
 * lands before `page10.jpg` the way a reader expects.
 *
 * [Companion.open] sniffs the container: the zip path is tried first and RAR
 * only when it fails or holds no images — some .cbr files are actually zip,
 * so content beats the file extension. Pages decode through the platform
 * [decodeImageBytes] actual (Android BitmapFactory / desktop ImageIO) and
 * pass through a [PageCache] so a swipe never re-extracts the neighboring
 * page. Render sizing is ignored — archives carry no vector layer, so pages
 * decode at their native size.
 */
class ComicArchiveDocument private constructor(
    private val source: PageSource,
) : BookDocument {

    override val pageCount: Int get() = source.pageCount

    private val cache = PageCache<ImageBitmap>()

    /** Entry names in page order — exposed (internal) for the natural-sort fixture test. */
    internal val pageEntryNames: List<String> get() = source.pageEntryNames

    override suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap? {
        if (pageIndex !in 0 until source.pageCount) return null
        cache[pageIndex]?.let { return it }
        val bytes = withContext(Dispatchers.IO) { source.readBytes(pageIndex) } ?: return null
        return decodeImageBytes(bytes)?.also { cache.put(pageIndex, it) }
    }

    override fun onPageChanged(page: Int) = cache.onPageChanged(page)

    override fun close() {
        cache.clear()
        source.close()
    }

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

        /** Digit-aware natural order — "2" < "10"; digit runs compare numerically, the rest char-wise. */
        internal fun naturalCompare(a: String, b: String): Int {
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                val ca = a[i]
                val cb = b[j]
                if (ca.isDigit() && cb.isDigit()) {
                    var ie = i
                    while (ie < a.length && a[ie].isDigit()) ie++
                    var je = j
                    while (je < b.length && b[je].isDigit()) je++
                    val na = a.substring(i, ie)
                    val nb = b.substring(j, je)
                    val cmp = if (na.length <= 18 && nb.length <= 18) {
                        na.toLong().compareTo(nb.toLong())
                    } else {
                        // Beyond Long range: shorter run is smaller, then raw
                        // char order — never throw inside sortedWith.
                        na.length.compareTo(nb.length).takeIf { it != 0 } ?: na.compareTo(nb)
                    }
                    if (cmp != 0) return cmp
                    i = ie
                    j = je
                } else {
                    if (ca != cb) return ca.compareTo(cb)
                    i++
                    j++
                }
            }
            return (a.length - i).compareTo(b.length - j)
        }

        /** Image-extension filter shared by the zip and rar pipelines. */
        internal fun isImageEntry(name: String, imageExtensions: Set<String>): Boolean =
            name.substringAfterLast('.', "").lowercase() in imageExtensions

        /** Filter + natural-sort a list of entry names — the rar pipeline's pure, fake-testable core. */
        internal fun pageNames(entryNames: List<String>, imageExtensions: Set<String>): List<String> =
            entryNames
                .filter { isImageEntry(it, imageExtensions) }
                .sortedWith { a, b -> naturalCompare(a, b) }

        /**
         * Opens [path] as a comic archive; null when neither container yields
         * images. Zip is tried first and RAR only on failure or an image-less
         * zip — sniffing beats extension trust.
         */
        fun open(path: Path, imageExtensions: Set<String> = IMAGE_EXTENSIONS): ComicArchiveDocument? {
            openZip(path, imageExtensions)?.let { return it }
            return openRar(path, imageExtensions)
        }

        private fun openZip(path: Path, imageExtensions: Set<String>): ComicArchiveDocument? = runCatching {
            val zip = ZipFile(path.toFile())
            val imageEntries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { isImageEntry(it.name, imageExtensions) }
                .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                .toList()
            if (imageEntries.isEmpty()) {
                runCatching { zip.close() }
                null
            } else {
                ComicArchiveDocument(ZipPageSource(zip, imageEntries))
            }
        }.getOrNull()

        /**
         * RAR (CBR) back-end: junrar extracts one page at a time into memory —
         * never the whole archive upfront.
         *
         * Licensing: junrar ships under the UnRAR license — free to use for
         * decompression only; it must never be used to (re)create RAR archives.
         */
        private fun openRar(path: Path, imageExtensions: Set<String>): ComicArchiveDocument? {
            val reader = JunrarRarEntryReader.open(path.toFile()) ?: return null
            val names = pageNames(reader.entryNames, imageExtensions)
            if (names.isEmpty()) {
                runCatching { reader.close() }
                return null
            }
            return ComicArchiveDocument(RarPageSource(names, reader))
        }
    }
}

/** File-scoped per-page byte source; members block — call from Dispatchers.IO. */
private interface PageSource : AutoCloseable {
    val pageCount: Int
    val pageEntryNames: List<String>

    /** Null when the entry can't be read (corrupt archive / decode-time failure surfaced earlier). */
    fun readBytes(pageIndex: Int): ByteArray?
}

private class ZipPageSource(
    private val zip: ZipFile,
    private val entries: List<ZipEntry>,
) : PageSource {
    override val pageCount: Int get() = entries.size
    override val pageEntryNames: List<String> get() = entries.map { it.name }
    override fun readBytes(pageIndex: Int): ByteArray? = runCatching {
        zip.getInputStream(entries[pageIndex]).use { stream -> stream.readBytes() }
    }.getOrNull()
    override fun close() {
        runCatching { zip.close() }
    }
}

/**
 * Seam over junrar so the filter/sort/dispatch logic stays unit-testable
 * without a real .rar fixture. junrar ships under the UnRAR license — free to
 * use for decompression only; it must never be used to (re)create RAR
 * archives.
 */
internal interface RarEntryReader : AutoCloseable {
    /** All non-directory entry header names, archive order. */
    val entryNames: List<String>

    /** Extracts one entry into memory; null on any failure. Implementations must be single-flight. */
    fun readEntry(headerName: String): ByteArray?

    override fun close() = Unit
}

/**
 * junrar adapter. junrar's [Archive] is not thread-safe, so extractions are
 * single-flighted the same way the PDF back-ends serialize renders.
 */
internal class JunrarRarEntryReader private constructor(
    private val archive: Archive,
    private val headersByName: Map<String, FileHeader>,
) : RarEntryReader {

    override val entryNames: List<String> get() = headersByName.keys.toList()

    override fun readEntry(headerName: String): ByteArray? {
        val header = headersByName[headerName] ?: return null
        return synchronized(archive) {
            runCatching {
                ByteArrayOutputStream().use { out ->
                    archive.extractFile(header, out)
                    out.toByteArray()
                }
            }.getOrNull()
        }
    }

    override fun close() {
        runCatching { archive.close() }
    }

    companion object {
        fun open(file: File): JunrarRarEntryReader? = runCatching {
            val archive = Archive(file)
            // RAR permits duplicate entry names (updated files stored twice) —
            // keep the first header per name instead of silently overwriting.
            val headers = LinkedHashMap<String, FileHeader>()
            archive.fileHeaders
                .filter { !it.isDirectory }
                .forEach { headers.putIfAbsent(headerName(it), it) }
            JunrarRarEntryReader(archive, headers)
        }.getOrNull()

        private fun headerName(header: FileHeader): String = header.fileName
    }
}

private class RarPageSource(
    private val names: List<String>,
    private val reader: RarEntryReader,
) : PageSource {
    override val pageCount: Int get() = names.size
    override val pageEntryNames: List<String> get() = names
    override fun readBytes(pageIndex: Int): ByteArray? = reader.readEntry(names[pageIndex])
    override fun close() {
        reader.close()
    }
}
