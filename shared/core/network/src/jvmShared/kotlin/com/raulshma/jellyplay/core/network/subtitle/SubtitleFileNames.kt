package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult

private val FILENAME_UNSAFE_CHARS_REGEX = Regex("[^A-Za-z0-9._-]")

/**
 * Fallback file name for a search result the provider did not name:
 * the release name with filesystem-unsafe characters replaced, else
 * "subtitle", plus the format (default "srt") as extension.
 */
internal fun defaultSubtitleFileName(result: SubtitleSearchResult): String {
    val ext = result.format?.lowercase()?.let { if (it.isBlank()) "srt" else it } ?: "srt"
    val base = result.releaseName?.takeIf { it.isNotBlank() }?.replace(FILENAME_UNSAFE_CHARS_REGEX, "_")
        ?: "subtitle"
    return "$base.$ext"
}
