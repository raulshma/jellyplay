package com.raulshma.jellyplay

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.raulshma.jellyplay.core.data.playback.AndroidPipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.navigation.playbackhost.PlayerActivityArgs
import com.raulshma.jellyplay.shell.AppLockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Activity-level pins for PlayerActivity's PIN/biometric gate
 * (`redirectToLockGateIfNeeded`): the media notification opens PlayerActivity
 * by class name, so the dedicated host must itself redirect to MainActivity
 * (whose compose gate renders the lock screen) while a lock is configured and
 * the app-scoped [AppLockState] says locked. The gate also folds the shared
 * auto-lock-on-return policy, so a return that skips MainActivity — launcher
 * relaunch onto the backgrounded player, notification tap on a live instance —
 * lands on the lock screen once the configured timer elapsed while
 * backgrounded, and leaves playback restoration to the gate's decline inside
 * the window (the no-redirect fixtures finish at the args-parse branch, so
 * they pin the gate decision, not playback setup — see below). The fold
 * runs at the ACTUAL foreground return (`onResume` outside PiP, or a PiP
 * exit callback that lands RESUMED): intent delivery
 * (`onCreate`/`onNewIntent`) only re-checks the current lock state and
 * leaves the background stamp untouched, so a foregrounding that lands past
 * the timer is still measured at the resume. The PiP-exit pins drive every
 * ordering of that rule (an expired absence relocks in a RESUMED exit
 * callback; an in-window one stays behind the declining gate; a STARTED exit
 * is not the return and leaves the stamp for the following resume; a resume
 * while still DOCKED does not consume the PiP-entry stamp, so the later
 * expand still folds it), the pause pin proves a later pause cannot reset an
 * outstanding stamp, the launch-arg pins prove a delivery to a
 * not-RESUMED — or docked-but-RESUMED — instance parks its payload until a
 * QUALIFYING return passes the gate — a resume outside PiP or a RESUMED-phase
 * PiP exit — while a docked resume or a STARTED exit keeps it parked, the
 * recreation pin proves the parked contract SURVIVES a configuration
 * recreation (whose re-delivered intent IS the parked payload), and the
 * dismissal pins prove a teardown PiP dismissal — the discharge whose
 * revealed shell gets no further resume — evaluates a pending expired
 * absence instead of stranding it, while a keep-alive dismissal keeps the
 * stamp for the next genuine return.
 *
 * Primes exactly the dependencies the gate + the full onCreate path resolve
 * from Koin — a REAL [SecurityStore] over a per-test DataStore file (so the
 * persisted-slice read `firstPersistedSecurity()` is exercised, not mocked),
 * the holder, a relaxed [PlayerLifecycleManager], and a stubbed
 * [PreferenceProjections] (the recreation pin runs the whole onCreate,
 * including its STARTED warm-up collectors) — and drives
 * `onCreate`/`onNewIntent` through Robolectric's ActivityController.
 *
 * Why the no-redirect cases launch with an args-less intent: past the gate,
 * the next branch in `onCreate` finishes the activity when the intent carries
 * no item id — BEFORE `setContent`. That keeps these pins off the full
 * Compose/engine composition (none of which is Robolectric-viable) while the
 * assertion that matters — no MainActivity launch — still proves the gate
 * declined to redirect. The `isFinishing` assertions in those cases document
 * which branch finished the activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlayerActivityLockRedirectTest {

    private val application get() = RuntimeEnvironment.getApplication()

    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var securityStore: SecurityStore
    private lateinit var appLockState: AppLockState

    @Before
    fun setUp() {
        // The stamps below subtract minutes from SystemClock.elapsedRealtime()
        // (the monotonic source BOTH hosts now record and fold with), and
        // Robolectric's shadow clock starts near zero — give it runway so a
        // "past" stamp stays positive.
        ShadowSystemClock.advanceBy(Duration.ofHours(1))
        // Per-test file: a DataStore instance may only ever be bound to one
        // file per process, and Windows holds onto freshly-closed handles.
        val file = kotlin.io.path.createTempFile(
            prefix = "app_lock_gate_${System.nanoTime()}",
            suffix = ".preferences_pb",
        ).toFile()
        file.deleteOnExit()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        securityStore = SecurityStore(dataStore, storeScope)
    }

    @After
    fun tearDown() {
        stopKoin()
        storeScope.cancel()
    }

    // ── Redirects ──────────────────────────────────────────────────────────

    @Test
    fun `locked with pin gate configured redirects to MainActivity and finishes`() {
        primeKoin(unlocked = false, pinLockEnabled = true)

        val controller = playerController(itemIntent("item-1"))
        controller.create()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("no player UI may compose while locked", controller.get().isFinishing)
    }

    @Test
    fun `locked with biometric-only gate configured also redirects`() {
        primeKoin(unlocked = false, biometricLockEnabled = true)

        val controller = playerController(itemIntent("item-1"))
        controller.create()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `onNewIntent redirects when the app locks under a live singleTask instance`() {
        // The media-notification PendingIntent carries SINGLE_TOP|CLEAR_TOP
        // and PlayerActivity is singleTask: while an instance is alive
        // (backgrounded/PiP playback) a notification tap routes to
        // onNewIntent, not onCreate — the gate must cover that path too.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        // Passed the gate unlocked (no redirect); finished at the args-parse
        // branch (args-less intent), before setContent.
        assertNull(Shadows.shadowOf(application).nextStartedActivity)

        appLockState.lock()
        controller.newIntent(itemIntent("item-2"))

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `onResume re-checks the gate after the app locks under a live instance`() {
        // A live PiP window survives
        // MainActivity's auto-lock; expanding it back to fullscreen returns
        // through onResume with NO intent delivered — the gate must be
        // re-consulted there, not only at onCreate/onNewIntent.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        // Passed the gate unlocked (no redirect); args-parse finish.
        assertNull(Shadows.shadowOf(application).nextStartedActivity)

        appLockState.lock()
        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `onResume after a gate redirect does not launch a second MainActivity`() {
        // The re-check is idempotent: after onCreate redirected, the activity
        // still walks its finishing lifecycle through onResume — exactly ONE
        // handoff may fire.
        primeKoin(unlocked = false, pinLockEnabled = true)

        val controller = playerController(itemIntent("item-1"))
        controller.create()
        assertEquals(
            MainActivity::class.java.name,
            Shadows.shadowOf(application).nextStartedActivity?.component?.className,
        )

        controller.resume()

        assertNull("redirect must not double-fire on the finishing lifecycle pass", Shadows.shadowOf(application).nextStartedActivity)
    }

    // ── Auto-lock timer on returns that skip MainActivity ─────────────────

    @Test
    fun `an intent delivered past the auto-lock timer redirects on the returning resume`() {
        // Intent delivery is not the return: onCreate/onNewIntent run before
        // the foregrounding completes, and consuming the stamp there would
        // let a delayed return skip the timer. The resume folds, relocks and
        // hands off.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)
        stampBackgroundedPast(timerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()

        assertNull("intent delivery alone must not redirect", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("the stamp must survive delivery for the resume to fold", appLockState.hasPendingBackground)

        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertTrue(controller.get().isFinishing)
        assertFalse("the fold must consume the stamp", appLockState.hasPendingBackground)
    }

    @Test
    fun `a return inside the auto-lock window neither relocks nor redirects`() {
        // Gate-scoped pin only: the args-less fixture finishes at the
        // args-parse branch (see the class KDoc), so "playback restored" is
        // not observable here — what is pinned is the gate's decline and the
        // stamp consumption of the in-window fold.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)
        appLockState.onBackgrounded(SystemClock.elapsedRealtime() - 5_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.resume()

        assertNull("a return inside the timer window must not redirect", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("no relock inside the window", appLockState.unlocked.value)
        assertFalse("an in-window return consumes the stamp", appLockState.hasPendingBackground)
    }

    @Test
    fun `onResume redirects when the app was backgrounded past the auto-lock timer`() {
        // The primary gap: the launcher icon resumes a live player directly,
        // so MainActivity.onResume never folds the timer — the gate must.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        assertNull(Shadows.shadowOf(application).nextStartedActivity)

        stampBackgroundedPast(timerMs = 60_000L)
        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertFalse(appLockState.unlocked.value)
        assertFalse("the fold must consume the stamp", appLockState.hasPendingBackground)
    }

    @Test
    fun `onResume inside the auto-lock window neither relocks nor redirects`() {
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()

        appLockState.onBackgrounded(SystemClock.elapsedRealtime() - 5_000L)
        controller.resume()

        assertNull("a return inside the timer window must not hand off", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("no relock inside the window", appLockState.unlocked.value)
        assertFalse("an in-window return consumes the stamp", appLockState.hasPendingBackground)
    }

    @Test
    fun `a notification intent past the timer leaves the stamp for the returning resume`() {
        // The media-notification path on a live instance: onNewIntent arrives
        // before the actual foreground return, so it must NOT consume the
        // stamp — the resume does, and redirects.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()

        stampBackgroundedPast(timerMs = 60_000L)
        controller.newIntent(itemIntent("item-2"))

        assertNull("intent delivery alone must not redirect", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("the live instance must not relock on intent delivery alone", appLockState.unlocked.value)
        assertTrue("the stamp must survive for the returning resume", appLockState.hasPendingBackground)

        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
    }

    // ── Deferred launch-arg publish (onNewIntent while not RESUMED) ───────

    @Test
    fun `a delivery to a not-RESUMED instance parks its payload until the resume gate passes`() {
        // Delivery is not the return: publishing here would initialize/retune
        // the swapped item or channel ahead of the resume gate. The payload
        // waits for the resume that passes the gate, then publishes.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()

        controller.newIntent(itemIntent("item-2"))

        assertNull("delivery alone must not swap the media", controller.get().launchArgs.value)
        assertNotNull("the payload must be parked for the resume", controller.get().pendingLaunchArgs)

        controller.resume()

        assertEquals(
            "the passing resume publishes the parked payload",
            "item-2",
            (controller.get().launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull("publishing clears the parked payload", controller.get().pendingLaunchArgs)
    }

    @Test
    fun `a redirecting resume drops the parked payload without publishing it`() {
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        stampBackgroundedPast(timerMs = 60_000L)

        controller.newIntent(itemIntent("item-2"))
        assertNotNull("delivery parks the payload", controller.get().pendingLaunchArgs)

        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertNull("a redirected return must never publish the swap", controller.get().launchArgs.value)
    }

    @Test
    fun `a delivery to an already-RESUMED fullscreen instance publishes immediately`() {
        // Branch pin, not a fullscreen-relaunch flow: this drives only the
        // RESUMED arm of onNewIntent's publish fold — a fullscreen instance
        // that IS the foreground state publishes directly. The flow-shaped
        // pins (a delivery to a paused or docked instance parking until the
        // qualifying return passes the gate) live in the tests below.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()

        controller.newIntent(itemIntent("item-2"))

        assertEquals(
            "a fullscreen RESUMED delivery swaps immediately",
            "item-2",
            (controller.get().launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull(controller.get().pendingLaunchArgs)
    }

    @Test
    @Suppress("DEPRECATION") // the single-arg callback is the pre-O entry point the override pairs with
    fun `a delivery to a docked-but-RESUMED instance parks for the exit fold`() {
        // OEMs that keep the activity RESUMED through the whole PiP session
        // deliver intents while the lifecycle state still reads RESUMED, so
        // lifecycle state alone cannot mean foreground: a docked delivery
        // parks even at RESUMED, and the RESUMED-phase exit — the return
        // whose fold may relock — publishes it.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()

        val activity = controller.get()
        activity.enterPictureInPictureMode()
        activity.onPictureInPictureModeChanged(true)
        controller.newIntent(itemIntent("item-2"))

        assertTrue(
            "the premise: the activity is still RESUMED while docked",
            activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),
        )
        assertNull("a docked delivery must not swap the media, RESUMED or not", activity.launchArgs.value)
        assertNotNull("the payload parks for the qualifying exit", activity.pendingLaunchArgs)

        activity.onPictureInPictureModeChanged(false)

        assertEquals(
            "the RESUMED exit publishes the parked payload",
            "item-2",
            (activity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull("publishing clears the parked payload", activity.pendingLaunchArgs)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a payload parked while docked publishes on the RESUMED PiP exit fold`() {
        // A docked resume is not a qualifying return (the gate fold is
        // skipped for the same reason), so a payload parked by the delivery
        // must stay parked past it — publishing there would initialize/retune
        // the swap under the lock challenge. The RESUMED exit is the return.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()

        val activity = controller.get()
        activity.enterPictureInPictureMode()
        activity.onPictureInPictureModeChanged(true)
        controller.pause()
        controller.newIntent(itemIntent("item-2"))

        assertNull("delivery alone must not swap the media", activity.launchArgs.value)
        assertNotNull("the payload parks while docked", activity.pendingLaunchArgs)

        controller.resume()

        assertNull("a docked resume must not publish the parked payload", activity.launchArgs.value)
        assertNotNull("the payload stays parked for the qualifying exit", activity.pendingLaunchArgs)

        activity.onPictureInPictureModeChanged(false)

        assertEquals(
            "the RESUMED exit publishes the parked payload",
            "item-2",
            (activity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull("publishing clears the parked payload", activity.pendingLaunchArgs)
    }

    @Test
    @Suppress("DEPRECATION") // the single-arg callback is the pre-O entry point the override pairs with
    fun `a recreation restores the active payload and keeps the parked one behind the gate`() {
        // Real Android re-delivers the LAST intent to a recreated instance,
        // and a parked delivery already moved its extras into the intent
        // (setIntent): a parse-from-intent onCreate would publish item B — the
        // swap the user has not cleared the lock challenge for — before any
        // qualifying return. The saved payloads must restore item A as ACTIVE
        // and item B as PARKED; B publishes only when the qualifying return
        // (the RESUMED-phase PiP exit here) passes the gate. The recreated
        // controller launches with the UPDATED (item-B) intent to pin that
        // the intent parse cannot win over the restore.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val original = playerController(Intent(application, PlayerActivity::class.java))
        original.create()
        original.start()
        original.resume()

        val activity = original.get()
        original.newIntent(itemIntent("item-A")) // RESUMED fullscreen: publishes
        activity.enterPictureInPictureMode()
        activity.onPictureInPictureModeChanged(true) // docked
        original.newIntent(itemIntent("item-B")) // docked delivery: parks

        assertEquals(
            "the premise: item A is the active payload",
            "item-A",
            (activity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertEquals(
            "the premise: item B is parked",
            "item-B",
            (activity.pendingLaunchArgs as? PlayerActivityArgs.Video)?.itemId,
        )

        val outState = Bundle()
        original.saveInstanceState(outState)

        val recreated = Robolectric.buildActivity(PlayerActivity::class.java, itemIntent("item-B"))
            .create(outState)
        recreated.start()
        val recreatedActivity = recreated.get()

        assertEquals(
            "onCreate must restore the ACTIVE payload, not parse the updated intent",
            "item-A",
            (recreatedActivity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertEquals(
            "the parked payload must survive the recreation",
            "item-B",
            (recreatedActivity.pendingLaunchArgs as? PlayerActivityArgs.Video)?.itemId,
        )

        recreatedActivity.enterPictureInPictureMode()
        recreatedActivity.onPictureInPictureModeChanged(true) // the recreated instance is docked too
        recreated.resume()

        assertEquals(
            "a docked resume must not publish the parked payload",
            "item-A",
            (recreatedActivity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertEquals(
            "item-B stays parked behind the docked resume",
            "item-B",
            (recreatedActivity.pendingLaunchArgs as? PlayerActivityArgs.Video)?.itemId,
        )

        recreatedActivity.onPictureInPictureModeChanged(false) // RESUMED exit: the qualifying return

        assertEquals(
            "the qualifying return publishes the parked payload",
            "item-B",
            (recreatedActivity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull("publishing clears the parked payload", recreatedActivity.pendingLaunchArgs)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a recreation without saved payloads falls back to the intent parse`() {
        // A fresh controller created with saved state that carries no payload
        // bundles (a save from before any args landed) must degrade to the
        // plain intent parse — the fresh-start behavior.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val outState = Bundle()
        val recreated = Robolectric.buildActivity(PlayerActivity::class.java, itemIntent("item-A"))
            .create(outState)

        assertEquals(
            "no saved payload — the intent parse decides",
            "item-A",
            (recreated.get().launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull(recreated.get().pendingLaunchArgs)
    }

    @Test
    @Suppress("DEPRECATION") // the single-arg callback is the pre-O entry point the override pairs with
    fun `a payload parked before a STARTED PiP exit publishes on the following resume`() {
        // The expand-before-onResume ordering: a STARTED exit is not the
        // return, so the payload must stay parked for the resume that folds
        // the absence — the STARTED exit publishes nothing.
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()

        val activity = controller.get()
        activity.onPictureInPictureModeChanged(true)
        controller.newIntent(itemIntent("item-2"))

        assertNull("delivery alone must not swap the media", activity.launchArgs.value)
        assertNotNull("the payload parks while not RESUMED", activity.pendingLaunchArgs)

        activity.onPictureInPictureModeChanged(false) // still STARTED

        assertNull("a STARTED exit is not the return — no publish yet", activity.launchArgs.value)
        assertNotNull("the payload must survive for the returning resume", activity.pendingLaunchArgs)

        controller.resume()

        assertEquals(
            "the qualifying resume publishes the parked payload",
            "item-2",
            (activity.launchArgs.value as? PlayerActivityArgs.Video)?.itemId,
        )
        assertNull("publishing clears the parked payload", activity.pendingLaunchArgs)
    }

    @Test
    fun `onPause preserves an outstanding stamp instead of resetting the clock`() {
        // A pause INSIDE an absence already stamped (the dismissal-time
        // pause of a session that stayed RESUMED through PiP) must not move
        // the clock: were it overwritten with "now", the returning resume
        // would measure ~zero elapsed and skip the relock.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        controller.pause()
        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertFalse(appLockState.unlocked.value)
        assertFalse("the fold must consume the preserved stamp", appLockState.hasPendingBackground)
    }

    @Test
    @Suppress("DEPRECATION") // the single-arg callback is the pre-O entry point the override pairs with
    fun `a PiP expand past the auto-lock timer relocks in the exit callback`() {
        // OEMs that keep the activity RESUMED through PiP deliver no onResume
        // on expand — the exit callback itself folds the PiP-entry stamp at
        // the returning resume. The entry callback preserves the stamp
        // already pending (stamped in the past to simulate a long session),
        // so the fold still measures the whole absence.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        controller.get().onPictureInPictureModeChanged(true)
        controller.get().onPictureInPictureModeChanged(false)

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertFalse(appLockState.unlocked.value)
        assertFalse("the exit fold must consume the stamp", appLockState.hasPendingBackground)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a PiP expand inside the auto-lock window neither relocks nor redirects`() {
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        appLockState.onBackgrounded(SystemClock.elapsedRealtime() - 5_000L)

        controller.get().onPictureInPictureModeChanged(true)
        controller.get().onPictureInPictureModeChanged(false)

        assertNull("an in-window expand must not hand off", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("no relock inside the window", appLockState.unlocked.value)
        assertFalse("the exit fold must consume the stamp", appLockState.hasPendingBackground)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a PiP exit at STARTED leaves the stamp pending for the following resume`() {
        // The expand-before-onResume ordering (and a keep-alive dismissal):
        // the exit callback runs while the activity is merely STARTED, which
        // is NOT the return — it must leave the expired stamp for the resume
        // that follows, which then folds (relocks) it.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        stampBackgroundedPast(timerMs = 60_000L)

        val activity = controller.get()
        activity.onPictureInPictureModeChanged(true)
        activity.onPictureInPictureModeChanged(false)

        assertNull("a STARTED exit is not the return — no handoff yet", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("no relock at the STARTED exit", appLockState.unlocked.value)
        assertTrue("the stamp must survive for the returning resume", appLockState.hasPendingBackground)

        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertFalse(appLockState.unlocked.value)
        assertFalse("the resume fold consumes the stamp", appLockState.hasPendingBackground)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a resume while still docked in PiP leaves the stamp pending for the expand fold`() {
        // A lifecycle resume can land while the window is still DOCKED
        // (isInPictureInPictureMode stays true). That is not a foreground
        // return: consuming the PiP-entry stamp there would let a later
        // expand (past the timer) skip the challenge entirely. The stamp must
        // survive, and the RESUMED exit callback still folds it.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        val activity = controller.get()
        activity.enterPictureInPictureMode()
        activity.onPictureInPictureModeChanged(true)
        controller.pause()
        controller.resume()

        assertNull("a docked resume must not hand off", Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("no relock while docked", appLockState.unlocked.value)
        assertTrue("a docked resume must leave the stamp pending", appLockState.hasPendingBackground)

        activity.onPictureInPictureModeChanged(false)

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertFalse("the expand fold relocks once the docked window exits", appLockState.unlocked.value)
        assertFalse("the exit fold consumes the stamp", appLockState.hasPendingBackground)
    }

    @Test
    fun `a timed-out policy read relocks an unlocked return with a pending stamp`() {
        // firstPersistedSecurity() never completes (fake store): the policy
        // cannot be evaluated, so the gate must fail CLOSED on a genuine
        // return instead of letting the stale unlocked flag restore playback.
        // The untouched stamp stays for the next host that can evaluate.
        primeKoin(
            unlocked = true,
            overrideSecurityStore = SecurityStore(HangingDataStore(), storeScope),
        )
        appLockState.onBackgrounded(System.currentTimeMillis() - 5_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        // Intent delivery never folds, so the timed-out read there is inert.
        assertNull(Shadows.shadowOf(application).nextStartedActivity)
        assertTrue(appLockState.unlocked.value)

        controller.resume()

        assertShellHandoff(Shadows.shadowOf(application).nextStartedActivity)
        assertFalse("an unevaluable auto-lock policy must fail closed", appLockState.unlocked.value)
        assertTrue("the unconsumed stamp stays for the next evaluator", appLockState.hasPendingBackground)
    }

    @Test
    fun `onPause records the shared background stamp the gate fold consumes`() {
        // The player can be the task top across a backgrounding, so it must
        // feed AppLockState itself — without this stamp the gate fold could
        // never see the timer elapse on a player-only return.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.pause()

        val relocked = appLockState.onResumed(
            nowMs = SystemClock.elapsedRealtime() + 61_000L,
            gateConfigured = true,
            autoLockTimerMs = 60_000L,
        )

        assertTrue("player onPause must record the shared background stamp", relocked)
    }

    @Test
    @Suppress("DEPRECATION") // the single-arg callback is the pre-O entry point the override pairs with
    fun `PiP entry records the shared stamp when the transition never paused`() {
        // OEMs that keep the activity RESUMED through the whole PiP session
        // never deliver the entry onPause, so the entry callback itself must
        // record the absence — otherwise an expand after the timer elapsed
        // cannot relock. On the standard pause-first ordering the pending
        // pause stamp already covers the same transition.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        assertFalse(appLockState.hasPendingBackground)

        controller.get().onPictureInPictureModeChanged(true)

        assertTrue(
            "PiP entry without a pause must stamp the backgrounding itself",
            appLockState.hasPendingBackground,
        )
    }

    // ── Teardown-dismissal folds (the discharge whose shell gets no resume) ─

    @Test
    @Suppress("DEPRECATION")
    fun `a STARTED dismissal discharge evaluates a pending expired absence`() {
        // The dismissal whose exit callback arms (no keep-alive) finishes via
        // the armed onStop discharge. The shell beneath the docked window has
        // been RESUMED since entry — its docked resume never folds — and the
        // finish delivers no new MainActivity.onResume, so the exit callback
        // must leave the stamp (a STARTED exit is not the return) and the
        // DISCHARGE must evaluate the absence; otherwise browse sits
        // interactive past an expired auto-lock timer with no challenge.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        val activity = controller.get()
        activity.onPictureInPictureModeChanged(true) // entry: the expired stamp is preserved
        controller.pause()
        activity.onPictureInPictureModeChanged(false) // STARTED exit: arms, no fold yet

        assertTrue("the STARTED exit must leave the stamp for the discharge", appLockState.hasPendingBackground)
        assertTrue("no relock at the STARTED exit itself", appLockState.unlocked.value)

        controller.stop() // the armed discharge: the dismissal is confirmed — fold

        assertFalse("the teardown dismissal must evaluate the expired absence", appLockState.unlocked.value)
        assertFalse("the discharge fold consumes the stamp", appLockState.hasPendingBackground)
        assertNull(
            "the fold relocks; the shell's compose gate renders the challenge, no handoff needed",
            Shadows.shadowOf(application).nextStartedActivity,
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a below-STARTED dismissal evaluates the pending expired absence at the exit callback`() {
        // The OEM ordering where onStop runs before the exit callback: the
        // callback lands below STARTED, finishes directly, and there is no
        // later lifecycle event on either host — the callback itself is the
        // discharge, so it folds the pending absence before finishing.
        primeKoin(unlocked = true, pinLockEnabled = true, autoLockTimerMs = 60_000L)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        val activity = controller.get()
        activity.onPictureInPictureModeChanged(true) // entry: the expired stamp is preserved
        controller.pause()
        controller.stop() // this OEM stops while still docked, before the callback
        activity.onPictureInPictureModeChanged(false) // below STARTED: the discharge fold

        assertFalse("the teardown dismissal must evaluate the expired absence", appLockState.unlocked.value)
        assertFalse("the fold consumes the stamp", appLockState.hasPendingBackground)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a keep-alive dismissal leaves the absence pending for the next genuine return`() {
        // Background audio keeps the player (and the listening session) alive
        // behind the revealed shell: the absence is not over, so neither the
        // exit callback (no arm, no below-STARTED finish) nor the stop folds —
        // the stamp stays for the notification-relaunch / app-re-entry return
        // that still folds it (ADR 0018's keep-alive residual).
        primeKoin(
            unlocked = true,
            pinLockEnabled = true,
            autoLockTimerMs = 60_000L,
            overrideLifecycleManager = mockk(relaxed = true) {
                every { isBackgroundAudioEnabled } returns true
            },
        )

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        val activity = controller.get()
        activity.onPictureInPictureModeChanged(true) // entry: the expired stamp is preserved
        controller.pause()
        activity.onPictureInPictureModeChanged(false) // keep-alive dismissal: no arm, no fold
        controller.stop() // unarmed stop: no discharge fold

        assertTrue("the session continues — the stamp stays pending", appLockState.hasPendingBackground)
        assertTrue("no relock for a keep-alive dismissal", appLockState.unlocked.value)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `an armed stop whose keep-alive re-check flipped true keeps the stamp for the next return`() {
        // The dismissal armed at the exit callback (background audio OFF
        // there), but by onStop the audio setting reads ON — the policy's
        // fresh keepPlayerAlive read CANCELS the teardown (Action.None: the
        // session continues under the shell). The discharge fold must follow
        // the policy's verdict, not the arm alone: the absence is not over,
        // so the stamp stays for the next genuine return exactly like the
        // never-armed keep-alive dismissal. The item-id intent keeps the
        // activity NOT finishing (the args-less fixtures finish at the
        // args-parse branch, which would discharge through the isFinishing
        // arm and mask the keep-alive case).
        primeKoin(
            unlocked = true,
            pinLockEnabled = true,
            autoLockTimerMs = 60_000L,
            overrideLifecycleManager = mockk(relaxed = true) {
                every { isBackgroundAudioEnabled } returnsMany listOf(false, true)
            },
        )

        val controller = playerController(itemIntent("item-1"))
        controller.create()
        controller.start()
        controller.resume()
        stampBackgroundedPast(timerMs = 60_000L)

        val activity = controller.get()
        activity.onPictureInPictureModeChanged(true) // entry: the expired stamp is preserved
        controller.pause()
        activity.onPictureInPictureModeChanged(false) // STARTED exit: keep-alive false → arms

        assertTrue("the premise: the exit armed the dismissal", activity.justExitedPip)

        controller.stop() // armed, but the keep-alive re-read cancels teardown: no fold, no finish

        assertTrue("keep-alive cancelled the teardown — no relock", appLockState.unlocked.value)
        assertTrue("the stamp stays for the next genuine return", appLockState.hasPendingBackground)
        assertNull("no handoff and no finish — the session continues", Shadows.shadowOf(application).nextStartedActivity)
        assertFalse("the armed stop did not finish", controller.get().isFinishing)
    }

    // ── Non-redirects ──────────────────────────────────────────────────────

    @Test
    fun `an unlocked return with no pending stamp skips the policy read`() {
        // For an unlocked holder with no pending stamp the fold is provably
        // a no-op under either read outcome (shouldRelock and the fail-closed
        // arm both require a stamp) and shouldRedirect can never fire, so the
        // gate must not even resolve the store — the unstubbed mock fails the
        // test the moment the read path is touched. The unlocked-with-stamp
        // case still reads (the timed-out pin above); locked holders always
        // read for the gate configuration.
        primeKoin(unlocked = true, overrideSecurityStore = mockk())

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()
        controller.resume()

        assertNull(Shadows.shadowOf(application).nextStartedActivity)
        assertTrue("still unlocked — the fold had nothing to fold", appLockState.unlocked.value)
        assertFalse(appLockState.hasPendingBackground)
        // Finished by the args-parse branch (no item id), not by a redirect.
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `unlocked with gate configured proceeds without redirect`() {
        primeKoin(unlocked = true, pinLockEnabled = true)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()

        assertNull("unlocked app must NOT be redirected", Shadows.shadowOf(application).nextStartedActivity)
        // Finished by the args-parse branch (no item id), not by a redirect.
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `locked with no gate configured never redirects`() {
        // Locked flag is irrelevant when no gate is configured — matches
        // MainActivity, which shows no lock screen either.
        primeKoin(unlocked = false, pinLockEnabled = false)

        val controller = playerController(Intent(application, PlayerActivity::class.java))
        controller.create()

        assertNull(Shadows.shadowOf(application).nextStartedActivity)
        // Again the args-parse finish; the gate let the activity proceed.
        assertTrue(controller.get().isFinishing)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * The gate's shell-handoff contract: MainActivity by class AND the
     * CLEAR_TOP|SINGLE_TOP pair. MainActivity is the task's singleTop root, so
     * without the flags this handoff would stack a second shell above the
     * finishing player instead of routing to the existing instance.
     */
    private fun assertShellHandoff(next: Intent?) {
        assertNotNull("locked app must hand off to the lock gate", next)
        assertEquals(MainActivity::class.java.name, next!!.component?.className)
        val reuseFlags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(
            "lock-gate handoff must carry CLEAR_TOP|SINGLE_TOP so it reuses the existing shell",
            reuseFlags,
            next.flags and reuseFlags,
        )
    }

    /** Starts Koin with the singles the gate + onCreate path resolve, optionally seeding the persisted gate keys. */
    private fun primeKoin(
        unlocked: Boolean,
        pinLockEnabled: Boolean = false,
        biometricLockEnabled: Boolean = false,
        autoLockTimerMs: Long? = null,
        overrideSecurityStore: SecurityStore? = null,
        overrideLifecycleManager: PlayerLifecycleManager? = null,
    ) {
        appLockState = AppLockState().apply { if (unlocked) unlock() }
        if (overrideSecurityStore == null &&
            (pinLockEnabled || biometricLockEnabled || autoLockTimerMs != null)
        ) {
            runBlocking {
                dataStore.edit { prefs ->
                    // Key literals match SecurityStore.Keys ("pin_lock_enabled"
                    // / "biometric_lock_enabled" / "auto_lock_timer_ms"); the
                    // typed keys are internal.
                    if (pinLockEnabled) prefs[booleanPreferencesKey("pin_lock_enabled")] = true
                    if (biometricLockEnabled) prefs[booleanPreferencesKey("biometric_lock_enabled")] = true
                    if (autoLockTimerMs != null) prefs[longPreferencesKey("auto_lock_timer_ms")] = autoLockTimerMs
                }
            }
        }
        startKoin {
            modules(
                module {
                    single { appLockState }
                    single { overrideSecurityStore ?: securityStore }
                    // onResume's player hook resolves this lazily even on the
                    // finishing-lifecycle pass (the no-redirect / already
                    // redirected cases) — a relaxed stub keeps the harness at
                    // exactly-the-singles-the-path-touches. The keep-alive
                    // dismissal pins override it to report background audio ON.
                    single<PlayerLifecycleManager> { overrideLifecycleManager ?: mockk(relaxed = true) }
                    // The PiP-entry pin drives onPipModeChanged directly; its
                    // tail feeds this shared singleton.
                    single { AndroidPipController() }
                    // Only the full-onCreate path (the recreation pin — a
                    // recreated instance with restored args runs onCreate to
                    // the end, including its STARTED warm-up collectors)
                    // resolves this; real MutableStateFlows keep those
                    // collectors alive without the 19-store graph.
                    single {
                        mockk<PreferenceProjections> {
                            every { mainPreferences } returns MutableStateFlow(MainPreferences())
                            every { playbackPreferences } returns MutableStateFlow(PlaybackPreferences())
                        }
                    }
                },
            )
        }
    }

    /** Stamps a backgrounding safely past the configured timer, relative to the monotonic clock the gate folds with. */
    private fun stampBackgroundedPast(timerMs: Long) {
        appLockState.onBackgrounded(SystemClock.elapsedRealtime() - timerMs - 60_000L)
    }

    /**
     * A DataStore whose reads never complete — pins the gate's fail-closed
     * branch for a timed-out `firstPersistedSecurity()`.
     */
    private class HangingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { awaitCancellation() }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            error("read-only test store")
    }

    private fun playerController(intent: Intent) =
        Robolectric.buildActivity(PlayerActivity::class.java, intent)

    /** A media-notification-shaped intent: PlayerActivity by class + item id extra. */
    private fun itemIntent(itemId: String): Intent =
        Intent(application, PlayerActivity::class.java)
            .putExtra(PlayerActivityArgs.EXTRA_ITEM_ID, itemId)
}
