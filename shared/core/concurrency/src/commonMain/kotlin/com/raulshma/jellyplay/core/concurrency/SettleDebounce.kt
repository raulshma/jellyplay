package com.raulshma.jellyplay.core.concurrency

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * The two phases of one upstream value in [settleDebounce]. [Arrived] is
 * emitted the instant the value lands; [Settled] only after the upstream has
 * stayed quiet for the settle window — so a burst of values (D-pad focus
 * sweeping a row) produces one [Arrived] per value but a [Settled] for the
 * LAST one alone.
 */
public sealed interface Settlement<out T> {
    /** The upstream value this settlement describes. */
    public val value: T

    /** The value as it arrived — emit cheap, in-memory reactions here. */
    public data class Arrived<T>(override val value: T) : Settlement<T>

    /** The value after the settle window elapsed undisturbed — the phase for anything with a cost (fetches, decodes). */
    public data class Settled<T>(override val value: T) : Settlement<T>
}

/**
 * Settle-debounce: every upstream value is emitted as
 * [Settlement.Arrived][Settlement.Arrived] immediately, then re-emitted as
 * [Settlement.Settled][Settlement.Settled] once no new value has arrived for
 * [settleMillis]. A value arriving mid-window cancels the pending settlement
 * (the [transformLatest] block is abandoned) and restarts the clock for the
 * new value — the plain [kotlinx.coroutines.flow.debounce] behaviour, plus
 * the immediate arrival signal for the zero-cost part of the reaction.
 *
 * Built for focus-driven UI (H10/H13): a card's BlurHash paints on
 * [Settlement.Arrived], the network-backed fetch waits for
 * [Settlement.Settled] and is cancelled for free when the focus moves on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <T> Flow<T>.settleDebounce(settleMillis: Long): Flow<Settlement<T>> =
    transformLatest { value ->
        emit(Settlement.Arrived(value))
        delay(settleMillis)
        emit(Settlement.Settled(value))
    }
