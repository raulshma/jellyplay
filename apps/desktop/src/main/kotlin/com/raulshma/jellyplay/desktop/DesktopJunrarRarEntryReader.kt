package com.raulshma.jellyplay.desktop

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.raulshma.jellyplay.feature.book.RarEntryReader
import com.raulshma.jellyplay.feature.book.RarEntryReaderFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The desktop-hosted twin of the store line's junrar adapter
 * (app/src/store/java/.../feature/book/JunrarRarEntryReader): a module-local
 * copy because apps/desktop cannot see the app module's source set, and the
 * shared player-book module must stay junrar-free (its UnRAR license — free
 * to use for decompression only; it must never be used to (re)create RAR
 * archives — is non-free for F-Droid, which compiles the same player-book).
 *
 * junrar's [Archive] is not thread-safe, so extractions are single-flighted
 * the same way the PDF back-ends serialize renders.
 */
class DesktopJunrarRarEntryReader private constructor(
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
        /**
         * The desktop Koin-bound [RarEntryReaderFactory]: opens [file] through
         * [open], null on any open failure — the same cannot-open contract the
         * comic pager's zip path serves.
         */
        val FACTORY: RarEntryReaderFactory = RarEntryReaderFactory { file -> open(file) }

        fun open(file: File): DesktopJunrarRarEntryReader? = runCatching {
            val archive = Archive(file)
            // RAR permits duplicate entry names (updated files stored twice) —
            // keep the first header per name instead of silently overwriting.
            val headers = LinkedHashMap<String, FileHeader>()
            archive.fileHeaders
                .filter { !it.isDirectory }
                .forEach { headers.putIfAbsent(headerName(it), it) }
            DesktopJunrarRarEntryReader(archive, headers)
        }.getOrNull()

        private fun headerName(header: FileHeader): String = header.fileName
    }
}
