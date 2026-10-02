package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaType

/**
 * The read-only view of a resolved snapshot an enrichment declaration gates
 * and runs on — the (itemId × mediaType × origin × capabilities) tuple the
 * declarations' decision tables read. Constructed once per new-resolution
 * reduction by the VM's evaluator; [MediaDetailSnapshot] stays the single
 * source (no copying, just named projections).
 */
internal class DetailEnrichmentInputs(
    val itemId: String,
    val snapshot: MediaDetailSnapshot,
) {
    val detail: MediaDetail = snapshot.detail

    val mediaType: MediaType get() = detail.item.mediaType

    val isRemote: Boolean get() = snapshot.context.origin == DetailOrigin.REMOTE

    val capabilities: DetailCapabilities get() = snapshot.capabilities

    /**
     * The shared remote gate: discovery-shaped side effects run for a REMOTE
     * origin AND only when the capability flip allows discovery at all —
     * the same two terms [DetailViewModel]'s former
     * `triggerRemoteSideEffects` early-return checked, now stated per
     * declaration instead of once around a hand-launched block.
     */
    val remoteDiscoveryAllowed: Boolean
        get() = isRemote && capabilities.remoteDiscovery
}

/**
 * One declared enrichment of the detail load path: a GATE over the resolved
 * snapshot (mediaType × origin × capability) plus the suspend side-effect it
 * fires. The load path's fan-out is DATA with this type — the VM's evaluator
 * walks its declaration list in order and launches each gated entry, so a
 * test can pin "snapshot of type X with capabilities Y fires E1..En and not
 * the others" without driving every repository.
 *
 * [gate] reads ONLY [DetailEnrichmentInputs] (no VM state) — the declaration
 * table must stay decidable from the resolved snapshot. Bodies keep their
 * own [DetailLoadGuard] re-checks at suspension points exactly as before;
 * the guard stays the single admission seam, the gate only decides what may
 * START.
 *
 * [name] is diagnostic: tests assert on the declaration list by position,
 * crash logs by name.
 */
internal class DetailEnrichment(
    val name: String,
    val gate: (DetailEnrichmentInputs) -> Boolean,
    val run: suspend (DetailEnrichmentInputs) -> Unit,
)
