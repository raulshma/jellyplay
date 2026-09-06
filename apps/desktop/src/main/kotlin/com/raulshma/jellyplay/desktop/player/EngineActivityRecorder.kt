package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_NO_OP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wave 13B session-harness instrumentation: records what every engine the
 * [DesktopMpvPlayerEngineFactory] creates actually DID — playback-state
 * transitions, isPlaying observations and sampled playhead positions — on the
 * recorder's own SupervisorJob scope, without touching the shared modules.
 *
 * Pure observation: it collects from the engine's existing flows/properties
 * and never calls into the engine, so it cannot perturb a session. Evidence is
 * read as immutable [EngineActivitySnapshot]s (see that type for the pure
 * classification helpers the harness asserts with and the unit tests cover).
 *
 * Koin single (DesktopPlayerModule); app-lifetime, like the factory it serves.
 * Per-engine observers are CANCELLED on engine release (CONC-1, 2026-09 audit
 * — they used to run for the process lifetime, so N released engines kept N×3
 * coroutines waking 2×/s against dead JNA handles): the desktop factory wires
 * every engine it owns into [recordReleased], which cancels the three observer
 * jobs while KEEPING the record itself (accumulated evidence is the point —
 * only the live observation stops). Engines without a desktop release hook
 * (the shared no-op EXTERNAL engine) keep the old always-observe behavior;
 * their reads are pure state (no JNA) and the sample caps still bound memory.
 */
class EngineActivityRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val records = CopyOnWriteArrayList<MutableRecord>()

    /**
     * Live per-engine observers, engine instance → its record. An entry exists
     * between [recordCreated] and [recordReleased] (the engine's release());
     * the RECORD itself always stays in [records] for snapshot history, so
     * cancelling observation never destroys evidence. Keyed by engine
     * identity — engines do not override equals, so the map's equals-based
     * lookups are reference comparisons.
     */
    private val observers = ConcurrentHashMap<MediaEngine, MutableRecord>()

    /** True when at least one engine was recorded (cheap poll guard). */
    val hasAnyEngine: Boolean get() = records.isNotEmpty()

    /**
     * Called by the factory right after it constructed an engine. Spawns the
     * observers; returns immediately (never blocks engine creation).
     *
     * @param surface which factory branch created the engine
     *   ([EngineActivitySnapshot.SURFACE_HWND] etc.).
     */
    fun recordCreated(engine: MediaEngine, surface: String) {
        val record = MutableRecord(
            displayName = engine.displayName,
            surface = surface,
            createdAtMs = System.currentTimeMillis(),
        )
        records.add(record)

        record.jobs = listOf(
            scope.launch {
                var last: String? = null
                engine.playbackState.collect { state ->
                    // StateFlow replays the current value first; dedupe so the
                    // transition list holds real transitions only.
                    val name = state.name
                    if (name != last) {
                        last = name
                        record.addTransition(name)
                    }
                }
            },
            scope.launch {
                var last: Boolean? = null
                engine.isPlaying.collect { playing ->
                    if (playing != last) {
                        last = playing
                        if (playing) record.markPlayingObserved()
                    }
                }
            },
            scope.launch {
                while (isActive) {
                    delay(SAMPLE_INTERVAL_MS)
                    record.addSample(
                        EngineActivitySnapshot.PositionSample(
                            atMs = System.currentTimeMillis(),
                            positionMs = engine.currentPositionMs,
                            isPlaying = engine.isPlaying.value,
                        ),
                    )
                }
            },
        )
        observers[engine] = record
    }

    /**
     * Stops the per-engine observers for a released engine (CONC-1): cancels
     * the three [recordCreated] jobs — the 500 ms position sampler and the two
     * flow collectors — while keeping everything recorded up to the release
     * point (post-release samples would read a dead handle anyway). Idempotent
     * by removal semantics; a call for an engine that was never recorded (or
     * already released) is a no-op.
     */
    fun recordReleased(engine: MediaEngine) {
        observers.remove(engine)?.cancelObservers()
    }

    /** Latest engine's snapshot, or [EngineActivitySnapshot.NONE]. */
    fun latest(): EngineActivitySnapshot = records.lastOrNull()?.snapshot() ?: EngineActivitySnapshot.NONE

    /**
     * Latest snapshot for a REAL playback engine (anything but the
     * EXTERNAL no-op) — the video session's evidence, ignoring incidental
     * no-op creations.
     */
    fun latestVideoEngine(): EngineActivitySnapshot =
        records.lastOrNull { it.surface != SURFACE_NO_OP }?.snapshot() ?: EngineActivitySnapshot.NONE

    /** App-lifetime type; the cancel exists for tests and completeness. */
    fun dispose() {
        scope.cancel()
    }

    /** Mutable accumulator; snapshot() hands out an immutable copy. */
    private class MutableRecord(
        val displayName: String,
        val surface: String,
        val createdAtMs: Long,
    ) {
        private val transitions = ArrayList<EngineActivitySnapshot.StateTransition>()
        private val samples = ArrayList<EngineActivitySnapshot.PositionSample>()
        private var playingObserved = false

        /**
         * The record's three observer jobs (state collector, isPlaying
         * collector, position sampler), set by recordCreated right after the
         * launches. @Volatile: release (and thus recordReleased) can race the
         * assignment from another thread — a cancel that sees the empty
         * pre-assignment default only leaks to the pre-CONC-1 behavior.
         */
        @Volatile var jobs: List<Job> = emptyList()

        fun cancelObservers() {
            jobs.forEach { it.cancel() }
        }

        fun addTransition(toState: String) = synchronized(this) {
            if (transitions.size < MAX_TRANSITIONS) {
                transitions += EngineActivitySnapshot.StateTransition(System.currentTimeMillis(), toState)
            }
        }

        fun markPlayingObserved() = synchronized(this) { playingObserved = true }

        fun addSample(sample: EngineActivitySnapshot.PositionSample) = synchronized(this) {
            if (samples.size < MAX_SAMPLES) samples += sample
        }

        fun snapshot(): EngineActivitySnapshot = synchronized(this) {
            EngineActivitySnapshot(
                displayName = displayName,
                surface = surface,
                createdAtMs = createdAtMs,
                transitions = transitions.toList(),
                isPlayingObserved = playingObserved,
                positionSamples = samples.toList(),
            )
        }
    }

    private companion object {
        /** ~2 samples/s — fine enough to see a 12 s clip advance + a pause. */
        const val SAMPLE_INTERVAL_MS = 500L

        /** Caps keep a forgotten session from growing unbounded (≈5 min). */
        const val MAX_SAMPLES = 600
        const val MAX_TRANSITIONS = 200
    }
}
