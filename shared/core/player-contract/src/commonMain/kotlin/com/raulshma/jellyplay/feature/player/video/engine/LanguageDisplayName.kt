package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Resolves a BCP-47 language tag ("en", "en-US", "eng") to its display name
 * ("English") for [TrackLabelFormatter] track labels. Returns `null` when the
 * platform cannot resolve it — the caller then falls back to the raw tag.
 *
 * expect/actual seam added with the wasmJs target (Phase W.3):
 * TrackLabelFormatter previously used `java.util.Locale` directly in
 * commonMain, which was legal while this module only shipped android+jvm
 * (V2a note) but breaks once wasmJs joins.
 */
internal expect fun platformLanguageDisplayName(bcp47Tag: String): String?
