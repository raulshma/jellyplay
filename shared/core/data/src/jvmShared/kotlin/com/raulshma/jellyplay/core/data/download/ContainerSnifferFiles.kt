package com.raulshma.jellyplay.core.data.download

import java.io.File
import java.io.InputStream

/**
 * File-reading glue for the pure byte-level [ContainerSniffer]: reads the
 * first [ContainerSniffer.SNIFF_HEADER_BYTES] bytes of the download at
 * [downloadPath] and delegates detection to the sniffer.
 *
 * Lives in jvmShared (not commonMain) and is
 * what the database layer's backfill probe resolves to — see
 * shared:core:data's `dataJvmModule`, which binds
 * `com.raulshma.jellyplay.core.database.migration.ContainerProbe` over this
 * function for MIGRATION_53_54.
 *
 * Returns the container code, or `null` when the file is missing,
 * unreadable, too short, or unrecognized — callers treat null as "leave the
 * row's container NULL", so this never throws.
 */
fun sniffContainerFile(downloadPath: String): String? {
    val file = File(downloadPath)
    if (!file.exists() || !file.canRead()) return null
    val buf = ByteArray(ContainerSniffer.SNIFF_HEADER_BYTES)
    return try {
        file.inputStream().use { input ->
            val read = readFully(input, buf)
            if (read < ContainerSniffer.MIN_SNIFF_BYTES) null
            else ContainerSniffer.sniff(buf, read)
        }
    } catch (_: Exception) {
        null
    }
}

private fun readFully(input: InputStream, buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val n = input.read(buf, total, buf.size - total)
        if (n < 0) break
        total += n
    }
    return total
}
