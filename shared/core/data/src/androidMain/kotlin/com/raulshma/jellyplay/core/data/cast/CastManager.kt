package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import androidx.compose.runtime.Stable
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import com.raulshma.jellyplay.core.model.CastingStrategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

sealed class CastSessionEvent {
    data object Connected : CastSessionEvent()
    data object Disconnected : CastSessionEvent()
}

@Stable
class CastManager(
    private val context: Context,
    private val googleCastIntegration: GoogleCastIntegration,
    private val dlnaCastStrategy: DlnaCastStrategy,
    private val jellyfinRemotePlayCastStrategy: JellyfinRemotePlayCastStrategy,
    private val syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
    /**
     * The Koin application scope (DatastoreQualifiers.applicationScope) this
     * manager's Main-dispatcher scope hangs off as a SupervisorJob child:
     * [release] still cancels only the manager's own scope, while an
     * app-level teardown can no longer orphan it.
     */
    private val applicationScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "CastManager"
        // Canonical dispatch names live on [CastStrategyNames] (commonMain,
        // where the pure state fan-out reads them); these public aliases keep
        // the manager's historical surface for existing consumers.
        const val STRATEGY_GOOGLE = CastStrategyNames.GOOGLE
        const val STRATEGY_DLNA = CastStrategyNames.DLNA
        const val STRATEGY_JELLYFIN = CastStrategyNames.JELLYFIN

        // The TaskBundle keys for the manager's four cancel-and-replace job slots.
        private const val TICKER = "CastManager.ticker"
        private const val STRATEGY_OBSERVER = "CastManager.strategyObserver"
        private const val DEVICE_MERGE = "CastManager.deviceMerge"
        private const val PREFERRED_RENDERER = "CastManager.preferredRenderer"
    }

    private val strategies = mutableMapOf<String, CastStrategy>()
    private var activeStrategyName: String = STRATEGY_GOOGLE

    val currentStrategyName: String get() = activeStrategyName

    // The receiver-side cast player, created (and released) by the injected
    // [GoogleCastIntegration] — cached here so loadMedia's external-listener
    // bookkeeping and the transport adapter can address it without re-asking.
    private var castPlayer: Player? = null
    private var externalListener: Player.Listener? = null

    private val coroutineScope =
        CoroutineScope(SupervisorJob(applicationScope.coroutineContext[Job]) + Dispatchers.Main)

    /**
     * The manager's four cancel-and-replace job slots — the shared ticker,
     * the strategy-session observer, the device merge and the
     * preferred-renderer watcher — as one [TaskBundle] over [coroutineScope].
     * Access is confined to Main.
     */
    private val tasks = TaskBundle(coroutineScope)

    /** Per-strategy poll cadence, re-read by the shared ticker each tick. */
    private val castPollingIntervalMs = MutableStateFlow(1000L)

    private val _sessionEvents = MutableSharedFlow<CastSessionEvent>(extraBufferCapacity = 1)
    val sessionEvents: SharedFlow<CastSessionEvent> = _sessionEvents.asSharedFlow()

    private val _castPositionMs = MutableStateFlow(0L)
    val castPositionMs: StateFlow<Long> = _castPositionMs.asStateFlow()

    private val _castDurationMs = MutableStateFlow(0L)
    val castDurationMs: StateFlow<Long> = _castDurationMs.asStateFlow()

    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()

    private val _castBufferedPositionMs = MutableStateFlow(0L)
    val castBufferedPositionMs: StateFlow<Long> = _castBufferedPositionMs.asStateFlow()

    private val _castVolume = MutableStateFlow(1f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    private val _castTitle = MutableStateFlow("")
    val castTitle: StateFlow<String> = _castTitle.asStateFlow()

    private val _castSubtitle = MutableStateFlow("")
    val castSubtitle: StateFlow<String> = _castSubtitle.asStateFlow()

    private val _allDiscoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())

    val castPlayerForSession: Player? get() = castPlayer

    /**
     * Single transport dispatch point. DLNA and Jellyfin own their transport;
     * every other active strategy (Google Cast, ad-hoc registrations) rides
     * the manager-owned CastPlayer via [localCastPlayerTransport] — exactly
     * what the old when-chain else-arms did. The `?:` fallback is unreachable
     * in practice: activeStrategyName only ever names [STRATEGY_GOOGLE] with
     * a non-null integration strategy (applyDefaultCastingStrategy degrades
     * to DLNA when the integration is Unavailable), so the DLNA arm only
     * covers the unregisterStrategy(fallback-name) edge on fdroid — where
     * DLNA is then the only renderer-capable strategy anyway.
     */
    private val activeTransport: CastStrategy
        get() = when (activeStrategyName) {
            STRATEGY_DLNA -> dlnaCastStrategy
            STRATEGY_JELLYFIN -> jellyfinRemotePlayCastStrategy
            else -> localCastPlayerTransport ?: dlnaCastStrategy
        }

    /**
     * Adapter driving the manager-owned CastPlayer through the [CastStrategy]
     * transport surface, so transport dispatch has one path. Built only when
     * the injected integration exposes a strategy (store); on fdroid it stays
     * null and the transport map has no Google arm at all. Discovery /
     * session members delegate to [googleCastIntegration.strategy] and are
     * never routed through this adapter — the strategies map stays
     * authoritative for those.
     */
    private val localCastPlayerTransport: CastStrategy? = googleCastIntegration.strategy?.let { strategy ->
        object : CastStrategy by strategy {
            override val ownsExternalListener: Boolean = true

            override fun play() { castPlayer?.play() }
            override fun pause() { castPlayer?.pause() }
            override fun seekTo(positionMs: Long) { castPlayer?.seekTo(positionMs) }
            override fun setRendererVolume(volume: Float) {
                val player = castPlayer ?: return
                player.volume = volume.coerceIn(0f, 1f)
                _castVolume.value = player.volume
            }
            override fun loadMedia(
                mediaItem: MediaItem,
                startPositionMs: Long,
                listener: Player.Listener,
                options: CastMediaOptions,
            ): Boolean {
                ensureGoogleSessionListener()
                externalListener?.let { castPlayer?.removeListener(it) }
                val player = ensureCastPlayer()
                if (player == null) {
                    // Nothing was attached: recording the listener would let the
                    // session-unavailable callback fire a phantom STATE_ENDED on
                    // a load that never started.
                    externalListener = null
                    return false
                }
                externalListener = listener
                player.addListener(listener)
                // Apply the user's track / quality selections to the stream URL so the
                // receiving Chromecast requests the right variants from the server.
                // Subtitle sidecars configured on the MediaItem
                // are preserved as text tracks by CastPlayer.
                player.setMediaItem(mediaItem.withCastOptions(options), startPositionMs)
                player.prepare()
                player.play()
                return true
            }
        }
    }

    /**
     * Whether a cast session is live — DECLARED FIX: derived from the ACTIVE
     * strategy's connection phase (its [CastStrategy.isConnected] projection,
     * i.e. phase == Connected), not the Google `castPlayer != null` shape it
     * used to be (`castPlayer != null && isConnected`). The old conjunct was
     * a Google-Player-shaped leak: a connected DLNA / Jellyfin session on a
     * device that never built a CastPlayer reported isActive()==false, while
     * a STALE CastPlayer cached from an earlier Google session made
     * isActive()==true report through a dead session. Both arms now answer
     * from the strategy that actually owns routing. Identical to [isConnected]
     * — kept as its own named member because callers read it as a verb
     * ("is a cast session active right now"), and it documents the
     * strategy-phase derivation at the one place the old shape lived.
     */
    fun isActive(): Boolean = isConnected

    fun setVolume(volume: Float) {
        activeTransport.setRendererVolume(volume)
    }

    @Volatile
    private var released = false

    // Number of active cast consumers (e.g. live video screens). The shared
    // singleton is only torn down when the last consumer releases — see
    // [acquireConsumer] / [releaseConsumer]. AtomicInteger because acquire
    // and release may originate from different VM lifecycles.
    private val consumerRefCount = java.util.concurrent.atomic.AtomicInteger(0)

    private val backgroundCasting = AtomicBoolean(false)
    val isBackgroundCasting: Boolean get() = backgroundCasting.get()

    fun markBackgroundCasting(casting: Boolean) {
        backgroundCasting.set(casting)
    }

    private val castPlayerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            coroutineScope.launch { updateCastState() }
            toggleTicker()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _castIsPlaying.value = isPlaying
            toggleTicker()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            coroutineScope.launch { updateCastState() }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            coroutineScope.launch { updateCastState() }
        }
    }

    /**
     * Dispatch + write-through for the now-playing flow family. Dispatch:
     * refresh the strategy that owns playback state (DLNA / Jellyfin own
     * renderer-protocol state; every other active strategy rides the
     * manager-owned CastPlayer, snapshotted off the main thread — or not at
     * all when no player exists, leaving every flow untouched). Write-through:
     * [castStateFanout] — pure and commonMain-pinned — decides per strategy
     * which fields each branch contributes; `null` fields keep their flow's
     * previous value.
     */
    private suspend fun updateCastState() {
        val strategyName = activeStrategyName
        val dlna: DlnaRendererState? = if (strategyName == STRATEGY_DLNA) {
            dlnaCastStrategy.refreshPlaybackState()
            DlnaRendererState(
                positionMs = dlnaCastStrategy.rendererPositionMs.value,
                durationMs = dlnaCastStrategy.rendererDurationMs.value,
                isPlaying = dlnaCastStrategy.rendererIsPlaying.value,
                volume = dlnaCastStrategy.rendererVolume.value,
            )
        } else {
            null
        }
        val jellyfin: JellyfinNowPlayingState? = if (strategyName == STRATEGY_JELLYFIN) {
            jellyfinRemotePlayCastStrategy.refreshPlaybackState()
            JellyfinNowPlayingState(
                positionMs = jellyfinRemotePlayCastStrategy.positionMs.value,
                durationMs = jellyfinRemotePlayCastStrategy.durationMs.value,
                isPlaying = jellyfinRemotePlayCastStrategy.isPlaying.value,
                volume = jellyfinRemotePlayCastStrategy.volume.value,
                title = jellyfinRemotePlayCastStrategy.nowPlayingTitle.value,
                subtitle = jellyfinRemotePlayCastStrategy.nowPlayingSubtitle.value,
            )
        } else {
            null
        }
        val player: CastPlayerSnapshot? = if (dlna == null && jellyfin == null) {
            castPlayer?.let { player ->
                withContext(Dispatchers.Default) {
                    CastPlayerSnapshot(
                        position = player.currentPosition.coerceAtLeast(0),
                        duration = player.duration.coerceAtLeast(0),
                        buffered = player.bufferedPosition.coerceAtLeast(0),
                        isPlaying = player.isPlaying,
                        volume = player.volume,
                    )
                }
            }
        } else {
            null
        }
        applyCastState(castStateFanout(strategyName, dlna, jellyfin, player))
    }

    /** Writes the fan-out's non-null fields; null fields leave their flow as-is. */
    private fun applyCastState(fanout: CastStateFanout) {
        fanout.positionMs?.let { _castPositionMs.value = it }
        fanout.durationMs?.let { _castDurationMs.value = it }
        fanout.bufferedPositionMs?.let { _castBufferedPositionMs.value = it }
        fanout.isPlaying?.let { _castIsPlaying.value = it }
        fanout.volume?.let { _castVolume.value = it }
        fanout.title?.let { _castTitle.value = it }
        fanout.subtitle?.let { _castSubtitle.value = it }
    }

    private fun toggleTicker() {
        tasks.cancel(TICKER)
        val isDlna = activeStrategyName == STRATEGY_DLNA
        val isJellyfin = activeStrategyName == STRATEGY_JELLYFIN
        val shouldTick = when {
            isDlna -> dlnaCastStrategy.isConnected.value
            isJellyfin -> jellyfinRemotePlayCastStrategy.isConnected.value
            else -> castPlayer != null && castPlayer?.isPlaying == true
        }
        if (shouldTick) {
            // The shared polling loop (player-contract): bounded reactive
            // paused-wait, interval re-read per tick, and the post-timeout
            // `continue` that keeps a paused session off the network — a
            // timed-out paused wait must not keep polling the remote renderer.
            castPollingIntervalMs.value = if (isDlna || isJellyfin) 1000L else 500L
            tasks.replace(TICKER) {
                EnginePositionTicker(
                    scopeProvider = { coroutineScope },
                    pollingIntervalMs = castPollingIntervalMs,
                    isPlayingFlow = _castIsPlaying,
                    isCurrentlyPlaying = { _castIsPlaying.value },
                    onActive = ::updateCastState,
                ).launch()
            }
        }
    }

    /**
     * The GMS session fan-out, expressed over the integration's
     * [GoogleCastIntegration.SessionEvents] surface: the integration's
     * SessionManagerListener maps started+resumed → onConnected and
     * ended+resumeFailed → onDisconnected (it never inspects the CastSession
     * param — exactly the old googleSessionListener semantics).
     */
    private val googleSessionEvents = object : GoogleCastIntegration.SessionEvents {
        override fun onConnected() {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onDisconnected() {
            resetCastState()
            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
        }
    }

    init {
        googleCastIntegration.strategy?.let { strategies[STRATEGY_GOOGLE] = it }
        strategies[STRATEGY_DLNA] = dlnaCastStrategy
        strategies[STRATEGY_JELLYFIN] = jellyfinRemotePlayCastStrategy
        applyDefaultCastingStrategy()
        ensureGoogleSessionListener()
        startDeviceMerge()
        startPreferredRendererWatcher()
    }

    /**
     * Seeds the initial active strategy from the [CastingStrategy] preference.
     * PREFER_DLNA selects the DLNA strategy; everything else defaults to
     * Google Cast when the integration is available (the ASK variant surfaces
     * a chooser in the UI but still needs a discovery-capable default
     * strategy). On fdroid — integration Unavailable, no strategy at all — a
     * PREFER_CAST preference degrades to DLNA, the only renderer-capable
     * strategy left.
     */
    private fun applyDefaultCastingStrategy() {
        val pref = syncPlayCastStore.syncPlayCast.value.defaultCastingStrategy
        activeStrategyName = when (pref) {
            CastingStrategy.PREFER_DLNA -> STRATEGY_DLNA
            CastingStrategy.PREFER_CAST, CastingStrategy.ASK ->
                if (googleCastIntegration.strategy != null) STRATEGY_GOOGLE else STRATEGY_DLNA
        }
        observeStrategySession()
    }

    /**
     * Watches discovered devices and auto-connects to the user's
     * `preferredRenderer` when it appears, so frequently-used renderers are
     * selected without manual intervention.
     */
    private fun startPreferredRendererWatcher() {
        tasks.replace(PREFERRED_RENDERER) {
            coroutineScope.launch {
                _allDiscoveredDevices.combine(syncPlayCastStore.syncPlayCast) { devices, prefs ->
                    devices to prefs.preferredRenderer
                }
                    .distinctUntilChanged()
                    .collect { (devices, preferredName) ->
                        if (preferredName.isNullOrBlank() || isConnected) return@collect
                        val match = devices.firstOrNull { it.name == preferredName }
                        if (match != null) connect(context, match)
                    }
            }
        }
    }

    fun registerStrategy(name: String, strategy: CastStrategy) {
        strategies[name] = strategy
    }

    /**
     * Removes a strategy from the map. DECLARED FIX: when the unregistered
     * strategy is the ACTIVE one, the former body re-seated routing on
     * STRATEGY_GOOGLE unconditionally — which, when GOOGLE was the strategy
     * being unregistered (the fdroid/unavailable-integration edge), left
     * [activeStrategyName] pointing at a name the map no longer contains:
     * [activeStrategy] went null, every cast call became a silent no-op, and
     * the flow getters' DLNA fallback below had to paper over the gap. Now
     * the re-seat picks a strategy that REMAINS registered — Google when it
     * survives (the historical default), else DLNA (mirroring
     * [applyDefaultCastingStrategy]'s degrade-to-DLNA renderer-capable
     * fallback), else whatever is left — and re-observes the session edge so
     * the observer follows the re-seated strategy ([setActiveStrategy]
     * semantics). Unregistering a NON-active strategy leaves
     * [activeStrategyName] untouched — picking a new route (e.g. dropping
     * Google on fdroid while DLNA is active) must not clobber the user's
     * active renderer. Keeping the Google NAME with an empty map preserves
     * the historical default for a manager with no strategies at all; the
     * flow getters' DLNA fallback remains the honest reporter there.
     */
    fun unregisterStrategy(name: String) {
        strategies.remove(name)
        if (activeStrategyName != name) return
        activeStrategyName = when {
            strategies.containsKey(STRATEGY_GOOGLE) -> STRATEGY_GOOGLE
            strategies.isNotEmpty() ->
                if (strategies.containsKey(STRATEGY_DLNA)) STRATEGY_DLNA else strategies.keys.first()
            else -> STRATEGY_GOOGLE
        }
        observeStrategySession()
    }

    fun setActiveStrategy(name: String) {
        val previous = activeStrategyName
        activeStrategyName = name
        val prevStrategy = strategies[previous]
        if (prevStrategy != null) {
            prevStrategy.stopDiscovery()
        }
        observeStrategySession()
    }

    private fun observeStrategySession() {
        tasks.cancel(STRATEGY_OBSERVER)
        val strategy = strategies[activeStrategyName]
        // The Google strategy's session edges fan out through the integration
        // listener (see googleSessionEvents), never through this observer —
        // the identity check keeps the two paths from double-reporting.
        if (strategy != null && strategy !== googleCastIntegration.strategy) {
            var wasConnected = strategy.isConnected.value
            tasks.replace(STRATEGY_OBSERVER) {
                coroutineScope.launch {
                    strategy.isConnected.collect { connected ->
                        if (connected && !wasConnected) {
                            _sessionEvents.tryEmit(CastSessionEvent.Connected)
                        } else if (!connected && wasConnected) {
                            resetCastState()
                            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
                        }
                        wasConnected = connected
                    }
                }
            }
        }
    }

    private val activeStrategy: CastStrategy?
        get() = strategies[activeStrategyName]

    private fun startDeviceMerge() {
        tasks.replace(DEVICE_MERGE) {
            coroutineScope.launch {
                val flows = strategies.entries.map { (name, strategy) ->
                    strategy.discoveredDevices
                }
                if (flows.isEmpty()) return@launch
                combine(flows) { deviceLists ->
                    deviceLists.flatMap { it }
                }.collect { _allDiscoveredDevices.value = it }
            }
        }
    }

    val isCastAvailable: Boolean
        get() = activeStrategy?.isAvailable?.value == true

    val isConnected: Boolean
        get() = activeStrategy?.isConnected?.value == true

    val isConnecting: Boolean
        get() = activeStrategy?.isConnecting?.value == true

    // The flow fallbacks read DLNA: activeStrategy is only null when the map
    // holds no strategies at all (unregisterStrategy re-seats routing onto a
    // REMAINING strategy whenever one survives, so a null activeStrategy no
    // longer happens on the unregistered-Google edge it used to) — where
    // DLNA is the only renderer-capable strategy left to report.
    val isAvailableFlow: StateFlow<Boolean>
        get() = activeStrategy?.isAvailable ?: dlnaCastStrategy.isAvailable

    val isConnectedFlow: StateFlow<Boolean>
        get() = activeStrategy?.isConnected ?: dlnaCastStrategy.isConnected

    val isConnectingFlow: StateFlow<Boolean>
        get() = activeStrategy?.isConnecting ?: dlnaCastStrategy.isConnecting

    val discoveredDevices: StateFlow<List<CastDevice>>
        get() = _allDiscoveredDevices

    fun startDiscovery(context: android.content.Context) {
        strategies.values.forEach { it.startDiscovery(context) }
    }

    fun stopDiscovery() {
        strategies.values.forEach { it.stopDiscovery() }
    }

    fun connect(context: android.content.Context, device: CastDevice) {
        if (device.strategyName.isNotBlank() && device.strategyName != activeStrategyName) {
            setActiveStrategy(device.strategyName)
        }
        activeStrategy?.connect(context, device)
    }

    fun disconnect(context: android.content.Context) {
        activeStrategy?.disconnect(context)
    }

    fun play() {
        activeTransport.play()
    }

    fun pause() {
        activeTransport.pause()
    }

    fun seekTo(positionMs: Long) {
        activeTransport.seekTo(positionMs)
    }

    /**
     * Registers the GMS session fan-out through the injected integration —
     * the CastContext lookup and listener registration live behind the seam
     * (the integration is idempotent; Unavailable is a no-op).
     */
    private fun ensureGoogleSessionListener() {
        googleCastIntegration.ensureSessionListener(context, googleSessionEvents)
    }

    /**
     * Delegates to the injected integration: the isConnected guard,
     * CastPlayer construction (listener + SessionAvailabilityListener wiring)
     * and the StrictMode-wrapped CastContext lookups all live behind the
     * seam — INCLUDING the guard's ordering, which must run before the
     * player cache so a session that dropped since the last load reports
     * null instead of handing back the stale player (exactly the old
     * guard-before-cache semantics; the impl consults connectivity before
     * its own cache). This half caches non-null results — [castPlayer]
     * mirrors the integration's player until [teardownSharedState] nulls
     * both — and the [released] flag stays manager-owned: [acquireConsumer]
     * flips it back so a released manager builds a fresh player on demand.
     */
    private fun ensureCastPlayer(): Player? {
        if (released) return null
        val player = googleCastIntegration.ensureCastPlayer(
            context,
            castPlayerListener,
            onSessionUnavailable = { externalListener?.onPlaybackStateChanged(Player.STATE_ENDED) },
        )
        if (player != null) {
            castPlayer = player
        }
        return player
    }

    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long = 0,
        listener: Player.Listener,
        options: CastMediaOptions = CastMediaOptions(),
    ) {
        val transport = activeTransport
        val loaded = transport.loadMedia(mediaItem, startPositionMs, listener, options)
        if (loaded && !transport.ownsExternalListener) {
            externalListener?.let { castPlayer?.removeListener(it) }
            externalListener = null
        }
        if (loaded) {
            coroutineScope.launch { updateCastState() }
            toggleTicker()
        }
    }

    fun ensurePlayerReady() {
        ensureGoogleSessionListener()
        ensureCastPlayer()
    }

    fun release() {
        teardownSharedState()
        coroutineScope.cancel()
    }

    /**
     * Registers an active consumer of cast (e.g. the video player screen).
     * Pairs with [releaseConsumer]. The shared singleton is only fully torn
     * down once the *last* consumer releases, so a second feature using cast
     * is not forcibly killed when one consumer exits. Also flips the
     * [released] flag back so [ensureCastPlayer] will build a fresh
     * [CastPlayer] on demand.
     */
    fun acquireConsumer() {
        consumerRefCount.incrementAndGet()
        released = false
    }

    /**
     * Releases a consumer previously registered via [acquireConsumer]. When
     * the refcount reaches zero the shared cast state (cast player, session
     * listener, strategies, flows) is torn down — but the singleton's
     * [coroutineScope] is deliberately kept alive so a subsequent
     * [acquireConsumer] works without re-injecting a fresh scope. Previously
     * the video VM called [release] directly, which cancelled this scope and
     * left cast permanently broken for every subsequent video session.
     */
    fun releaseConsumer() {
        if (consumerRefCount.decrementAndGet() <= 0) {
            consumerRefCount.set(0)
            teardownSharedState()
        }
    }

    /**
     * Cancels every manager-owned background job (ticker, strategy observer,
     * device merge, preferred-renderer watcher) via the [TaskBundle].
     * Shared by the full teardown ([teardownSharedState]) and [softRelease].
     * [resetCastState] deliberately cancels only the ticker: it runs on
     * session edges where discovery and the observers must keep running.
     */
    private fun cancelJobs() {
        tasks.cancelAll()
    }

    private fun teardownSharedState() {
        released = true
        backgroundCasting.set(false)
        cancelJobs()
        castPlayer?.removeListener(castPlayerListener)
        externalListener?.let { castPlayer?.removeListener(it) }
        // The integration owns the CastPlayer and the CastContext
        // SessionManagerListener registration — its release tears both down
        // so neither keeps firing on a stale CastContext after logout/re-login
        // (without the listener removal the SessionManagerListener can fire on
        // the wrong user's sessions, and the singleton retains a reference to
        // a dead CastContext). Idempotent.
        googleCastIntegration.release()
        castPlayer = null
        externalListener = null
        resetCastState()
        strategies.values.forEach { it.stopDiscovery() }
        // Delegate to per-strategy release hooks (GoogleCastStrategy owns its
        // own session/state listeners; DLNA owns its own discovery sockets).
        strategies.values.forEach {
            runCatching { it.release() }
                .onFailure { Log.w(TAG, "Cast strategy release failed", it) }
        }
    }

    fun softRelease() {
        cancelJobs()
        strategies.values.forEach { it.stopDiscovery() }
    }

    /**
     * The session-edge reset: zero the ENTIRE now-playing family, not just
     * the transport quartet. DECLARED FIX: the former reset covered only
     * position / duration / isPlaying / buffered, so the LAST session's
     * title, subtitle and volume bled into the next one — a Jellyfin cast's
     * "Pilot · S1E1" kept rendering after the session died, and the dead
     * session's volume carried into the following session's first render.
     * Each flow returns to its construction default (volume 1f, title /
     * subtitle empty), the same defaults a fresh manager starts with.
     */
    private fun resetCastState() {
        tasks.cancel(TICKER)
        _castPositionMs.value = 0L
        _castDurationMs.value = 0L
        _castIsPlaying.value = false
        _castBufferedPositionMs.value = 0L
        _castVolume.value = 1f
        _castTitle.value = ""
        _castSubtitle.value = ""
    }
}

/**
 * Returns a copy of [this] [MediaItem] whose stream URI carries the cast
 * options as query params, so Google Cast requests the correct audio/subtitle
 * variant and respects the user's quality ceiling. Subtitle configurations and
 * metadata are preserved.
 *
 * Thin Android-side fold: the query-param construction itself lives in the
 * commonMain `String.withCastQueryParams` (CastQueryParams.kt, beside
 * [CastMediaOptions]) so the pure shaping is pinned off-platform; this
 * extension only rewrites the media3 item's URI through it.
 */
internal fun MediaItem.withCastOptions(options: CastMediaOptions): MediaItem {
    val currentUri = localConfiguration?.uri ?: return this
    val enrichedUri = currentUri.toString().withCastQueryParams(options)
    if (enrichedUri == currentUri.toString()) return this
    return buildUpon()
        .setUri(enrichedUri)
        .build()
}
