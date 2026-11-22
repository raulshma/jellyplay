package com.raulshma.jellyplay

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowCompat
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AndroidPipController
import com.raulshma.jellyplay.core.data.playback.ShellAudioSurface
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.distribution.DistributionPlatform
import com.raulshma.jellyplay.core.ui.util.LocalNetworkAccess
import com.raulshma.jellyplay.core.ui.components.JellyPlayPreferenceTheme
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.di.KoinViewModelFactory
import com.raulshma.jellyplay.deeplink.IncomingIntentDisposition
import com.raulshma.jellyplay.deeplink.IncomingIntentRequest
import com.raulshma.jellyplay.feature.shell.LaunchPhase
import com.raulshma.jellyplay.navigation.JellyPlayApp
import com.raulshma.jellyplay.shell.AppLockRedirect
import com.raulshma.jellyplay.shell.AppLockReturn
import com.raulshma.jellyplay.shell.AppLockState
import com.raulshma.jellyplay.shell.LockGateHost
import com.raulshma.jellyplay.shell.PinGateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.mp.KoinPlatform
import java.util.concurrent.atomic.AtomicBoolean

// FragmentActivity is load-bearing: androidx.biometric.BiometricPrompt hosts its
// dialog in a support fragment, so AuthChallengeScreen's findFragmentActivity()
// resolves the prompt host from this activity. A plain ComponentActivity returns
// null there and leaves fingerprint-only users on an empty (black) lock screen.
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels { KoinViewModelFactory }

    // Cross-cutting shell infrastructure, resolved from the Koin container
    // instead of re-exported through MainViewModel — the ViewModel exposes
    // only the signals it owns. The bundle below is the composition's ONE
    // coordinator resolution path too: the shell coordinators ride it as
    // lazy providers (no standalone lazy fields, no extra threading through
    // JellyPlayApp → MainContent); MainViewModel keeps its constructor
    // injection only for the start-on-scope side effects. Every ShellInfra
    // member is a Lazy PROVIDER (mirroring audioPlaybackManagerLazy below) so
    // MainActivity.onCreate constructs none of them — JellyPlayApp resolves
    // the shared UserMessageBus through the bundle at its first composition and
    // the network status flow / remote-control pair inside their
    // composition branches and post-frame collection effects, keeping
    // NetworkMonitor's
    // connectivity-callback registration and the remote-control objects off
    // the cold-start critical path (and out of auth/onboarding-only
    // sessions entirely).
    private val pinRateLimiter: PinRateLimiter by lazy { KoinPlatform.getKoin()!!.get() }
    private val securityStore: SecurityStore by lazy { KoinPlatform.getKoin()!!.get() }
    private val networkMonitor: NetworkMonitor by lazy { KoinPlatform.getKoin()!!.get() }
    private val remoteNavigationBridgeLazy: kotlin.Lazy<RemoteNavigationBridge> =
        lazy { KoinPlatform.getKoin()!!.get() }
    private val remoteControlReceiverLazy: kotlin.Lazy<RemoteControlReceiver> =
        lazy { KoinPlatform.getKoin()!!.get() }
    // Lazy deferral is load-bearing: the playback engine (AudioPlaybackManager
    // and its 14-dep graph) stays unbuilt for auth/onboarding-only sessions —
    // resolved only inside ShellInfra's authenticated branch (JellyPlayApp).
    // Typed to the shell's narrow ShellAudioSurface — the Koin alias over the
    // same single — so the shell composition never pulls the concrete class.
    private val audioPlaybackManagerLazy: kotlin.Lazy<ShellAudioSurface> =
        lazy { KoinPlatform.getKoin()!!.get<ShellAudioSurface>() }

    // App-scoped lock flag: PlayerActivity's redirect check (the
    // media-notification class-name-PendingIntent bypass) reads the SAME
    // flag this gate renders. Same resolution pattern as the shell infra
    // above; same default (locked) and the same call sites flip it. One
    // deliberate property: the flag survives this activity's recreate() —
    // pre-Android-13 language change — instead of re-locking mid-session;
    // see AppLockState KDoc.
    private val appLockState: AppLockState by lazy { KoinPlatform.getKoin()!!.get() }

    // The shared PiP state owner — the SAME Koin single PlayerActivity drives
    // (resolved as the concrete class, like PlayerActivity, for pipSession).
    // onResume consults it to tell a genuine return from a resume that lands
    // beneath the player's still-docked PiP window (ADR 0018, Decision 5),
    // which must leave the PiP-entry stamp pending for the expand fold; onStop
    // additionally records the dock session this activity backgrounded under
    // so a later docked resume can still be recognised as a genuine return
    // (see dockedSessionAtBackgroundStop).
    private val pipController: AndroidPipController
        by lazy { KoinPlatform.getKoin()!!.get<AndroidPipController>() }

    // The dock-session id (AndroidPipController.pipSession) that was current
    // at this activity's last onStop which ran while the player window was
    // docked — `0` when no such stop was recorded. PiP state alone cannot
    // distinguish the entry reveal (exempt, Decision 5) from a LATER return
    // from outside the app with the window still floating (a genuine return
    // whose auto-lock fold must run): this record can. A resume while docked
    // is genuine only when the record matches the CURRENT session — the shell
    // left the foreground during THAT session — so a record left over from an
    // earlier session cannot exempt-consume a newer session's entry stamp.
    // One value, overwritten by each onStop; stale values are harmless (a
    // mismatch keeps the exemption, and the fold still no-ops without a
    // pending stamp).
    private var dockedSessionAtBackgroundStop = 0

    // The PIN-attempt command fold (click-time lockout re-check, off-main
    // verify, failure/success accounting) — composed here from the same Koin
    // singletons it uses.
    private val pinGateController: PinGateController by lazy {
        PinGateController(
            rateLimiter = pinRateLimiter,
            appLockState = appLockState,
            verifyPin = { pin -> securityStore.verifyPinOffMainThread(pin) },
        )
    }

    // Flipped by the cast-init coroutine kicked off in onCreate just before
    // setContent: whether the distribution's cast pre-init succeeded
    // (store: the one-time GMS CastContext warm; fdroid: always false).
    // Held as plain mutableStateOf — NOT remember-cached at the read site —
    // so the hidden media-route button in setContent composes the moment it
    // flips true (a frame or two after the first composition), and its
    // setUpMediaRouteButton resolves the cached CastContext instead of
    // re-triggering the Dynamite dex load.
    private var castPreinitialized by mutableStateOf(false)

    // One-shot guard for the reportFullyDrawn() call above the splash-gate
    // collector in onCreate. Per-instance (a locale recreate() gets a fresh
    // one, which is correct — the recreated activity has its own launch).
    private val fullyDrawnReported = AtomicBoolean(false)

    // Latched by the composition's gate branch: `true` once the shell's gate
    // decision has rendered a USABLE surface — LockScreen or Browse, never the
    // fail-closed Blocked box. The fully-drawn report waits on this beside the
    // session-restore phase, because with a configured lock the composition
    // can still be Blocked (persisted security slice unread) when the restore
    // completes — reporting there would measure the neutral box as full
    // display. Latched: a later re-block (snapshot invalidation) must not
    // matter — the app HAS displayed usable content by then.
    private val shellGateUsable = MutableStateFlow(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Android 17+ blocks local network access (LAN Jellyfin servers + SSDP/DLNA
    // discovery) unless ACCESS_LOCAL_NETWORK is granted. Requested once on cold
    // start so returning users — who never see the Add Server screen — still get
    // prompted. On grant, the self-healing WebSocket/health monitor reconnect on
    // their own. Mirrors the notification permission launcher above.
    private val requestLocalNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // A process that survived a previous exit must not leak the stale
        // "host finishing" signal into this session (see onDestroy).
        com.raulshma.jellyplay.feature.player.video.PlayerHostExitState.isHostFinishing = false
        // Clear any orientation lock a previous in-nav player (e.g. Live TV) may
        // have written onto this activity before the process was
        // killed. Because configChanges includes orientation, MainActivity is
        // never recreated, so without this reset a restored task can boot stuck
        // in landscape with no control to unlock it. UNSPECIFIED follows the
        // system auto-rotate setting, matching the fresh-install default.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        // The launch-intent classification FIRST — the cold-start session
        // restoration's skip signal rides the ShellInfra bundle below (an
        // explicit launch destination — shortcut / deep link / shared text /
        // search — is never hijacked by a "resume last watched?" prompt). The
        // dispatch still runs through the same single classification.
        val launchDisposition = handleIncomingIntent(intent)
        // Bundled once here (before the splash gate below reads the session
        // coordinator's restore flag) so the shell host's cross-cutting
        // services — the infrastructure providers plus the three shell
        // coordinators and the shared message bus — travel to JellyPlayApp →
        // MainContent as ONE value with ONE Koin resolution site. All ten
        // lazy providers — nothing here resolves any Koin single; each
        // `.value` fires at the consumer's first real use (see ShellInfra's
        // KDoc).
        val shellInfra = com.raulshma.jellyplay.shell.ShellInfra(
            networkStatusLazy = lazy { networkMonitor.networkStatus },
            networkMeteredLazy = lazy { networkMonitor.isMetered },
            audioPlaybackManagerLazy = audioPlaybackManagerLazy,
            remoteNavigationBridgeLazy = remoteNavigationBridgeLazy,
            remoteControlReceiverLazy = remoteControlReceiverLazy,
            sessionCoordinatorLazy = lazy { KoinPlatform.getKoin()!!.get() },
            updateCoordinatorLazy = lazy { KoinPlatform.getKoin()!!.get() },
            syncPlayOpenCoordinatorLazy = lazy { KoinPlatform.getKoin()!!.get() },
            whatsNewCoordinatorLazy = lazy { KoinPlatform.getKoin()!!.get() },
            sessionRestorationControllerLazy = lazy {
                KoinPlatform.getKoin()!!.get<com.raulshma.jellyplay.feature.player.video.session.SessionRestorationController>()
            },
            launchedWithExplicitDestination =
                launchDisposition != null && launchDisposition != IncomingIntentDisposition.None,
            userMessageBusLazy = lazy { KoinPlatform.getKoin()!!.get<com.raulshma.jellyplay.core.ui.message.UserMessageBus>() },
            externalPlayerReportsLazy = lazy { KoinPlatform.getKoin()!!.get<com.raulshma.jellyplay.navigation.playbackhost.ExternalPlayerReports>() },
            // Remote navigation ladder: synthesized D-pad/select/menu
            // key events go through the activity's own dispatch (down + up),
            // so Compose's existing key/focus handling interprets them —
            // nothing here re-implements focus traversal. Returns whether
            // anything consumed the pair, the fallback signal for the
            // context-menu message.
            keyDispatcher = { keyCode ->
                dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)) ||
                    dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
            },
        )
        // The distribution cast pre-init (the one-time GMS CastContext warm —
        // Dynamite dex load + hasSystemFeature binder call — on store; an
        // always-false no-op on fdroid) runs in the coroutine kicked off just
        // before setContent below — NOT synchronously before it — so neither
        // cost lands in TTID (the splash cannot release until onCreate + the
        // first composition finish). Every later getSharedInstance caller
        // (the hidden route button there, CastManager, GoogleCastStrategy)
        // hits the cached singleton.
        // The splash keeps until BOTH content gates are usable — the same
        // pair the fully-drawn report below waits on: the session-restore
        // phase leaving `Restoring` AND the shell's compose gate rendering a
        // usable surface (LockScreen or Browse; never the fail-closed
        // Blocked box — see shellGateUsable). The persisted-security read
        // that decides the gate answers after the first composition, so
        // without the second half a cold start can drop the splash onto the
        // neutral box for a frame or more before the gate resolves. A gate
        // that stays Blocked (a stalled read) keeps the splash up rather
        // than revealing the neutral surface — the read retries, so this is
        // a recovery wait, not a wedge.
        splashScreen.setKeepOnScreenCondition {
            shellInfra.sessionCoordinatorLazy.value.phase.value == LaunchPhase.Restoring ||
                !shellGateUsable.value
        }
        // Fully-drawn report: the app's real "content ready" boundary is BOTH
        // gates clearing — the session-restore phase leaving `Restoring` AND
        // the shell's compose gate rendering a usable surface (LockScreen or
        // Browse; see shellGateUsable). With a configured lock the composition
        // can still sit in the fail-closed Blocked box when the restore
        // completes, so the report fires on the first state where both hold —
        // immediately, when the persisted read answers on the first usable
        // frame. StartupTimingMetric uses reportFullyDrawn as
        // TimeToFullDisplay; without it, full-display timing is unmeasurable.
        // The collector runs on Main (reportFullyDrawn is a main-thread API)
        // and the AtomicBoolean makes it fire exactly once per activity
        // instance even though both StateFlows replay their current value to
        // every subscriber and the restore choreography can flip the phase
        // gate more than once during a locale-driven recreate().
        lifecycleScope.launch(Dispatchers.Main) {
            combine(
                shellGateUsable,
                shellInfra.sessionCoordinatorLazy.value.phase,
            ) { gateUsable, phase -> gateUsable && phase != LaunchPhase.Restoring }
                .collect { ready ->
                    if (ready && fullyDrawnReported.compareAndSet(false, true)) {
                        reportFullyDrawn()
                    }
                }
        }
        // No custom setOnExitAnimationListener: the system default splash exit
        // is a clean cross-fade to the first composed frame. A manual listener
        // holds the splash view alive across an alpha fade, and because the
        // starting window's background is the splash color, the fade revealed
        // that color through the outgoing splash — producing a visible "splash
        // flashes back in" artifact after Home had already rendered. Letting
        // the system remove the splash the instant the gate releases avoids it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            window.navigationBarColor = android.graphics.Color.parseColor("#66000000") // Translucent black
        }
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Android 17+: request local network access so LAN servers and discovery
        // work for returning users on cold start. The session restore in
        // MainViewModel runs concurrently; on denial, connections to LAN hosts
        // simply fail fast (and recover once the permission is later granted).
        if (LocalNetworkAccess.enforced &&
            !LocalNetworkAccess.isGranted(this)
        ) {
            requestLocalNetworkPermissionLauncher.launch(
                LocalNetworkAccess.PERMISSION
            )
        }

        // Pre-Android 13 per-app language: observe the saved language and apply
        // it on cold start, then recreate when the user changes it at runtime.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            var localeApplied = false
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    viewModel.preferences.collect { prefs ->
                        if (localeApplied) return@collect
                        val current = com.raulshma.jellyplay.core.ui.components.LocaleApplier
                            .currentLanguageTag(this@MainActivity)
                        if (prefs.appLanguage != current) {
                            localeApplied = true
                            com.raulshma.jellyplay.core.ui.components.LocaleApplier
                                .apply(this@MainActivity, prefs.appLanguage)
                            recreate()
                        }
                    }
                }
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    com.raulshma.jellyplay.core.ui.components.LocaleApplier.recreateSignal.collect {
                        recreate()
                    }
                }
            }
        }

        // The cast pre-init (GMS CastContext warm on store; always-false on
        // fdroid — the full timing story lives in GoogleCastIntegrationImpl /
        // DistributionPlatform.HiddenCastRouteButton), kicked off here so it
        // runs CONCURRENTLY with setContent instead of serially before it —
        // onCreate (and thus TTID) never pays the initialization cost, and
        // castPreinitialized flips the hidden route button in one or two
        // frames.
        lifecycleScope.launch(Dispatchers.Main) {
            castPreinitialized = DistributionPlatform.preinitCast(this@MainActivity)
        }
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            // The unlocked flag lives in the app-scoped holder —
            // collected here so the gate below recomposes on every change.
            val pinUnlocked by appLockState.unlocked.collectAsStateWithLifecycle()

            // The gate's security inputs come from the PERSISTED slice, read
            // at each locking transition (cold start, auto-lock relock) —
            // NOT from the preferences pipeline, whose seed is all-defaults
            // ("no gate configured") until DataStore's first emission lands.
            // Keying the gate off the seed would flash browse content before
            // hydration and, with a stalled read, leave it reachable; the
            // persisted read keeps [AppLockRedirect.ShellGate.Blocked] until
            // it answers (fail closed). The read goes through the SAME
            // bounded seam the auto-lock folds use (AppLockReturn), so the
            // decision cannot drift onto a second, hand-rolled read with a
            // different bound. The projected security fields are the
            // invalidation trigger as well: a PIN/biometric toggle or PIN
            // change in Settings moves them without moving `pinUnlocked`, so
            // without re-reading the shell would keep rendering the
            // pre-edit decision (and stale `pinHash`) for the life of this
            // activity. A snapshot the projection contradicts is dropped
            // first so the gate re-blocks until the fresh read answers; the
            // warm read replays the in-memory snapshot.
            var gateSecurity by remember { mutableStateOf<SecuritySlice?>(null) }
            // The projected gate inputs the snapshot above was loaded FOR.
            // The branch selection below rejects a snapshot the projection
            // has moved off — Blocked IMMEDIATELY, at this composition, not
            // a frame later when this effect's blank-and-re-read lands: a
            // snapshot loaded before a Settings-side PIN/biometric toggle
            // would otherwise still decide the gate (and render Browse or a
            // stale challenge) for the frames between the projection flip
            // and the effect run — unless the snapshot's CONTENT already
            // matches the moved projection (the hydration catch-up, where
            // the read would return identical values), which keeps deciding
            // so catching up cannot blank an already-rendered challenge for
            // a frame. Because staleness compares LOAD inputs —
            // not slice content — a completed read also resolves the gate
            // while the projection is still hydrating: the pre-hydration
            // seed matches the inputs the read ran for, so the fresh slice
            // decides instead of blocking until hydration catches up.
            var gateInputs by remember { mutableStateOf<GateProjectionInputs?>(null) }
            val projectedGateInputs = GateProjectionInputs(
                pinLockEnabled = preferences.pinLockEnabled,
                biometricLockEnabled = preferences.biometricLockEnabled,
                pinHash = preferences.pinHash,
            )
            LaunchedEffect(pinUnlocked, projectedGateInputs) {
                if (pinUnlocked) return@LaunchedEffect
                if (AppLockRedirect.gateSnapshotStale(
                        loaded = gateSecurity,
                        projectedPinLockEnabled = projectedGateInputs.pinLockEnabled,
                        projectedBiometricLockEnabled = projectedGateInputs.biometricLockEnabled,
                        projectedPinHash = projectedGateInputs.pinHash,
                    )
                ) {
                    gateSecurity = null
                    gateInputs = null
                }
                // The bounded read can time out; this effect is the only
                // writer of the decision, so retry rather than strand the
                // fail-closed Blocked surface forever. A matching snapshot
                // already loaded stays up across the retries (it was not
                // judged stale), and the branch selection keeps it valid.
                var persisted = AppLockReturn.persistedSecurity(securityStore)
                while (persisted == null) {
                    delay(AppLockReturn.READ_RETRY_MS)
                    persisted = AppLockReturn.persistedSecurity(securityStore)
                }
                gateSecurity = persisted
                gateInputs = projectedGateInputs
            }

            // Hidden Cast media-route button (store builds; DistributionPlatform
            // renders nothing on fdroid), composed only once the onCreate
            // cast-init coroutine (kicked off just before setContent) reports
            // success. The gate reads castPreinitialized HERE — not inside the
            // platform — so this call site keeps subscribing to the field and
            // recomposes the moment it flips: the button appears on the
            // recomposition that lands the flip, a frame or two after the
            // first composition. When Cast is unavailable (or not yet
            // initialized) the branch is skipped outright.
            if (castPreinitialized) {
                DistributionPlatform.HiddenCastRouteButton()
            }

            // Preference-driven dark-theme derivation (DARK/LIGHT/SYSTEM/SCHEDULED
            // + synthwave override) is shared with PlayerActivity via
            // rememberPreferenceDarkTheme so both hosts flip theme identically.
            val darkTheme = rememberPreferenceDarkTheme(preferences)

            val activity = this
            LaunchedEffect(darkTheme) {
                activity.enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            JellyPlayPreferenceTheme(
                preferences = preferences,
                darkTheme = darkTheme,
                isTv = isTv(),
            ) {
                val gateDecision = AppLockRedirect.shellGate(
                    pinUnlocked,
                    // A snapshot decides while it still represents the
                    // projection (see gateInputs above): either it was
                    // loaded FOR exactly these inputs, or its CONTENT
                    // already matches them — the bounded read would return
                    // the same values, so the decision is provably
                    // identical. The content arm closes the hydration
                    // flip's one-frame gap: the projection catching up to
                    // an already-rendered challenge must not blank it for
                    // the frames between the flip and the effect's warm
                    // re-read; a genuine Settings-side change still
                    // rejects (content mismatch → Blocked now, never the
                    // stale snapshot).
                    if (gateInputs == projectedGateInputs ||
                        !AppLockRedirect.gateSnapshotStale(
                            loaded = gateSecurity,
                            projectedPinLockEnabled = projectedGateInputs.pinLockEnabled,
                            projectedBiometricLockEnabled = projectedGateInputs.biometricLockEnabled,
                            projectedPinHash = projectedGateInputs.pinHash,
                        )
                    ) gateSecurity else null,
                )
                // Latch the fully-drawn signal on the first usable decision
                // (never while the fail-closed Blocked box is up — see
                // shellGateUsable). Latching means a later re-block after a
                // Settings-side snapshot invalidation cannot un-report.
                LaunchedEffect(gateDecision) {
                    if (gateDecision != AppLockRedirect.ShellGate.Blocked) {
                        shellGateUsable.value = true
                    }
                }
                when (gateDecision) {
                    // Fail CLOSED until the persisted slice answers (see the
                    // gate-inputs effect above): a neutral themed surface —
                    // never browse content — while locked and unread, however
                    // long a stalled read takes.
                    AppLockRedirect.ShellGate.Blocked -> Box(Modifier.fillMaxSize())
                    // The gate's composition — lockout clock loop, error
                    // fork, title fork, verify spinner — lives in its own
                    // host composable beside PinGateController
                    // (shell/LockGateHost.kt), the overlay-host pattern; this
                    // fork keeps only the theme + gate decision. The
                    // challenge's static inputs come from the persisted slice
                    // that decided the branch; the lockout deadline stays
                    // live from the rate limiter's flow.
                    AppLockRedirect.ShellGate.LockScreen -> LockGateHost(
                        pinHash = gateSecurity?.pinHash,
                        biometricLockEnabled = gateSecurity?.biometricLockEnabled == true,
                        pinLockoutUntilEpochMs = preferences.pinLockoutUntilEpochMs,
                        pinRateLimiter = pinRateLimiter,
                        pinGateController = pinGateController,
                        submitScope = lifecycleScope,
                    )
                    AppLockRedirect.ShellGate.Browse -> JellyPlayApp(
                        viewModel = viewModel,
                        infra = shellInfra,
                    )
                }
            }
        }
    }

    /**
     * The player VM's teardown reads this (through PlayerHostExitState) to
     * tell an in-app player close from the host itself going away: only a
     * genuine exit (finished, not a configuration recreation) suppresses the
     * cold-start restore prompt's handled-offer marker, so an app swiped
     * from recents mid-playback still prompts on the next launch. Written
     * BEFORE super.onDestroy() — the ViewModelStore clear that runs the
     * player's onCleared teardown follows it.
     */
    override fun onDestroy() {
        com.raulshma.jellyplay.feature.player.video.PlayerHostExitState.isHostFinishing =
            isFinishing && !isChangingConfigurations
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * One dispatch line over the pure [IncomingIntentRequest] fold (deeplink
     * package): the fold owns the action vocabulary and classifies the intent
     * into the shell entry point. Returns the disposition — onCreate feeds it
     * to [ShellInfra]'s `launchedWithExplicitDestination` (the cold-start
     * session restoration's skip signal); onNewIntent ignores it.
     */
    private fun handleIncomingIntent(intent: Intent?): IncomingIntentDisposition? {
        if (intent == null) return null
        return IncomingIntentRequest.from(intent).classify().also { disposition ->
            when (disposition) {
                is IncomingIntentDisposition.Shortcut -> viewModel.handleShortcutIntent(disposition)
                IncomingIntentDisposition.DeepLink -> viewModel.handleDeepLink(intent)
                is IncomingIntentDisposition.SharedText -> viewModel.handleSharedText(disposition.sharedText)
                is IncomingIntentDisposition.Search -> viewModel.handleSearchQuery(disposition.query)
                IncomingIntentDisposition.None -> Unit
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // The video engine is hosted by PlayerActivity, which owns its own
        // pause/resume handling; LiveTV manages its own engine lifecycle too,
        // so MainActivity pauses nothing here (driving the shared
        // PlayerLifecycleManager from here would reach across Activities into
        // PlayerActivity's engine). Only record backgrounding for the PIN
        // auto-lock timer — the stamp itself lives in AppLockState. The
        // monotonic clock keeps the later fold's elapsed measurement immune
        // to wall-clock jumps and counting device sleep. Recording only when
        // none is pending (same rule as PlayerActivity's recorders): the
        // shell can pause INSIDE an absence the player already stamped — the
        // expanding player pauses the revealed shell in the same transition
        // whose fold it is about to run, and the OEM orderings vary — so
        // overwriting the PiP-entry stamp with "now" would reset the clock
        // and let an absence past the auto-lock timer skip the challenge
        // (ADR 0018, Decision 6).
        appLockState.onBackgroundedIfNonePending(SystemClock.elapsedRealtime())
    }

    override fun onResume() {
        super.onResume()

        // Stamp the callback entry BEFORE the bounded policy read below: the
        // fold measures the absence against this instant, so the read's
        // latency (up to the full timeout) must not count as time away.
        val nowMs = SystemClock.elapsedRealtime()

        // The auto-lock-on-resume policy (gate configured + timer elapsed while
        // backgrounded → relock) is folded by AppLockReturn.foldReturnPolicy —
        // the same seam PlayerActivity's return gate runs — from the PERSISTED
        // security slice. The preferences pipeline's `.value` seeds
        // all-defaults until DataStore hydrates ("no gate configured"), so
        // folding off it could consume an expired background stamp without
        // relocking on an activity recreated in a warm process; the persisted
        // read replays the in-memory snapshot for a hydrated process and
        // fails closed on a stalled one. onResumed consumes the background
        // stamp unconditionally and measures against the same monotonic
        // clock onPause stamped with — which is exactly why a resume beneath
        // the player's still-DOCKED PiP window must skip the fold entirely:
        // it is not a genuine return (ADR 0018, Decision 5), and consuming
        // the PiP-entry stamp here would let the later expand past the
        // auto-lock timer skip the challenge. But the exclusion covers only
        // the reveal within the dock session: a return from OUTSIDE the app
        // (home/recents) while the window still floats is a genuine return,
        // recognised by this activity's own onStop having run during the
        // CURRENT dock session (dockedSessionAtBackgroundStop). PiP state
        // alone cannot separate the two resumes; the session-matched
        // record does.
        if (!AppLockReturn.isGenuineShellReturn(
                playerDockedInPip = pipController.isInPipMode.value,
                shellBackgroundedDuringDockedSession =
                    dockedSessionAtBackgroundStop != 0 &&
                    dockedSessionAtBackgroundStop == pipController.pipSession,
            )
        ) {
            return
        }
        // Without a pending background stamp the fold below is provably a
        // no-op — shouldRelock requires a stamp, and the fail-closed arm
        // requires one too — so the return decision completes WITHOUT the
        // bounded read (which can block main up to its full timeout on a
        // cold process; the prewarm narrows but never closes that race).
        // A pending stamp still folds, locked holder included, exactly as
        // before.
        if (!appLockState.hasPendingBackground) return
        val persisted = runBlocking { AppLockReturn.persistedSecurity(securityStore) }
        AppLockReturn.foldReturnPolicy(
            lockState = appLockState,
            persisted = persisted,
            nowMs = nowMs,
        )
    }

    override fun onStop() {
        super.onStop()
        // No engine lifecycle here. The video engine lives in PlayerActivity
        // (which owns its own PiP + pause/resume handling); LiveTV manages
        // itself. MainActivity never enters PiP, so this hook has no
        // PiP-dismiss work to do — but it records WHICH dock session (if any)
        // this stop backgrounded under: a stop while the player window is
        // docked means the task left the foreground mid-session, so the
        // matching later docked resume is a genuine return whose auto-lock
        // fold must run (AppLockReturn.isGenuineShellReturn), unlike the
        // entry reveal that Decision 5 exempts. Stopping outside PiP clears
        // the record (a plain shell backgrounding); the value is overwritten
        // on every stop, and a stale record can only ever match its own
        // session id.
        dockedSessionAtBackgroundStop = if (pipController.isInPipMode.value) {
            pipController.pipSession
        } else {
            0
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

/**
 * The projected gate inputs a persisted-slice snapshot was loaded for —
 * MainActivity's gate staleness key (see the composition's `gateInputs`).
 * A snapshot may decide the shell branch only while the projection still
 * equals the inputs it was read under; any field moving (a Settings-side
 * PIN/biometric toggle or PIN change) rejects it into Blocked immediately,
 * and the keyed LaunchedEffect re-reads.
 */
private data class GateProjectionInputs(
    val pinLockEnabled: Boolean,
    val biometricLockEnabled: Boolean,
    val pinHash: String?,
)
