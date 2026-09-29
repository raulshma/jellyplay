package com.raulshma.jellyplay.core.testfixtures

import com.raulshma.jellyplay.feature.player.video.SessionPositionStore

/**
 * The one recording [SessionPositionStore] double — the merge of the
 * two per-test-file private copies (PlaybackSessionLifecycleTest's served-
 * values variant and PlaybackSessionReportingTest's always-null variant,
 * byte-identical apart from the saved getters' bodies).
 *
 * Persists are recorded in [persists] (order-preserving); the saved-value
 * getters serve the settable `saved*Value` fields, defaulting to `null` —
 * which IS the reporting suite's former shape, so its tests adopt this fake
 * with zero setup while the lifecycle suite sets only the fields its resume
 * path reads.
 */
class FakePositionStore : SessionPositionStore {

    data class PersistCall(
        val itemId: String,
        val positionMs: Long,
        val playSessionId: String,
        val nowMs: Long,
    )

    val persists = mutableListOf<PersistCall>()
    var savedItemIdValue: String? = null
    var savedPositionMsValue: Long? = null
    var savedPersistedAtValue: Long? = null
    var savedPlaySessionIdValue: String? = null

    override fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long) {
        persists += PersistCall(itemId, positionMs, playSessionId, nowMs)
    }

    override fun savedItemId(): String? = savedItemIdValue
    override fun savedPositionMs(): Long? = savedPositionMsValue
    override fun savedPersistedAtMs(): Long? = savedPersistedAtValue
    override fun savedPlaySessionId(): String? = savedPlaySessionIdValue
}