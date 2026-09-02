package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Converts [url] into a form mpv's stream layer can actually open.
 *
 * `File.toURI()` produces single-slash `"file:/data/…"` URIs, and mpv's file
 * stream handler only strips the scheme for the `"file://…"` form — the
 * single-slash form is opened literally as a relative path and fails with
 * ENOENT (observed on-device: a freshly downloaded side-load's `sub-add`
 * succeeded but its demuxer open failed, so the track never materialized,
 * while ExoPlayer opened the identical URI fine). Returns the decoded
 * absolute path for file URIs; bare paths and every other scheme
 * (`content://`, `http(s)://`) pass through unchanged. Top-level for
 * unit-testability without an engine.
 *
 * Lives in commonMain (moved verbatim out of the androidMain engine) so the
 * pure JVM logic is unit-testable from jvmTest: both this module's targets
 * are JVM-based, so `java.net.URI` is commonMain-legal (see the
 * `fileUriString` seam in PlayerSessionManager.kt for the same rationale).
 */
internal fun mpvOpenableUrl(url: String): String =
    if (url.startsWith("file:")) {
        try {
            java.net.URI(url).path?.takeIf { it.isNotEmpty() } ?: url
        } catch (_: Exception) {
            url
        }
    } else {
        url
    }
