package com.raulshma.jellyplay.feature.book

/**
 * What a one-shot download-response probe learned about a book item — the
 * header-side format carriers, parsed: the response `Content-Type` and the
 * `Content-Disposition` filename (Jellyfin serves both on `/Items/{id}/Download`,
 * verified on 10.9–12). Either may be null.
 */
data class BookDownloadMetadata(
    val contentType: String?,
    val fileName: String?,
)

/**
 * Header probe used when the item's `Path` yielded no readable format (server
 * withheld or blanked it — measured on misconfigured/path-substituted
 * servers). Implementations issue ONE cheap request against the book's
 * download URL (ranged GET / HEAD) and report the metadata headers; they must
 * not download the body. [accessToken] rides the `Authorization: MediaBrowser`
 * header — Jellyfin 12 rejects the legacy `?api_key=` query param on data
 * endpoints with 401 (images still accept it), so the header is the only
 * reliable carrier. Null = probe failed (offline, non-2xx) — callers fall
 * through to their path-based error.
 */
fun interface BookFormatProbe {
    suspend fun probe(url: String, accessToken: String?): BookDownloadMetadata?
}

/** Offline/failure-neutral probe: always reports nothing. */
object NoopBookFormatProbe : BookFormatProbe {
    override suspend fun probe(url: String, accessToken: String?): BookDownloadMetadata? = null
}
