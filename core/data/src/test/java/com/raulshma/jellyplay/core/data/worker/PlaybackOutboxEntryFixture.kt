package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.model.PlayMethod

/**
 * The outbox entry shape the worker suites (`PlaybackSyncWorkerResilienceTest`,
 * `OfflineWatchSyncContractTest`) build identically — one definition so the
 * fixture defaults cannot drift apart. Package-level on purpose: call sites
 * resolve to it after their private copies were removed. The drain-policy
 * lane's twin lives in :shared:core:data's jvmTest (same package/name) —
 * keep the two in sync.
 */
internal fun entry(
    id: String,
    itemId: String,
    type: PlaybackOutboxEventType,
    sessionId: String = "s1",
    positionTicks: Long = 100L,
) = PlaybackOutboxEntry(
    id = id,
    itemId = itemId,
    eventType = type,
    sessionId = sessionId,
    positionTicks = positionTicks,
    isPaused = false,
    playMethod = PlayMethod.DIRECT_PLAY,
    mediaSourceId = null,
    recordedAt = 1_000L,
    createdAt = 1_000L,
)
