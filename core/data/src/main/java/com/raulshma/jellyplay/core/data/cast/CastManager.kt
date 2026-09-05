package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import androidx.compose.runtime.Stable
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

sealed class CastSessionEvent {
    data object Connected : CastSessionEvent()
    data object Disconnected : CastSessionEvent()
}

@OptIn(UnstableApi::class)
@Stable
class CastManager(
    private val context: Context,
    private val googleCastStrategy: GoogleCastStrategy,
    private val dlnaCastStrategy: DlnaCastStrategy,
    private val jellyfinRemotePlayCastStrategy: JellyfinRemotePlayCastStrategy,
    private val syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
) {
    companion object {
        private const val TAG = "CastManager"
        // How often the cast ticker re-checks whether playback resumed while
        // paused. Mirrors the playback engines' POSITION_PAUSED_RECHECK_MS
        // (internal to shared/feature/player-video, so redeclared here).
        private const val CAST_PAUSED_RECHECK_MS = 2_500L
        const val STRATEGY_GOOGLE = "google"
        const val STRATEGY_LIBVLC = "libvlc"
        const val STRATEGY_DLNA = "dlna"
        const val STRATEGY_JELLYFIN = "jellyfin"
    }

    private val strategies = mutableMapOf<String, CastStrategy>()
    private var activeStrategyName: String = STRATEGY_GOOGLE

    val currentStrategyName: String get() = activeStrategyName

    private var castPlayer: CastPlayer? = null
    private var sessionAvailabilityListener: SessionAvailabilityListener? = null
    private var externalListener: Player.Listener? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var strategyObserverJob: Job? = null
    private var deviceMergeJob: Job? = null
    private var preferredRendererJob: Job? = null

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
     * every other active strategy (Google Cast, the libvlc fallback, ad-hoc
     * registrations) rides the manager-owned CastPlayer via
     * [localCastPlayerTransport] — exactly what the old when-chain else-arms
     * did.
     */
    private val activeTransport: CastStrategy
        get() = when (activeStrategyName) {
            STRATEGY_DLNA -> dlnaCastStrategy
            STRATEGY_JELLYFIN -> jellyfinRemotePlayCastStrategy
            else -> localCastPlayerTransport
        }

    /**
     * Adapter driving the manager-owned CastPlayer through the [CastStrategy]
     * transport surface, so transport dispatch has one path. Discovery /
     * session members delegate to [googleCastStrategy] and are never routed
     * through this adapter — the strategies map stays authoritative for those.
     */
    private val localCastPlayerTransport: CastStrategy = object : CastStrategy by googleCastStrategy {
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
            externalListener = listener
            val player = ensureCastPlayer() ?: return false
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

    fun isActive(): Boolean = castPlayer != null && isConnected

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

    private suspend fun updateCastState() {
        if (activeStrategyName == STRATEGY_DLNA) {
            dlnaCastStrategy.refreshPlaybackState()
            _castPositionMs.value = dlnaCastStrategy.rendererPositionMs.value
            _castDurationMs.value = dlnaCastStrategy.rendererDurationMs.value
            _castIsPlaying.value = dlnaCastStrategy.rendererIsPlaying.value
            _castVolume.value = dlnaCastStrategy.rendererVolume.value
            return
        }
        if (activeStrategyName == STRATEGY_JELLYFIN) {
            jellyfinRemotePlayCastStrategy.refreshPlaybackState()
            _castPositionMs.value = jellyfinRemotePlayCastStrategy.positionMs.value
            _castDurationMs.value = jellyfinRemotePlayCastStrategy.durationMs.value
            _castIsPlaying.value = jellyfinRemotePlayCastStrategy.isPlaying.value
            _castVolume.value = jellyfinRemotePlayCastStrategy.volume.value
            _castTitle.value = jellyfinRemotePlayCastStrategy.nowPlayingTitle.value
            _castSubtitle.value = jellyfinRemotePlayCastStrategy.nowPlayingSubtitle.value
            return
        }
        val player = castPlayer ?: return
        val snapshot = withContext(Dispatchers.Default) {
            CastPlayerSnapshot(
                position = player.currentPosition.coerceAtLeast(0),
                duration = player.duration.coerceAtLeast(0),
                buffered = player.bufferedPosition.coerceAtLeast(0),
                isPlaying = player.isPlaying,
                volume = player.volume,
            )
        }
        _castPositionMs.value = snapshot.position
        _castDurationMs.value = snapshot.duration
        _castBufferedPositionMs.value = snapshot.buffered
        _castIsPlaying.value = snapshot.isPlaying
        _castVolume.value = snapshot.volume
    }

    private fun toggleTicker() {
        tickerJob?.cancel()
        val isDlna = activeStrategyName == STRATEGY_DLNA
        val isJellyfin = activeStrategyName == STRATEGY_JELLYFIN
        val shouldTick = when {
            isDlna -> dlnaCastStrategy.isConnected.value
            isJellyfin -> jellyfinRemotePlayCastStrategy.isConnected.value
            else -> castPlayer != null && castPlayer?.isPlaying == true
        }
        if (shouldTick) {
            val interval = if (isDlna || isJellyfin) 1000L else 500L
            tickerJob = coroutineScope.launch {
                while (isActive) {
                    // While paused, stop polling and wait for playback to resume
                    // (bounded so a DLNA/Jellyfin strategy flip is still picked
                    // up). Mirrors EnginePositionTicker's bounded paused-wait so
                    // a paused cast session no longer wakes the CPU at 1–2 Hz.
                    if (!_castIsPlaying.value) {
                        withTimeoutOrNull(CAST_PAUSED_RECHECK_MS) {
                            _castIsPlaying.first { it }
                        }
                    }
                    delay(interval)
                    updateCastState()
                }
            }
        }
    }

    private val googleSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            resetCastState()
            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            resetCastState()
            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    @Volatile
    private var googleSessionListenerRegistered = false

    init {
        strategies[STRATEGY_GOOGLE] = googleCastStrategy
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
     * Google Cast (the ASK variant surfaces a chooser in the UI but still
     * needs a discovery-capable default strategy).
     */
    private fun applyDefaultCastingStrategy() {
        val pref = syncPlayCastStore.syncPlayCast.value.defaultCastingStrategy
        activeStrategyName = when (pref) {
            CastingStrategy.PREFER_DLNA -> STRATEGY_DLNA
            CastingStrategy.PREFER_CAST, CastingStrategy.ASK -> STRATEGY_GOOGLE
        }
        observeStrategySession()
    }

    /**
     * Watches discovered devices and auto-connects to the user's
     * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.preferredRenderer]
     * when it appears, so frequently-used renderers are selected without
     * manual intervention.
     */
    private fun startPreferredRendererWatcher() {
        preferredRendererJob?.cancel()
        preferredRendererJob = coroutineScope.launch {
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

    fun registerStrategy(name: String, strategy: CastStrategy) {
        strategies[name] = strategy
    }

    fun unregisterStrategy(name: String) {
        strategies.remove(name)
        if (activeStrategyName == name) {
            activeStrategyName = STRATEGY_GOOGLE
        }
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
        strategyObserverJob?.cancel()
        strategyObserverJob = null
        val strategy = strategies[activeStrategyName]
        if (strategy != null && strategy !== googleCastStrategy) {
            var wasConnected = strategy.isConnected.value
            strategyObserverJob = coroutineScope.launch {
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

    private val activeStrategy: CastStrategy?
        get() = strategies[activeStrategyName]

    private fun startDeviceMerge() {
        deviceMergeJob?.cancel()
        deviceMergeJob = coroutineScope.launch {
            val flows = strategies.entries.map { (name, strategy) ->
                strategy.discoveredDevices
            }
            if (flows.isEmpty()) return@launch
            combine(flows) { deviceLists ->
                deviceLists.flatMap { it }
            }.collect { _allDiscoveredDevices.value = it }
        }
    }

    val isCastAvailable: Boolean
        get() = activeStrategy?.isAvailable?.value == true

    val isConnected: Boolean
        get() = activeStrategy?.isConnected?.value == true

    val isConnecting: Boolean
        get() = activeStrategy?.isConnecting?.value == true

    val isAvailableFlow: StateFlow<Boolean>
        get() = activeStrategy?.isAvailable ?: googleCastStrategy.isAvailable

    val isConnectedFlow: StateFlow<Boolean>
        get() = activeStrategy?.isConnected ?: googleCastStrategy.isConnected

    val isConnectingFlow: StateFlow<Boolean>
        get() = activeStrategy?.isConnecting ?: googleCastStrategy.isConnecting

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

    private fun ensureGoogleSessionListener() {
        if (googleSessionListenerRegistered) return
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
            castContext.sessionManager.addSessionManagerListener(googleSessionListener, CastSession::class.java)
            googleSessionListenerRegistered = true
        } catch (_: Exception) {}
    }

    private fun ensureCastPlayer(): CastPlayer? {
        if (!googleCastStrategy.isConnected.value) return null
        if (castPlayer != null) return castPlayer
        if (released) return null
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
            sessionAvailabilityListener = object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {}
                override fun onCastSessionUnavailable() {
                    externalListener?.onPlaybackStateChanged(Player.STATE_ENDED)
                }
            }
            castPlayer = CastPlayer(castContext).apply {
                addListener(castPlayerListener)
                setSessionAvailabilityListener(sessionAvailabilityListener!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create CastPlayer", e)
        }
        return castPlayer
    }

    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long = 0,
        listener: Player.Listener,
        options: CastMediaOptions = CastMediaOptions(),
    ) {
        val transport = activeTransport
        val loaded = transport.loadMedia(mediaItem, startPositionMs, listener, options)
        // Renderer-protocol transports have no Player to fan events out to, so
        // the manager's external listener is dropped instead of handed over.
        if (loaded && transport !== localCastPlayerTransport) {
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
     * Cancels the manager-owned background jobs (ticker, strategy observer,
     * device merge, preferred-renderer watcher). Shared by the full teardown
     * ([teardownSharedState]) and [softRelease]. [resetCastState] deliberately
     * cancels only the ticker: it runs on session edges where discovery and
     * the observers must keep running.
     */
    private fun cancelJobs() {
        tickerJob?.cancel()
        tickerJob = null
        strategyObserverJob?.cancel()
        strategyObserverJob = null
        deviceMergeJob?.cancel()
        deviceMergeJob = null
        preferredRendererJob?.cancel()
        preferredRendererJob = null
    }

    private fun teardownSharedState() {
        released = true
        backgroundCasting.set(false)
        cancelJobs()
        castPlayer?.removeListener(castPlayerListener)
        externalListener?.let { castPlayer?.removeListener(it) }
        castPlayer?.release()
        castPlayer = null
        sessionAvailabilityListener = null
        externalListener = null
        resetCastState()
        strategies.values.forEach { it.stopDiscovery() }
        // Remove the CastContext.SessionManagerListener so it doesn't keep
        // firing on a stale CastContext after logout/re-login. Without this
        // the listener can fire on the wrong user's sessions, and the
        // Singleton retains a reference to a dead CastContext.
        if (googleSessionListenerRegistered) {
            try {
                val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
                castContext.sessionManager.removeSessionManagerListener(
                    googleSessionListener, CastSession::class.java,
                )
            } catch (_: Exception) {
                // Cast SDK may already be torn down.
            }
            googleSessionListenerRegistered = false
        }
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

    private fun resetCastState() {
        tickerJob?.cancel()
        tickerJob = null
        _castPositionMs.value = 0L
        _castDurationMs.value = 0L
        _castIsPlaying.value = false
        _castBufferedPositionMs.value = 0L
    }
}

private data class CastPlayerSnapshot(
    val position: Long,
    val duration: Long,
    val buffered: Long,
    val isPlaying: Boolean,
    val volume: Float,
)

/**
 * Appends the active track / quality selections onto a Jellyfin stream URL as
 * standard query params (`AudioStreamIndex`, `SubtitleStreamIndex`,
 * `MaxVideoBitrate`), used by the DLNA and Google Cast transports which both
 * consume a server URL. Existing params are preserved; only non-null options
 * are added. Used for the cast-handoff fix.
 */
internal fun String.withCastQueryParams(options: CastMediaOptions): String {
    if (options.audioStreamIndex == null &&
        options.subtitleStreamIndex == null &&
        options.maxVideoBitrate == null
    ) {
        return this
    }
    val separator = if ('?' in this) "&" else "?"
    val params = buildList {
        options.audioStreamIndex?.let { add("AudioStreamIndex=${it}") }
        options.subtitleStreamIndex?.let { add("SubtitleStreamIndex=${it}") }
        options.maxVideoBitrate?.let { add("MaxVideoBitrate=${it}") }
    }
    return this + separator + params.joinToString("&")
}

/**
 * Returns a copy of [this] [MediaItem] whose stream URI carries the cast
 * options as query params, so Google Cast requests the correct audio/subtitle
 * variant and respects the user's quality ceiling. Subtitle configurations and
 * metadata are preserved.
 */
internal fun MediaItem.withCastOptions(options: CastMediaOptions): MediaItem {
    val currentUri = localConfiguration?.uri ?: return this
    val enrichedUri = currentUri.toString().withCastQueryParams(options)
    if (enrichedUri == currentUri.toString()) return this
    return buildUpon()
        .setUri(enrichedUri)
        .build()
}
