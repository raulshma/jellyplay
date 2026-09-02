package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Recovers the stable side-loaded subtitle configuration id from a prepared
 * track's `Format.id` (androidMain Media3 shape).
 *
 * The raw format id is NOT the configuration id: Media3's MergingMediaSource
 * prefixes every side-loaded child source's ids with the child index
 * (`"{n}:{id}"`) to keep the merged namespace unique — observed on-device as
 * `SubtitleSource.id = "provider:WYZIE:x"` surfacing as
 * `"3:provider:WYZIE:x"`. Exact-match resolution against `SubtitleSource.id`
 * (the "Use"-activation ladder, the `"external:{index}"` restore contract, the
 * subtitle-sync preview) therefore fails unless the prefix is stripped.
 *
 * The merge prefix is only stripped when the suffix matches one of
 * [configIds] — the engine's own live subtitle configurations — so unrelated
 * container-demuxed formats that happen to share the `{n}:{m}` shape pass
 * through untouched. Top-level for unit-testability without an engine.
 *
 * Lives in commonMain (moved verbatim out of the androidMain engine) so the
 * pure string logic is unit-testable from jvmTest.
 */
internal fun resolveStableSideloadedTrackId(formatId: String?, configIds: Set<String>): String? {
    val raw = formatId?.takeIf { it.isNotBlank() } ?: return null
    if (raw in configIds) return raw
    val suffix = raw.substringAfter(':', missingDelimiterValue = "")
    return suffix.takeIf { it.isNotEmpty() && it in configIds } ?: raw
}
