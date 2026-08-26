package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import io.mockk.every
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Stubs [SyncPlayManager.events] with an empty, never-emitting [MutableSharedFlow].
 *
 * SyncPlayBridge starts an events collector in the ViewModel `<init>`; leaving
 * `events` unstubbed lets the relaxed-mock SharedFlow leak an uncaught exception
 * into sibling test classes under parallel execution.
 */
fun SyncPlayManager.stubEmptyEvents() {
    every { events } returns MutableSharedFlow()
}
