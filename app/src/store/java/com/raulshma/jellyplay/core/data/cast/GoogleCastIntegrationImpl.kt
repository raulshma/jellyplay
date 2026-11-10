package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/**
 * The GMS half of the [GoogleCastIntegration] seam — lives only on the store
 * classpath (the fdroid variant never sees this file, so core:data compiles
 * GMS-free everywhere). Owns exactly what CastManager used to touch directly:
 * the one-time CastContext pre-init, the CastContext.SessionManagerListener
 * session fan-out, and the receiver-side media3 CastPlayer plus its
 * SessionAvailabilityListener.
 *
 * Every CastContext touch stays wrapped in [withCastDiskReadsPermitted]: the
 * Cast SDK's one-time initialization loads its Dynamite dex modules from disk
 * on the calling (main) thread — the API has no off-thread variant — so these
 * third-party reads are permitted rather than fixed. Release builds install
 * no StrictMode policy, making the permit a no-op there.
 */
@OptIn(UnstableApi::class)
class GoogleCastIntegrationImpl(
    private val context: Context,
    private val googleCastStrategy: GoogleCastStrategy,
) : GoogleCastIntegration {

    companion object {
        private const val TAG = "GoogleCastIntegration"
    }

    override val strategy: CastStrategy? get() = googleCastStrategy

    /**
     * Main-thread pre-init: the hasSystemFeature binder probe is cheap but the
     * CastContext.getSharedInstance call is the one-time Dynamite dex load —
     * callers kick this off on Dispatchers.Main (NOT Main.immediate, which
     * would execute inline inside onCreate and put the dex I/O into TTID) so
     * it interleaves with the first composed frames instead of serializing
     * ahead of them. Every later getSharedInstance caller (the hidden route
     * button, CastManager via this integration, [GoogleCastStrategy]) hits the
     * cached singleton afterwards.
     *
     * The failure catch is plain runCatching, not
     * runCatchingRethrowingCancellation: the block has no suspension point
     * (getSharedInstance is synchronous), so no CancellationException can
     * originate inside it — the cancellation-rethrow variant would require a
     * suspend signature for no behavioral difference.
     */
    override fun preinit(context: Context): Boolean =
        context.packageManager.hasSystemFeature("com.google.android.gms.cast") &&
            runCatching {
                withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
            }.isSuccess

    /**
     * The session fan-out CastManager hung off its own googleSessionListener:
     * the callbacks never inspect the CastSession param — started+resumed
     * report Connected, ended+resumeFailed report Disconnected, everything
     * else is a no-op. Registered against the cached CastContext once (the
     * *Registered flag makes re-registration after [release] the only path
     * back in).
     */
    @Volatile
    private var sessionListenerRegistered = false

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            sessionEvents?.onConnected()
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            sessionEvents?.onDisconnected()
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            sessionEvents?.onConnected()
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            sessionEvents?.onDisconnected()
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    /** The fan-out captured at the successful registration — null until then. */
    @Volatile
    private var sessionEvents: GoogleCastIntegration.SessionEvents? = null

    override fun ensureSessionListener(context: Context, events: GoogleCastIntegration.SessionEvents) {
        if (sessionListenerRegistered) return
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
            sessionEvents = events
            castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            sessionListenerRegistered = true
        } catch (_: Exception) {}
    }

    /**
     * The receiver-side player, created on demand exactly as CastManager used
     * to: only against a connected session (the strategy's isConnected guard),
     * once per lifetime between releases, with the manager's [playerListener]
     * attached at creation and [onSessionUnavailable] wired as the
     * SessionAvailabilityListener's unavailable callback (available stays a
     * no-op — the manager keys off playback state, not the availability edge).
     */
    @Volatile
    private var castPlayer: CastPlayer? = null

    private var sessionAvailabilityListener: SessionAvailabilityListener? = null

    override fun ensureCastPlayer(
        context: Context,
        playerListener: Player.Listener,
        onSessionUnavailable: () -> Unit,
    ): Player? {
        if (!googleCastStrategy.isConnected.value) return null
        if (castPlayer != null) return castPlayer
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
            sessionAvailabilityListener = object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {}
                override fun onCastSessionUnavailable() {
                    onSessionUnavailable()
                }
            }
            castPlayer = CastPlayer(castContext).apply {
                addListener(playerListener)
                setSessionAvailabilityListener(sessionAvailabilityListener!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create CastPlayer", e)
        }
        return castPlayer
    }

    /**
     * Releases the integration-owned CastPlayer and removes the
     * SessionManagerListener so it doesn't keep firing on a stale CastContext
     * after logout/re-login (without the removal the listener can fire on the
     * wrong user's sessions, and the CastContext singleton retains a reference
     * through a dead manager). Idempotent: the *Registered flag and the null
     * player make repeat calls no-ops.
     */
    override fun release() {
        castPlayer?.release()
        castPlayer = null
        sessionAvailabilityListener = null
        if (sessionListenerRegistered) {
            try {
                val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
                castContext.sessionManager.removeSessionManagerListener(
                    sessionListener, CastSession::class.java,
                )
            } catch (_: Exception) {
                // Cast SDK may already be torn down.
            }
            sessionListenerRegistered = false
            sessionEvents = null
        }
    }
}
