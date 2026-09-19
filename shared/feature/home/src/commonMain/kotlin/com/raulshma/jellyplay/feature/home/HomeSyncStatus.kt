package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Web seam over core:data's jvmShared `SyncStatusStateHolder`
 * (+ its Factory) — the home screen's pending-sync surface: outbox badge
 * count, sync-details sheet rows, per-row resolved metadata, the manual
 * drain trigger and the offline→online drain gate. The holder's constructor
 * closure is the JVM sync stack (WorkManager scheduler, ConcurrentHashMap
 * mirrors), so commonMain cannot name the class. The interface carries
 * exactly the holder's consumer-facing surface, the jvmShared factory
 * adapter builds the REAL holder via the real `SyncStatusStateHolderFactory`
 * (android/desktop behavior unchanged — the sheet, badge and drain flows
 * are the holder's own), and the wasmJs factory builds an honestly idle
 * holder.
 *
 * Web behavior: the browser has no offline playback outbox (no download
 * pipeline exists to queue watch-progress events), so the wasm holder keeps
 * the count at 0, the entries/details empty, [syncNow] inert and
 * [awaitOutboxDrained] trivially true (there is genuinely nothing pending —
 * never a fabricated pending set).
 */
interface HomeSyncStatus {

    /** Count of playback events queued in the offline outbox (header badge). */
    val pendingSyncCount: StateFlow<Int>

    /** Pending outbox rows (oldest-first) for the sync details sheet. */
    val pendingSyncEntries: StateFlow<List<PlaybackOutboxEntry>>

    /** Per-row resolved metadata keyed by outbox itemId (sheet rows). */
    val pendingItemDetails: StateFlow<Map<String, ResolvedMediaRef>>

    /**
     * Ensures [pendingItemDetails] holds a resolution for every id in
     * [itemIds], pruning stale entries — cheap to call on every recomposition.
     */
    fun ensurePendingItemDetails(itemIds: Collection<String>)

    /** Manually drains the playback outbox (no-op while offline). */
    fun syncNow()

    /**
     * Waits for the outbox to drain before the going-online fetch. Returns
     * `false` on timeout — the caller proceeds anyway and knows its fetch
     * raced a still-pending sync.
     */
    suspend fun awaitOutboxDrained(): Boolean
}

/**
 * Construction seam mirroring core:data's `SyncStatusStateHolderFactory`:
 * owns the pure-DI collaborators so they never surface on the home
 * ViewModel's constructor. [create] takes only the consumer-owned runtime
 * inputs (the VM's scope and the shared offline-mode manager).
 */
fun interface HomeSyncStatusFactory {

    fun create(scope: CoroutineScope, offlineModeManager: OfflineModeManager): HomeSyncStatus
}

/**
 * Web seam over core:data's jvmShared `NewsletterTriggerManager`
 * — the one read the home banner makes (is this week's newsletter issue due?).
 * The manager computes weekday windows through java.time, so commonMain
 * cannot name the class. NewsletterBanner gates on the single flow;
 * the jvmShared actual delegates to the real manager (android/desktop
 * unchanged), and the wasmJs actual never shows the banner (the web shell
 * has no newsletter notification pipeline — genuinely false, not a
 * fabricated due-state).
 */
fun interface HomeNewsletterGate {

    /** Whether the newsletter banner should show (weekday-window logic). */
    fun shouldShowBanner(): Flow<Boolean>
}
