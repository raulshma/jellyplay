package com.raulshma.jellyplay.feature.book

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The junrar adapter behind [RarEntryReader] — store-source-set resident
 * (same package as the player-book seam types it implements) because junrar
 * ships under the UnRAR license: free to use for decompression only; it must
 * never be used to (re)create RAR archives. That license is non-free for
 * F-Droid, so this class (and the junrar dependency) exists only on the store
 * classpath — the fdroid line binds no [RarEntryReaderFactory] and treats RAR
 * comics as unsupported.
 *
 * junrar's [Archive] is not thread-safe, so extractions are single-flighted
 * the same way the PDF back-ends serialize renders.
 */
class JunrarRarEntryReader private constructor(
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
         * The Koin-bound [RarEntryReaderFactory] (store DistributionPlatform):
         * opens [file] through [open], null on any open failure — the same
         * cannot-open contract the comic pager's zip path serves.
         */
        val FACTORY: RarEntryReaderFactory = RarEntryReaderFactory { file -> open(file) }

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
