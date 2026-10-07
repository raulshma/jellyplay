package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.CAP_SILENT_PUSH
import com.raulshma.jellyplay.core.network.api.JellyPlayDevicePush
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayPushRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The push registration's lifecycle state for THIS device — the machine the
 * settings row's subtitle reads. Exactly the five states the push wave's
 * contract names:
 *  - [Unregistered] — no registration asked for (or the last one failed);
 *  - [Registering] — the distributor has been asked; waiting for the endpoint;
 *  - [Registered] — endpoint in hand AND attached to the server device record;
 *  - [NoDistributor] — the user has no UnifiedPush distributor app installed
 *    (ntfy & co) — the settings row surfaces "requires a push distributor app";
 *  - [ServerPushOff] — the probe's registry does not expose the `push` key
 *    (server admin disabled push, or the plugin predates the push wave).
 */
sealed interface JellyPushState {
    data object Unregistered : JellyPushState
    data object Registering : JellyPushState
    data class Registered(val endpoint: String) : JellyPushState
    data object NoDistributor : JellyPushState
    data object ServerPushOff : JellyPushState
}

/**
 * The platform half of the push wave: the UnifiedPush distributor seam. The
 * Android surface implements it over the connector (`UnifiedPush.register` /
 * `unregister` / distributor discovery); desktop registers nothing, so a
 * `push` toggle there degrades to [JellyPushState.NoDistributor] without any
 * distributor machinery — the same getOrNull shape as
 * [com.raulshma.jellyplay.core.data.notification.JellyPlayNewMediaNotifier].
 *
 * All methods are fire-and-forget: `register` asks the distributor for an
 * endpoint and the answer lands asynchronously through the connector
 * receiver into [JellyPushRepository.onNewEndpoint].
 */
interface JellyPushDistributor {

    /** Whether a UnifiedPush distributor app is installed on this device. */
    fun hasDistributor(): Boolean

    /** Ask the distributor for (a fresh) endpoint — connector `register()`. */
    fun register(instance: String = INSTANCE_DEFAULT)

    /** Drop the registration — connector `unregister()`. No callback follows. */
    fun unregister(instance: String = INSTANCE_DEFAULT)

    companion object {
        /** The connector's default registration instance (single-registration apps). */
        const val INSTANCE_DEFAULT = ""
    }
}

/**
 * The push face of the jellyfin-plugin-jellyplay companion plugin (the
 * plugin's push wave): per-device registration state machine over the
 * UnifiedPush distributor seam + the plugin's additive `push` device
 * registration field.
 *
 * Identity + transport are the EVENTS face's, deliberately: the SAME
 * `jpsync.device.id` deviceId ([deviceIdProvider]), the same
 * name/platform/appVersion triple, and the same `registerDevice` route —
 * push attaches to the device record the events session already creates, and
 * re-POSTing the same deviceId with a (new) endpoint rotates the
 * registration server-side.
 *
 * Lifecycle: [start] rides the `push` gate reactively (probe AVAILABLE AND
 * the user's toggle — the ONE gating seam, ADR 0010) on the shared
 * application scope, exactly like the events face's session controller:
 *
 *  - gate OPEN and no registration yet → ask the distributor
 *    ([JellyPushDistributor.register]); [onNewEndpoint] persists the endpoint
 *    locally (the reserved per-device `jpsync.device.push.endpoint` pref,
 *    never synced) AND re-POSTs `registerDevice` WITH the push field;
 *  - gate OPEN with a persisted endpoint → re-POST `registerDevice` with the
 *    push field directly (freshens/repairs the server record — the events
 *    session's own registration POSTs with the push field OMITTED, so this
 *    restore is what keeps push attached across sessions);
 *  - gate CLOSED because the user's toggle went off → `registerDevice`
 *    with an explicit push-off (`push: null` on the wire) +
 *    [JellyPushDistributor.unregister] (push explicitly gone);
 *  - gate CLOSED because the probe stopped exposing `push` (admin disabled /
 *    old plugin) → [JellyPushState.ServerPushOff] and the device record is
 *    left alone (an old plugin must never see a `push` field it cannot parse);
 *  - [onUnregistered] (the distributor revoked us — app uninstalled,
 *    registration expired) → [JellyPushState.NoDistributor] + re-POST
 *    `registerDevice` with the explicit push-off, so the server stops
 *    routing to a dead endpoint — gate-guarded like [onNewEndpoint]: a
 *    closed gate has nothing to detach and must not touch the wire.
 *
 * Expected usage: this works when the user has a UnifiedPush distributor
 * installed (e.g. the ntfy Android app). Without one the state machine parks
 * on [JellyPushState.NoDistributor] — the settings row reads that and shows
 * "requires a push distributor app"; the toggle itself stays a synced
 * `pluginFeature.push.enabled` pref like every other feature key.
 */
class JellyPushRepository(
    private val apiClient: JellyPlayPluginApiClient,
    private val statusStore: JellyPlayPluginStatusStore,
    private val featureGate: JellyPlayFeatureGate,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val deviceName: String,
    private val devicePlatform: String,
    private val appVersion: String,
    private val deviceIdProvider: suspend () -> String,
    /** The Android connector impl; null where no distributor surface exists (desktop). */
    private val distributor: JellyPushDistributor?,
) {

    private val _state = MutableStateFlow<JellyPushState>(JellyPushState.Unregistered)

    /** The registration state the settings row's subtitle reads. */
    val state: StateFlow<JellyPushState> = _state.asStateFlow()

    /**
     * Serializes every state transition: the gate collector, the connector
     * receiver's callbacks and the toggle paths never interleave a
     * registration with a teardown.
     */
    private val transition = Mutex()

    /** The session's gate collector (see [start]). */
    private var gateJob: Job? = null

    /**
     * Rides the `push` gate for one signed-in session. The events session
     * controller calls this on the session's auth edge — independent of the
     * `events` toggle (push delivery through the distributor does not ride
     * the SSE stream). The plain device registration the events face POSTs
     * omits the push field (the server keeps its push state), so this
     * repository's restore re-POST is what re-attaches the persisted
     * endpoint after a detach. Safe to call repeatedly (idempotent, the same
     * replace-never-stack shape).
     */
    fun start() {
        if (gateJob?.isActive == true) return
        gateJob = scope.launch {
            // One probe when the store hasn't got a fresh AVAILABLE (the shared
            // ladder) — a mid-session plugin install restores push without a
            // re-auth; the collector below re-probes on capability flips.
            statusStore.ensureAvailable()
            // Optimistic restore: a persisted endpoint re-attaches below (the
            // freshening re-POST) without demanding a live distributor round-trip.
            // Guarded on the WHOLE gate (probe exposing push AND the user's
            // toggle — the one-shot read): attaching on the probe alone would
            // re-attach push for a toggle-off user until the collector below
            // tears it back down — a transient server-side attach.
            persistedEndpoint()?.let { endpoint ->
                if (featureGate.isAvailableNow(JellyPlayPluginFeatures.Push)) {
                    transition.withLock {
                        if (_state.value == JellyPushState.Unregistered) {
                            postDeviceRegistration(attachPush(endpoint))
                                .onSuccess { _state.value = JellyPushState.Registered(endpoint) }
                                .onFailure { e ->
                                    // Stays Unregistered ("the last one failed") —
                                    // the next start or toggle cycle retries.
                                    Log.e(TAG, "Push restore re-POST failed; retrying next session", e)
                                }
                        }
                    }
                }
            }
            featureGate.isAvailable(JellyPlayPluginFeatures.Push).collect { available ->
                when {
                    available -> enable()
                    JellyPlayPluginFeatures.Push in statusStore.features.value -> disable()
                    else -> serverPushOff()
                }
            }
        }
    }

    /** Cancels the gate collector; the registration itself persists (device-level, not session-level). */
    fun stop() {
        gateJob?.cancel()
        gateJob = null
    }

    /**
     * The enable half of the machine: ask the distributor for an endpoint.
     * No-op when already registered/registering. Without the platform seam
     * (desktop) or a distributor app (no ntfy & co installed) the machine
     * parks on [JellyPushState.NoDistributor] — the UI surfaces it, nothing
     * is sent to the server.
     */
    suspend fun enable() {
        transition.withLock {
            when {
                _state.value is JellyPushState.Registered || _state.value is JellyPushState.Registering -> return
                // No platform seam (desktop): a distributor app cannot exist either.
                distributor == null || !distributor.hasDistributor() -> {
                    _state.value = JellyPushState.NoDistributor
                }
                else -> {
                    _state.value = JellyPushState.Registering
                    distributor.register(JellyPushDistributor.INSTANCE_DEFAULT)
                }
            }
        }
    }

    /**
     * The disable half: detach push from the server device record
     * (`registerDevice` with the explicit push-off — `push: null` on the
     * wire, CONTRACT.md's detach shape) and drop the distributor
     * registration + the persisted endpoint. The local half only drops on a
     * CONFIRMED detach: a failed POST leaves the machine as it stood (the
     * row keeps reading [JellyPushState.Registered] — the server still holds
     * the endpoint) and the next session start or toggle cycle retries.
     */
    suspend fun disable() {
        transition.withLock {
            if (_state.value is JellyPushState.ServerPushOff) return
            postDeviceRegistration(JellyPlayDevicePush.Detach)
                .onSuccess {
                    clearPersistedEndpoint()
                    distributor?.unregister(JellyPushDistributor.INSTANCE_DEFAULT)
                    _state.value = JellyPushState.Unregistered
                }
                .onFailure { e ->
                    Log.e(TAG, "Push detach POST failed; registration left attached for retry", e)
                }
        }
    }

    /**
     * The connector receiver's NEW_ENDPOINT callback: persist the endpoint
     * locally AND attach it to the server device record (the rotation path —
     * same deviceId, new endpoint replaces the old server-side). A late
     * endpoint landing after an explicit disable (the toggle went off while
     * the registration request was in flight) does NOT re-arm the machine —
     * the fresh gate read is the authority, taken UNDER the transition lock
     * so a disable racing the in-flight endpoint structurally wins. A failed
     * attach POST parks the machine on [JellyPushState.Unregistered] ("the
     * last one failed") but KEEPS the persisted endpoint — the next
     * [start] restore re-POSTs it without a new distributor round-trip.
     */
    suspend fun onNewEndpoint(endpoint: String, instance: String = JellyPushDistributor.INSTANCE_DEFAULT) {
        if (instance != JellyPushDistributor.INSTANCE_DEFAULT) {
            Log.d(TAG, "Ignoring non-default push instance: $instance")
            return
        }
        transition.withLock {
            if (!featureGate.isAvailableNow(JellyPlayPluginFeatures.Push)) {
                Log.d(TAG, "Ignoring endpoint while the push gate reads closed")
                return
            }
            persistEndpoint(endpoint)
            postDeviceRegistration(attachPush(endpoint))
                .onSuccess { _state.value = JellyPushState.Registered(endpoint) }
                .onFailure { e ->
                    Log.e(TAG, "Push attach POST failed; endpoint kept for the restore path", e)
                    _state.value = JellyPushState.Unregistered
                }
        }
    }

    /**
     * The connector receiver's UNREGISTERED callback: the distributor revoked
     * us (distributor uninstalled, registration expired) — the endpoint is
     * dead, so detach it server-side too and surface
     * [JellyPushState.NoDistributor]. Guarded on the live gate exactly like
     * [onNewEndpoint]: with the gate closed there is nothing to detach — the
     * disable path already did it (toggle off), and under a probe that
     * stopped exposing `push` the persisted endpoint stays put for the day
     * push comes back AND an old plugin must never see a `push` field.
     */
    suspend fun onUnregistered(instance: String = JellyPushDistributor.INSTANCE_DEFAULT) {
        if (instance != JellyPushDistributor.INSTANCE_DEFAULT) {
            Log.d(TAG, "Ignoring non-default push instance: $instance")
            return
        }
        transition.withLock {
            // The gate read rides INSIDE the lock, same as [onNewEndpoint] —
            // a disable racing the revocation must win.
            if (!featureGate.isAvailableNow(JellyPlayPluginFeatures.Push)) {
                Log.d(TAG, "Ignoring unregistration while the push gate reads closed")
                return
            }
            // The distributor already revoked us — the endpoint is dead
            // regardless of the detach's fate: local cleanup runs either way,
            // a failed POST only leaves a stale server entry the next attach
            // rotates away.
            clearPersistedEndpoint()
            postDeviceRegistration(JellyPlayDevicePush.Detach)
                .onFailure { e -> Log.e(TAG, "Push detach after distributor revocation failed", e) }
            _state.value = JellyPushState.NoDistributor
        }
    }

    /**
     * The connector receiver's REGISTRATION_FAILED callback: transient by
     * nature (no network, distributor busy) — fall back to
     * [JellyPushState.Unregistered] so the next session start or a toggle
     * cycle retries; the reason is log-only.
     */
    suspend fun onRegistrationFailed(reason: String?, instance: String = JellyPushDistributor.INSTANCE_DEFAULT) {
        if (instance != JellyPushDistributor.INSTANCE_DEFAULT) {
            Log.d(TAG, "Ignoring non-default push instance: $instance")
            return
        }
        Log.d(TAG, "Push registration failed${reason?.let { ": $it" } ?: ""}")
        transition.withLock {
            if (_state.value is JellyPushState.Registering) {
                _state.value = JellyPushState.Unregistered
            }
        }
    }

    /**
     * The probe stopped exposing `push` (server admin disabled push, or the
     * plugin predates the push wave): park the machine on
     * [JellyPushState.ServerPushOff] and touch NOTHING — an old plugin must
     * never see a `push` field it cannot parse, and the persisted endpoint +
     * distributor registration stay put for the day push comes back (the
     * restore re-POST re-attaches then).
     */
    private suspend fun serverPushOff() {
        transition.withLock {
            _state.value = JellyPushState.ServerPushOff
        }
    }

    /** The attach shape both the rotation and the restore path POST. */
    private fun attachPush(endpoint: String): JellyPlayDevicePush.Attach =
        JellyPlayDevicePush.Attach(JellyPlayPushRegistration(kind = KIND_GENERIC, endpoint = endpoint))

    /** Re-POSTs the device registration with the given push field: attach (rotation/restore path) or detach. */
    private suspend fun postDeviceRegistration(push: JellyPlayDevicePush): Result<Unit> =
        apiClient.registerDevice(
            deviceId = deviceIdProvider(),
            name = deviceName,
            platform = devicePlatform,
            appVersion = appVersion,
            push = push,
            // The caps assertion rides EVERY registration — the wire REPLACES
            // caps, so this rotation path must re-assert what the events face
            // registered or the silent-push opt-in would silently drop.
            caps = listOf(CAP_SILENT_PUSH),
        )

    private suspend fun persistedEndpoint(): String? =
        dataStore.data.first()[ENDPOINT_KEY]

    private suspend fun persistEndpoint(endpoint: String) {
        dataStore.edit { it[ENDPOINT_KEY] = endpoint }
    }

    private suspend fun clearPersistedEndpoint() {
        dataStore.edit { it.remove(ENDPOINT_KEY) }
    }

    private companion object {
        const val TAG = "JellyPushRepository"

        /** The plugin contract's kind for the connector-agnostic generic payload. */
        const val KIND_GENERIC = "generic"

        /** Reserved per-device pref (`jpsync.device.` keys never sync — adapter rule). */
        val ENDPOINT_KEY = stringPreferencesKey("jpsync.device.push.endpoint")
    }
}
