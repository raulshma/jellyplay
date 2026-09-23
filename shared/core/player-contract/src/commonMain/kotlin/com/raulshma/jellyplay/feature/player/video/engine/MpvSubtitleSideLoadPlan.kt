package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Pure plan for side-loading subtitles onto mpv via `sub-add` — the decision
 * half of the two mpv hosts' side-load choreography (Android's former
 * `dedupeRuntimeSideLoad`/`dedupePendingSideLoad`/`registerSideLoadedLabel`
 * gates; the desktop had none of it — a bare sub-add loop with synthetic ids).
 * Engines execute the returned [SubAdd] rows through their one-method
 * `sub-add` seam and store back the returned registry.
 *
 * The registry maps the uniquified `title` (the label passed to `sub-add` and
 * echoed back as the track-list's `title`) to the caller's
 * [SubtitleSource.id]; [MpvTrackCatalog.mediaTracks] consumes it to stamp the
 * stable id onto the resulting [MediaTrack] so the id-resolution contract in
 * `TrackSelectionPolicy` (`offline:{index}` / `external:{index}` /
 * `provider:` / `local:`) resolves on BOTH hosts.
 */
object MpvSubtitleSideLoadPlan {

    /** Force-activate the track (`sub-add <url> select ...`). */
    const val FLAG_SELECT = "select"

    /** Leave selection to mpv's slang/sub-auto heuristics. */
    const val FLAG_AUTO = "auto"

    /**
     * One executable `sub-add`: [label] is the uniquified `title` arg AND the
     * registry key (they stay in lockstep by construction); [flags] is
     * [FLAG_SELECT] when the source is the user's explicit pick or flagged
     * isDefault (the source asking for the track to be shown — mpv's auto
     * heuristic drops a side-loaded track with no language and no slang
     * match), else [FLAG_AUTO].
     */
    data class SubAdd(
        val source: SubtitleSource,
        val label: String,
        val flags: String,
    )

    /**
     * The load-time batch plan (`START_FILE`'s pending-subtitle flush).
     * Dedupe is by live track-list label only — an id-based skip here would
     * drop EVERY batch entry, because the registry is pre-seeded from the
     * same batch below (the "before sub-add runs" stamping contract).
     */
    data class BatchPlan(
        val adds: List<SubAdd>,
        /**
         * The registry to store back: the incoming registry, plus the raw
         * label pre-seed for every source (so [MpvTrackCatalog] can stamp ids
         * the moment the tracks appear), plus the uniquified registrations.
         */
        val registry: Map<String, String>,
    )

    /**
     * Plans the whole pending batch against [existingLabels] (the live
     * track-list's subtitle labels — blank/unknown reads yield an empty set
     * so the caller proceeds to add).
     *
     * The raw-label pre-seed is last-wins: two batch sources sharing a title
     * stamp the added (first) track with the *later* source's id. Carried
     * forward deliberately from the pre-catalog `associate {}` behavior —
     * same-titled batch sidecars are a latent edge, not a solved one.
     */
    fun planBatch(
        sources: List<SubtitleSource>,
        existingLabels: Set<String>,
        registry: Map<String, String>,
    ): BatchPlan {
        // Raw-label pre-seed first: buildTracks may enumerate a sub-add'd
        // track before its uniquified registration would land, and a raw
        // (non-colliding) label is exactly what the track's `title` echoes.
        var reg = registry
        sources.forEach { source ->
            if (source.id.isNotBlank()) reg = reg + (source.label to source.id)
        }
        val usedLabels = existingLabels.toMutableSet()
        val adds = mutableListOf<SubAdd>()
        sources.forEach { source ->
            if (source.label in usedLabels) return@forEach   // true duplicate re-add
            val label = uniquify(source, usedLabels)
            reg = register(reg, source, label)
            adds += SubAdd(source, label, if (source.isDefault) FLAG_SELECT else FLAG_AUTO)
        }
        return BatchPlan(adds, reg)
    }

    /** A runtime add decision: skip the true re-add, or execute one add. */
    sealed interface RuntimeAdd {
        data class Skip(val reason: String) : RuntimeAdd
        data class Add(val add: SubAdd, val registry: Map<String, String>) : RuntimeAdd
    }

    /**
     * Plans a single user-initiated add (`addExternalSubtitle`). True re-adds
     * are skipped — by registered id when the source supplies one, by live
     * track-list label otherwise (the id check cannot fire for legacy id-less
     * sources; an id-carrying source with a colliding label is a DIFFERENT
     * subtitle and gets uniquified instead of skipped). Runtime adds are the
     * user's explicit pick, so they always carry [FLAG_SELECT].
     */
    fun planRuntimeAdd(
        source: SubtitleSource,
        existingLabels: Set<String>,
        registry: Map<String, String>,
    ): RuntimeAdd {
        if (source.id.isNotBlank()) {
            if (registry.containsValue(source.id)) {
                return RuntimeAdd.Skip("id already registered: id=${source.id}")
            }
        } else if (source.label in existingLabels) {
            return RuntimeAdd.Skip("already in track-list: label=${source.label}")
        }
        val usedLabels = existingLabels.toMutableSet()
        val label = uniquify(source, usedLabels)
        return RuntimeAdd.Add(
            SubAdd(source, label, FLAG_SELECT),
            registry = register(registry, source, label),
        )
    }

    /**
     * Same-label-but-different-source subs are NOT skipped: their label is
     * uniquified ("Label (2)", …) — skipping them made the second sidecar of
     * a same-titled pair permanently unselectable (it never entered the
     * track-list, so neither its side-loaded id nor any other resolution key
     * existed).
     */
    private fun uniquify(source: SubtitleSource, usedLabels: MutableSet<String>): String {
        var label = source.label.ifBlank { "External subtitle" }
        if (label in usedLabels) {
            var n = 2
            while ("$label ($n)" in usedLabels) n++
            label = "$label ($n)"
        }
        usedLabels += label
        return label
    }

    private fun register(
        registry: Map<String, String>,
        source: SubtitleSource,
        uniquifiedLabel: String,
    ): Map<String, String> =
        if (source.id.isNotBlank()) registry + (uniquifiedLabel to source.id) else registry
}
