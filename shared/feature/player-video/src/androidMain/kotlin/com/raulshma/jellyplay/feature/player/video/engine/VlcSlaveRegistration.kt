package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The generation-guarded poll machine for external-subtitle registration
 * (LibVlcPlayerEngine's slave dance): the serialized begin → tick → advance
 * state over plain values, so the pairing/budget/queue/guard decisions are
 * JVM-pinnable without libVLC (the [TimePosDedupe] tested-holder precedent).
 *
 * libVLC offers no handle linking an `addSlave` call to the spu track it
 * produces, so the registration identifies the new track by diffing the spu
 * list against the pre-add baseline, polling until it surfaces (the slave
 * parses asynchronously — local file or remote fetch) or the attempt budget
 * runs out. Adds are chained one-at-a-time — two overlapping pre-add
 * snapshots could each claim the other's track and stamp the wrong stable id,
 * silently activating the wrong subtitle on "Use". The generation guard
 * aborts a poll scheduled under an older media generation, so a late tick can
 * never register a stale diff against the NEXT media's track ids.
 *
 * The engine owns the transport: reading spu ids off the player (fresh set
 * per tick), the id→source-id registry write, the track republish and the log
 * lines — it executes the [SlaveTickVerdict]s tick returns. Poll scheduling
 * rides the [schedulePoll] seam (the engine's handler postDelayed).
 */
internal class VlcSlaveRegistration(
    private val attemptsPerSlave: Int,
    private val schedulePoll: (generation: Int) -> Unit,
) {
    private class Pending(
        val sourceId: String,
        val knownSpuIds: Set<Int>,
        var attemptsLeft: Int,
    )

    private var pending: Pending? = null
    private val queuedSourceIds = ArrayDeque<String>()

    /** False while a registration chain is mid-flight — further adds queue behind it. */
    val acceptsBegin: Boolean
        get() = pending == null

    /** Queues a source id behind the in-flight chain (its add already ran). */
    fun enqueue(sourceId: String) {
        queuedSourceIds.addLast(sourceId)
    }

    /**
     * Starts the registration chain for one slave. [knownSpuIds] is the spu
     * id snapshot taken as this diff window opens — the add itself already
     * ran in the engine, but the track materializes asynchronously so the
     * pre-add list is still the correct baseline. Schedules the first poll
     * under [generation].
     */
    fun begin(sourceId: String, knownSpuIds: Set<Int>, generation: Int) {
        pending = Pending(sourceId, knownSpuIds, attemptsPerSlave)
        schedulePoll(generation)
    }

    /**
     * One serialized tick. [generation] is the generation this poll was
     * scheduled under and [currentGeneration] the engine's live one;
     * [spuTrackIds] is the fresh spu id list and [claimedTrackIds] the ids
     * already registered for earlier slaves. Returns the verdict for the
     * engine to execute — the reschedule on remaining budget is handled here.
     */
    fun tick(
        generation: Int,
        currentGeneration: Int,
        spuTrackIds: Set<Int>,
        claimedTrackIds: Set<Int>,
    ): SlaveTickVerdict {
        val pending = pending ?: return SlaveTickVerdict.Abandoned
        if (generation != currentGeneration) return SlaveTickVerdict.Abandoned
        val addedTrackId = spuTrackIds.firstOrNull { it !in pending.knownSpuIds && it !in claimedTrackIds }
        if (addedTrackId != null) {
            return SlaveTickVerdict.Register(vlcTrackId = addedTrackId, sourceId = pending.sourceId)
        }
        pending.attemptsLeft--
        if (pending.attemptsLeft <= 0) {
            return SlaveTickVerdict.Exhausted(pending.sourceId)
        }
        schedulePoll(generation)
        return SlaveTickVerdict.PollRescheduled
    }

    /**
     * Retires the current registration and starts the next queued one, if
     * any. [knownSpuIds] is the fresh baseline snapshot for the next slave's
     * diff window — the engine re-reads it at retire time, the same moment
     * the pre-extraction body did.
     */
    fun advance(knownSpuIds: Set<Int>, generation: Int) {
        pending = null
        val nextSourceId = queuedSourceIds.removeFirstOrNull() ?: return
        begin(nextSourceId, knownSpuIds, generation)
    }

    /** Clears the chain — pending registration and queue (media teardown). */
    fun reset() {
        pending = null
        queuedSourceIds.clear()
    }
}

/** The engine-executed outcome of one [VlcSlaveRegistration.tick]. */
internal sealed interface SlaveTickVerdict {
    /**
     * A fresh spu track paired with the pending source id — the engine
     * stamps its registry, republishes tracks, then calls
     * [VlcSlaveRegistration.advance].
     */
    data class Register(val vlcTrackId: Int, val sourceId: String) : SlaveTickVerdict

    /** The attempt budget ran out — the engine logs, then calls advance. */
    data class Exhausted(val sourceId: String) : SlaveTickVerdict

    /** Budget remaining — the holder re-scheduled the poll itself. */
    data object PollRescheduled : SlaveTickVerdict

    /** No pending registration, or a stale generation — nothing to do. */
    data object Abandoned : SlaveTickVerdict
}
