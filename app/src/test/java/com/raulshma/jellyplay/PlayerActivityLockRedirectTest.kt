package com.raulshma.jellyplay

import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.navigation.playbackhost.PlayerActivityArgs
import com.raulshma.jellyplay.shell.AppLockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * Wave 20E — activity-level pins for PlayerActivity's PIN/biometric gate
 * (`redirectToLockGateIfNeeded`): the media notification opens PlayerActivity
 * by class name, so the dedicated host must itself redirect to MainActivity
 * (whose compose gate renders the lock screen) while a lock is configured and
 * the app-scoped [AppLockState] says locked.
 *
 * Primes exactly the two dependencies the gate resolves from Koin — a REAL
 * [SecurityStore] over a per-test DataStore file (so the persisted-slice read
 * `firstPersistedSecurity()` is exercised, not mocked) and the holder — and
 * drives `onCreate`/`onNewIntent` through Robolectric's ActivityController.
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

        val next = Shadows.shadowOf(application).nextStartedActivity
        assertNotNull("locked app must hand off to the lock gate", next)
        assertEquals(MainActivity::class.java.name, next?.component?.className)
        assertTrue("no player UI may compose while locked", controller.get().isFinishing)
    }

    @Test
    fun `locked with biometric-only gate configured also redirects`() {
        primeKoin(unlocked = false, biometricLockEnabled = true)

        val controller = playerController(itemIntent("item-1"))
        controller.create()

        assertEquals(
            MainActivity::class.java.name,
            Shadows.shadowOf(application).nextStartedActivity?.component?.className,
        )
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

        assertEquals(
            MainActivity::class.java.name,
            Shadows.shadowOf(application).nextStartedActivity?.component?.className,
        )
    }

    // ── Non-redirects ──────────────────────────────────────────────────────

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

    /** Starts Koin with exactly the two singles the gate resolves, optionally seeding the persisted gate keys. */
    private fun primeKoin(
        unlocked: Boolean,
        pinLockEnabled: Boolean = false,
        biometricLockEnabled: Boolean = false,
    ) {
        appLockState = AppLockState().apply { if (unlocked) unlock() }
        if (pinLockEnabled || biometricLockEnabled) {
            runBlocking {
                dataStore.edit { prefs ->
                    // Key literals match SecurityStore.Keys ("pin_lock_enabled"
                    // / "biometric_lock_enabled"); the typed keys are internal.
                    if (pinLockEnabled) prefs[booleanPreferencesKey("pin_lock_enabled")] = true
                    if (biometricLockEnabled) prefs[booleanPreferencesKey("biometric_lock_enabled")] = true
                }
            }
        }
        startKoin {
            modules(
                module {
                    single { appLockState }
                    single { securityStore }
                },
            )
        }
    }

    private fun playerController(intent: Intent) =
        Robolectric.buildActivity(PlayerActivity::class.java, intent)

    /** A media-notification-shaped intent: PlayerActivity by class + item id extra. */
    private fun itemIntent(itemId: String): Intent =
        Intent(application, PlayerActivity::class.java)
            .putExtra(PlayerActivityArgs.EXTRA_ITEM_ID, itemId)
}
