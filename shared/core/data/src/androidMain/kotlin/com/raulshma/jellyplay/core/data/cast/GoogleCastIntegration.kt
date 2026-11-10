package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import androidx.media3.common.Player

/**
 * The Google Cast seam between [CastManager] and the GMS cast stack
 * (CastContext / CastPlayer / SessionManagerListener). CastManager owns
 * strategy routing, state fan-out and transport dispatch; this interface
 * owns everything that needs Play services classes on the classpath.
 *
 * The GMS half lives behind this seam so the fdroid build — which ships no
 * Play services at all — compiles core:data unchanged: fdroid binds
 * [Unavailable], and every member degrades to its documented no-op. The
 * store binding ([com.raulshma.jellyplay.core.data.cast.GoogleCastIntegrationImpl]
 * plus [com.raulshma.jellyplay.core.data.cast.GoogleCastStrategy]) lives in
 * the app module's store source set.
 */
interface GoogleCastIntegration {

    /**
     * Discovery/session strategy, or null when the integration is unavailable
     * (fdroid). Non-null on store: CastManager registers it in its strategies
     * map under [CastStrategyNames.GOOGLE].
     */
    val strategy: CastStrategy?

    /**
     * Main-thread pre-init (hasSystemFeature probe + one-time CastContext
     * warm). True → the hidden route button may compose. Unavailable: always
     * false.
     */
    fun preinit(context: Context): Boolean

    /**
     * Registers the session fan-out the manager used to own. GMS maps
     * started+resumed → [SessionEvents.onConnected], ended+resumeFailed →
     * [SessionEvents.onDisconnected]. Idempotent until [release]; re-registers
     * after a release. Unavailable: no-op.
     */
    fun ensureSessionListener(context: Context, events: SessionEvents)

    /**
     * Returns the receiver-side player, creating it on demand (media3
     * CastPlayer under the hood). [playerListener] is attached at creation;
     * [onSessionUnavailable] mirrors
     * SessionAvailabilityListener.onCastSessionUnavailable. Null when the
     * strategy reports no connected session / the player cannot be created /
     * unavailable.
     */
    fun ensureCastPlayer(
        context: Context,
        playerListener: Player.Listener,
        onSessionUnavailable: () -> Unit,
    ): Player?

    /**
     * Tears down integration-owned listeners/player. Idempotent — the
     * manager's teardown paths may invoke it more than once across the
     * application lifecycle. Unavailable: no-op.
     */
    fun release()

    /**
     * Connected/disconnected fan-out surface (mirrors the old
     * googleSessionListener semantics the manager hung off the GMS
     * SessionManagerListener).
     */
    interface SessionEvents {
        fun onConnected()
        fun onDisconnected()
    }

    /**
     * The fdroid binding: no Play services on the classpath, so every member
     * is a no-op — [strategy] is null, [preinit] reports false, and
     * [ensureCastPlayer] returns null. CastManager binds this via Koin's
     * getOrNull when the store graph is absent.
     */
    object Unavailable : GoogleCastIntegration {
        override val strategy: CastStrategy? = null
        override fun preinit(context: Context): Boolean = false
        override fun ensureSessionListener(context: Context, events: SessionEvents) = Unit
        override fun ensureCastPlayer(
            context: Context,
            playerListener: Player.Listener,
            onSessionUnavailable: () -> Unit,
        ): Player? = null
        override fun release() = Unit
    }
}
