package com.raulshma.jellyplay.feature.book

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the Windows SAPI binding's SHUTDOWN ORDERING and released-guard
 * semantics ([SapiTtsBinding] — formerly the module's one completely
 * untested class): acquire → use → release → post-release no-op, the
 * use-after-free guard (a poll Callable queued behind the shutdown task must
 * short-circuit on `released` instead of touching the freed voice), the
 * exactly-once completion callback, the poll loop's slice interval, and the
 * init-failure fold.
 *
 * The COM apartment is faked with a MANUAL single-task-at-a-time executor —
 * the test drains its queue explicitly, so the poll-vs-shutdown queue
 * ordering the `released` flag exists for is deterministic. The voice is the
 * [SapiVoiceHandle] seam (recording fake; a WAIT-AFTER-RELEASE marker makes
 * any post-release touch fail loudly). Both test seams carry production
 * defaults in the binding (real COM object, 100 ms slice).
 */
class SapiTtsBindingTest {

    private val apartment = ManualApartment()
    private val voice = FakeVoice()
    private val host = FakeHost()
    private var factoryCalls = 0
    private val binding = SapiTtsBinding(
        apartment = apartment,
        voiceFactory = { factoryCalls++; voice },
        pollIntervalMs = 7,
    )

    @AfterTest
    fun tearDown() {
        // Release any waiter thread the test left parked on a poll future.
        binding.shutdown()
        apartment.runAll()
    }

    @Test
    fun `init failure folds to one onInit false and the binding stays dead`() {
        val failing = SapiTtsBinding(apartment, voiceFactory = { error("no SAPI") }, pollIntervalMs = 7)
        failing.host = host

        failing.ensureStarted()

        assertEquals(listOf(false), host.inits)
        assertFalse(failing.isDefaultLanguageUsable())
        // Commands are no-ops: no waiter was ever created, nothing queued.
        failing.speak("hello", "u1")
        failing.configure(150, 100)
        failing.stopPlatform()
        assertEquals(0, voice.eventCount)
        assertTrue(apartment.pendingCount() == 0)
    }

    @Test
    fun `init success reports connected and the voice answers probes`() {
        binding.host = host
        binding.ensureStarted()

        assertEquals(listOf(true), host.inits)
        assertTrue(binding.isDefaultLanguageUsable())
        // Idempotent: a second start must not re-create the voice.
        binding.ensureStarted()
        assertEquals(1, factoryCalls)
        assertEquals(1, host.inits.size)
    }

    @Test
    fun `utterance polls the apartment in slices until done then completes once`() {
        binding.host = host
        binding.ensureStarted()

        binding.speak("hello", "u1")
        assertEquals(listOf("speak:hello"), voice.events())

        // Two undrained slices, then the utterance finishes.
        awaitQueuedPoll()
        apartment.runNext() // poll 1 → false
        awaitQueuedPoll()
        apartment.runNext() // poll 2 → false
        voice.done = true
        drainUntil { host.doneUtt == listOf("u1") }

        // Every poll asked the voice with the configured slice interval.
        assertTrue(voice.waitTimeouts.isNotEmpty() && voice.waitTimeouts.all { it == 7 })
        // Exactly one completion, posted back through the apartment.
        assertEquals(1, host.doneUtt.size)
        assertEquals("u1", host.doneUtt.single())
        assertEquals(0, host.errors.size)
    }

    @Test
    fun `configure maps the percent band onto sapi's log-scaled rate`() {
        binding.host = host
        binding.ensureStarted()

        binding.configure(200, 100) // double speed → +10
        binding.configure(100, 100) // normal → 0
        binding.configure(50, 100)  // half speed → −10
        binding.configure(25, 100)  // coerced into the band first → −10

        assertEquals(listOf("rate:10", "rate:0", "rate:-10", "rate:-10"), voice.events())
    }

    @Test
    fun `stop purge speaks the empty utterance`() {
        binding.host = host
        binding.ensureStarted()

        binding.stopPlatform()

        assertEquals(listOf("speak:"), voice.events())
    }

    @Test
    fun `a poll queued behind shutdown sees released and never touches the freed voice`() {
        binding.host = host
        binding.ensureStarted()

        binding.speak("hello", "u1")
        awaitQueuedPoll()
        // Queue the shutdown BEHIND the pending poll — the exact queue shape
        // the released-guard exists for (production: machine.shutdown posts
        // to the same apartment thread the polls run on).
        apartment.execute { binding.shutdown() }

        // Poll 1 runs first (voice still live) and reports not-done.
        apartment.runNext()
        // Shutdown runs second: waiter stopped, released set, voice released.
        apartment.runNext()
        assertEquals(1, voice.releaseCount)

        // Drain everything the teardown left queued: any poll behind the
        // shutdown must short-circuit on `released` — no further wait call —
        // and the stranded utterance still completes exactly once (the
        // machine's id guard makes it inert above this seam).
        drainUntil { host.doneUtt == listOf("u1") }
        apartment.runAll()

        assertEquals(
            listOf("speak:hello", "wait:7", "release"),
            voice.events(),
        )
        assertFalse(voice.events().any { it == WAIT_AFTER_RELEASE })
        assertEquals(1, voice.releaseCount)
        assertEquals(1, host.doneUtt.size)
        assertEquals("u1", host.doneUtt.single())
    }

    @Test
    fun `post-release commands are no-ops and the binding reports unusable`() {
        binding.host = host
        binding.ensureStarted()
        apartment.execute { binding.shutdown() }
        apartment.runNext()

        binding.speak("more", "u2")
        binding.configure(150, 100)
        binding.stopPlatform()
        binding.shutdown() // second shutdown: still exactly one release

        assertEquals(listOf("release"), voice.events())
        assertFalse(binding.isDefaultLanguageUsable())
        assertEquals(0, host.doneUtt.size)
    }

    // -----------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------

    private fun awaitQueuedPoll() {
        val deadline = System.currentTimeMillis() + 5_000
        while (apartment.pendingCount() == 0) {
            check(System.currentTimeMillis() < deadline) {
                "the waiter never submitted a poll; events=${voice.events()}"
            }
            Thread.sleep(2)
        }
    }

    private fun drainUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            apartment.runAll()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) {
                "condition never met; voice=${voice.events()} hostDone=${host.doneUtt}"
            }
            Thread.sleep(2)
        }
    }

    /** The waiter thread submits; the TEST thread drains — full interleaving control. */
    private class ManualApartment : AbstractExecutorService() {
        private val lock = Any()
        private val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            synchronized(lock) { queue.addLast(command) }
        }

        fun pendingCount(): Int = synchronized(lock) { queue.size }

        /** Pops and runs the next queued task on the CALLING thread; false when empty. */
        fun runNext(): Boolean {
            val task: Runnable = synchronized(lock) { queue.removeFirstOrNull() } ?: return false
            task.run()
            return true
        }

        fun runAll() {
            while (runNext()) { /* drain */ }
        }

        // Lifecycle is unused by the binding under test (it only execute/submit()s).
        override fun shutdown() {}
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean = true
    }

    private class FakeVoice : SapiVoiceHandle {
        private val lock = Any()
        private val log = mutableListOf<String>()
        var releaseCount = 0
            private set
        private var released = false

        /** What waitUntilDone answers (the "utterance still playing" flip). */
        @Volatile
        var done: Boolean = false

        /** Every WaitUntilDone timeout argument, in order. */
        val waitTimeouts: List<Int>
            get() = synchronized(lock) { log.filter { it.startsWith("wait:") }.map { it.substringAfter(':').toInt() } }

        fun events(): List<String> = synchronized(lock) { log.toList() }

        val eventCount: Int
            get() = synchronized(lock) { log.size }

        override fun speakAsync(text: String) {
            synchronized(lock) { log += "speak:$text" }
        }

        override fun setRate(rate: Int) {
            synchronized(lock) { log += "rate:$rate" }
        }

        override fun waitUntilDone(timeoutMs: Int): Boolean {
            synchronized(lock) {
                log += if (released) WAIT_AFTER_RELEASE else "wait:$timeoutMs"
            }
            return done
        }

        override fun release() {
            synchronized(lock) {
                releaseCount++
                log += "release"
                released = true
            }
        }
    }

    private class FakeHost : TtsBinding.Host {
        val inits = mutableListOf<Boolean>()
        val doneUtt = mutableListOf<String?>()
        val errors = mutableListOf<String?>()

        override fun onInit(connected: Boolean) {
            inits += connected
        }

        override fun onUtteranceDone(utteranceId: String?) {
            doneUtt += utteranceId
        }

        override fun onUtteranceError(utteranceId: String?) {
            errors += utteranceId
        }
    }

    private companion object {
        /** Marker for the forbidden post-release wait — never allowed in the log. */
        const val WAIT_AFTER_RELEASE = "WAIT-AFTER-RELEASE"
    }
}
