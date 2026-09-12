package com.raulshma.jellyplay.core.database.migration

/**
 * Resolves the container format code (`"mkv"`, `"webm"`, `"mp4"`, `"ts"`,
 * `"flv"`, `"avi"`) of a downloaded media file from its on-disk header
 * bytes, or `null` when the file is missing, unreadable, too short or
 * unrecognized.
 *
 * Migration seam (mirrors [com.raulshma.jellyplay.core.database.crypto.TokenCipher]):
 * [Migration53To54] backfills `downloads.container` for legacy rows by
 * probing each row's `downloadPath`, but this module can neither do file IO
 * in commonMain nor see the magic-byte sniffer — that lives in
 * shared:core:data, a *downstream* module (its repositories consume this
 * module's DAOs). The interface is therefore declared here and implemented
 * where both java.io and the sniffer are visible: :shared:core:data's
 * `dataJvmModule` binds it over its `sniffContainerFile` glue, and the
 * platform database modules resolve it via Koin `get()` when assembling the
 * migration chain.
 */
fun interface ContainerProbe {
    fun probe(downloadPath: String): String?
}
