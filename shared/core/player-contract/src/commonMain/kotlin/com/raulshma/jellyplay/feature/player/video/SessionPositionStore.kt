package com.raulshma.jellyplay.feature.player.video

/**
 * Narrow persistence seam for the session's resume position: the four
 * SavedStateHandle keys (item id, position, play-session id, persisted-at
 * epoch) behind read accessors, so the session can persist and restore a
 * process-death resume position without touching the handle type.
 *
 * Live since B3: the production implementation
 * (`SavedStateHandlePositionStore`, player-video) is constructed by the
 * ViewModel (which keeps the handle as a constructor parameter solely to
 * build the store) and injected into [PlaybackSession].
 *
 * Home: moved here verbatim from player-video's PlaybackSession.kt (SAME
 * package, zero consumer import churn — the PlayerLifecycleCallbacks
 * precedent) when :shared:core:test-fixtures' FakePositionStore implemented
 * it — a test-fixtures → player-video edge would have been both a first
 * core→feature dependency and a project cycle with player-video's
 * jvmTest → test-fixtures edge. The double has since moved to player-video's
 * jvmTest itself (ADR-0006 rule 4: its only consumers were that module's
 * suites); this interface stays put — same package, its single consumer.
 */
interface SessionPositionStore {
    fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long)
    fun savedItemId(): String?
    fun savedPositionMs(): Long?
    fun savedPersistedAtMs(): Long?
    fun savedPlaySessionId(): String?
}
