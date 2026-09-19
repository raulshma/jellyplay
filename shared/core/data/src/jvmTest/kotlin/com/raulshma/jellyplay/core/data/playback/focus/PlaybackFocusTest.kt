package com.raulshma.jellyplay.core.data.playback.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pins [PlaybackFocusMatrix] (the who-pauses-whom table) and
 * [DefaultPlaybackFocus] (the phase machine + synchronous dispatch) at the
 [PlaybackFocus] interface. These rows ARE the cross-player exclusivity
 * policy — the thing that used to be smeared across engine configs, a
 * lifecycle module, prefs plumbing and per-shell code.
 */
class PlaybackFocusTest {

    private class FakeArbiter(var granted: Boolean = true) : FocusArbiter {
        /** Every (attributes, listener) pair handed to the OS seat, in order. */
        val requests = mutableListOf<Pair<FocusAudioAttributes, FocusListener>>()
        var abandons = 0
        override fun request(attributes: FocusAudioAttributes, listener: FocusListener): Boolean {
            requests.add(attributes to listener)
            return granted
        }
        override fun abandon() {
            abandons++
        }
        fun loseTransient() = requests.last().second.onFocusEvent(FocusEvent.LostTransient)
        fun losePermanent() = requests.last().second.onFocusEvent(FocusEvent.LostPermanent)
        fun regain() = requests.last().second.onFocusEvent(FocusEvent.Regained)
    }

    private class FakeSurface(override val id: PlaybackSurfaceId) : PlaybackSurface {
        var pauses = 0
        override fun pause() {
            pauses++
        }
    }

    private fun focus(arbiter: FakeArbiter, music: FakeSurface): DefaultPlaybackFocus =
        DefaultPlaybackFocus(arbiter = arbiter, surfaces = listOf(music))

    // ------------------------------------------------------------------
    // Matrix rows
    // ------------------------------------------------------------------

    @Test
    fun `matrix rows pin slice one`() {
        assertEquals(listOf(PlaybackSurfaceId.MUSIC), PlaybackFocusMatrix.victimsOf(PlaybackSurfaceId.READ_ALOUD))
        assertTrue(PlaybackFocusMatrix.victimsOf(PlaybackSurfaceId.MUSIC).isEmpty())
        assertTrue(PlaybackFocusMatrix.isGrantable(PlaybackSurfaceId.READ_ALOUD))
        assertTrue(PlaybackFocusMatrix.isGrantable(PlaybackSurfaceId.MUSIC))
        assertTrue(!PlaybackFocusMatrix.isGrantable(PlaybackSurfaceId.VIDEO), "slice-1 closed world denies video")
    }

    @Test
    fun `matrix attributes pin slice two - read-aloud keeps the slice-one speech pair`() {
        // Zero-behavior-change pin: the pair AndroidFocusArbiter previously
        // hardcoded (USAGE_MEDIA + CONTENT_TYPE_SPEECH) is now the READ_ALOUD
        // row of the decision table, nothing else.
        assertEquals(
            FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.SPEECH),
            PlaybackFocusMatrix.attributesOf(PlaybackSurfaceId.READ_ALOUD),
        )
        // The rows the migration slice will claim under: music must land on
        // MUSIC attributes (never speech — OS ducking policy reads the
        // content type), video on MOVIE.
        assertEquals(
            FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MUSIC),
            PlaybackFocusMatrix.attributesOf(PlaybackSurfaceId.MUSIC),
        )
        assertEquals(
            FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MOVIE),
            PlaybackFocusMatrix.attributesOf(PlaybackSurfaceId.VIDEO),
        )
    }

    @Test
    fun `os seat request carries the claimant attributes`() {
        val arbiter = FakeArbiter()
        val focus = focus(arbiter, FakeSurface(PlaybackSurfaceId.MUSIC))
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        assertEquals(1, arbiter.requests.size)
        assertEquals(
            FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.SPEECH),
            arbiter.requests.single().first,
            "the executor derives seat attributes from the matrix, not adapter defaults",
        )
    }

    // ------------------------------------------------------------------
    // TTS-over-music
    // ------------------------------------------------------------------

    @Test
    fun `read-aloud claim pauses music BEFORE returning granted`() {
        val arbiter = FakeArbiter(granted = true)
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)

        val outcome = focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        assertEquals(FocusOutcome.Granted, outcome)
        assertEquals(1, music.pauses, "the victim pause is synchronous with the claim")
        assertIs<FocusClaimState.Held>(focus.claimState.value).let {
            assertEquals(PlaybackSurfaceId.READ_ALOUD, it.holder)
        }
    }

    @Test
    fun `refused OS focus denies the claim and touches no victim`() {
        val arbiter = FakeArbiter(granted = false)
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)

        val outcome = focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        assertEquals(FocusOutcome.Denied, outcome)
        assertEquals(0, music.pauses, "a denied claim must not pause anyone")
        assertEquals(FocusClaimState.Idle, focus.claimState.value)
    }

    @Test
    fun `re-acquire while held is idempotent - no OS churn, no re-pause`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        val outcome = focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        assertEquals(FocusOutcome.Granted, outcome)
        assertEquals(1, arbiter.requests.size, "sentence-to-sentence re-claims are free")
        assertEquals(1, music.pauses)
    }

    @Test
    fun `user-paused read-aloud keeps the claim`() {
        val arbiter = FakeArbiter()
        val focus = focus(arbiter, FakeSurface(PlaybackSurfaceId.MUSIC))
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)
        // (The reader VM pauses the speech LOOP without releasing — that
        // contract lives on the reader side; here we pin that a release is
        // the ONLY path to Idle.)
        assertEquals(PlaybackSurfaceId.READ_ALOUD, (focus.claimState.value as FocusClaimState.Held).holder)
    }

    // ------------------------------------------------------------------
    // Newest-wins (music during read-aloud)
    // ------------------------------------------------------------------

    @Test
    fun `music claim during read-aloud wins the floor`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        val outcome = focus.acquire(PlaybackSurfaceId.MUSIC)

        assertEquals(FocusOutcome.Granted, outcome)
        val held = assertIs<FocusClaimState.Held>(focus.claimState.value)
        assertEquals(PlaybackSurfaceId.MUSIC, held.holder)
        assertEquals(1, music.pauses, "only the ORIGINAL read-aloud claim paused music, not this one")
        // Migration slice: MUSIC now takes the OS seat too (handleAudioFocus
        // is off on the players — the module owns the whole story), with its
        // own attributes row. Request 1 = READ_ALOUD speech seat, request 2 =
        // MUSIC (the READ_ALOUD seat was abandoned first — pinned below).
        assertEquals(2, arbiter.requests.size)
        assertEquals(
            FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MUSIC),
            arbiter.requests.last().first,
            "music's seat carries the MUSIC attributes row, never speech",
        )
    }

    @Test
    fun `evicting a held os-seat holder abandons its seat`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        focus.acquire(PlaybackSurfaceId.MUSIC)

        assertEquals(1, arbiter.abandons, "the displaced read-aloud's OS seat dies with its claim")

        // The displaced holder's late release is a no-op: music holds the
        // floor, and the seat was already abandoned at eviction.
        focus.release(PlaybackSurfaceId.READ_ALOUD)
        assertEquals(1, arbiter.abandons)
        assertIs<FocusClaimState.Held>(focus.claimState.value).let {
            assertEquals(PlaybackSurfaceId.MUSIC, it.holder)
        }
    }

    // ------------------------------------------------------------------
    // OS losses, suspension, release
    // ------------------------------------------------------------------

    @Test
    fun `os transient loss suspends and a regain never auto-resumes`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)
        // music.pauses == 1 here — the read-aloud CLAIM's victim pause. The
        // suspension below must not add to it.
        val pausesAfterClaim = music.pauses

        arbiter.loseTransient()
        val suspended = assertIs<FocusClaimState.Suspended>(focus.claimState.value)
        assertEquals(PlaybackSurfaceId.READ_ALOUD, suspended.holder)
        assertEquals(FocusLossReason.Transient, suspended.reason)
        assertEquals(pausesAfterClaim, music.pauses, "a suspended READ_ALOUD is NEVER commanded — the reader observes the state and pauses its own loop")

        arbiter.regain()
        val still = focus.claimState.value
        assertIs<FocusClaimState.Suspended>(still)
        assertEquals(FocusLossReason.Transient, (still as FocusClaimState.Suspended).reason)
        assertEquals(pausesAfterClaim, music.pauses)
    }

    @Test
    fun `suspended claimant re-acquires onto the same seat`() {
        val arbiter = FakeArbiter()
        val focus = focus(arbiter, FakeSurface(PlaybackSurfaceId.MUSIC))
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)
        arbiter.losePermanent()

        assertEquals(FocusOutcome.Granted, focus.acquire(PlaybackSurfaceId.READ_ALOUD))
        assertEquals(2, arbiter.requests.size, "resume re-requests the OS seat")
        assertIs<FocusClaimState.Held>(focus.claimState.value)
    }

    @Test
    fun `release abandons the OS seat and idles - release of a non-holder is a no-op`() {
        val arbiter = FakeArbiter()
        val focus = focus(arbiter, FakeSurface(PlaybackSurfaceId.MUSIC))
        focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        focus.release(PlaybackSurfaceId.MUSIC) // wrong claimant
        assertEquals(PlaybackSurfaceId.READ_ALOUD, (focus.claimState.value as FocusClaimState.Held).holder)
        assertEquals(0, arbiter.abandons)

        focus.release(PlaybackSurfaceId.READ_ALOUD)
        assertEquals(FocusClaimState.Idle, focus.claimState.value)
        assertEquals(1, arbiter.abandons)

        focus.release(PlaybackSurfaceId.READ_ALOUD) // idempotent
        assertEquals(FocusClaimState.Idle, focus.claimState.value)
        assertEquals(1, arbiter.abandons)
    }

    // ------------------------------------------------------------------
    // Music OS-leg migration (ADR-0004): the holder enforcement leg
    // ------------------------------------------------------------------

    @Test
    fun `music takes the os seat with its own attributes`() {
        val arbiter = FakeArbiter()
        val focus = focus(arbiter, FakeSurface(PlaybackSurfaceId.MUSIC))

        focus.acquire(PlaybackSurfaceId.MUSIC)

        assertEquals(1, arbiter.requests.size, "music's OS leg lives in the module now — handleAudioFocus is off on the players")
        assertEquals(
            FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MUSIC),
            arbiter.requests.single().first,
        )
    }

    @Test
    fun `os loss on the music holder suspends it AND commands its surface pause`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.MUSIC)

        arbiter.loseTransient()

        val suspended = assertIs<FocusClaimState.Suspended>(focus.claimState.value)
        assertEquals(PlaybackSurfaceId.MUSIC, suspended.holder)
        assertEquals(FocusLossReason.Transient, suspended.reason)
        assertEquals(1, music.pauses, "the enforcement leg: a holder has no claimState observer — without the commanded pause it would keep playing unfocused")

        arbiter.losePermanent()
        // A second loss event after suspension is dropped (only a Held can
        // suspend): no duplicate command.
        assertEquals(1, music.pauses)
    }

    @Test
    fun `permanent loss suspends the music holder and commands its pause too`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.MUSIC)

        arbiter.losePermanent()

        val suspended = assertIs<FocusClaimState.Suspended>(focus.claimState.value)
        assertEquals(FocusLossReason.Permanent, suspended.reason)
        assertEquals(1, music.pauses)
    }

    @Test
    fun `regain does not auto-resume the suspended music holder`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.MUSIC)
        arbiter.loseTransient()

        arbiter.regain()

        assertIs<FocusClaimState.Suspended>(focus.claimState.value)
        assertEquals(1, music.pauses, "resume is manual — a regain commands nothing and restarts nothing")
        assertEquals(1, arbiter.requests.size, "a regain never re-requests the seat either")
    }

    @Test
    fun `user resume after a music suspension re-acquires the seat`() {
        val arbiter = FakeArbiter()
        val music = FakeSurface(PlaybackSurfaceId.MUSIC)
        val focus = focus(arbiter, music)
        focus.acquire(PlaybackSurfaceId.MUSIC)
        arbiter.loseTransient()

        val outcome = focus.acquire(PlaybackSurfaceId.MUSIC)

        assertEquals(FocusOutcome.Granted, outcome)
        assertIs<FocusClaimState.Held>(focus.claimState.value).let {
            assertEquals(PlaybackSurfaceId.MUSIC, it.holder)
        }
        assertEquals(2, arbiter.requests.size, "the user's resume is the way back — it re-requests the OS seat")
    }

    @Test
    fun `video claims are denied by the slice-one closed world`() {
        val arbiter = FakeArbiter()
        val focus = focus(arbiter, FakeSurface(PlaybackSurfaceId.MUSIC))
        assertEquals(FocusOutcome.Denied, focus.acquire(PlaybackSurfaceId.VIDEO))
        assertEquals(FocusClaimState.Idle, focus.claimState.value)
    }
}
