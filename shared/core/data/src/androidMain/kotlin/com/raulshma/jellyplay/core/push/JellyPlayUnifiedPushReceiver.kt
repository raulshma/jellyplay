package com.raulshma.jellyplay.core.push

import android.content.Context
import com.raulshma.jellyplay.core.data.di.koin
import com.raulshma.jellyplay.core.data.repository.JellyPushRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * The UnifiedPush connector's BroadcastReceiver for JellyPlay (the plugin's
 * push wave): the distributor delivers NEW_ENDPOINT / MESSAGE / UNREGISTERED /
 * REGISTRATION_FAILED broadcasts here (manifest-declared with the connector's
 * action intent filter, exported — the sender is the distributor app).
 *
 * Routing:
 *  - endpoint/unregistration/failed-registration → [JellyPushRepository]'s
 *    state machine (persist + `registerDevice` with the push field, or
 *    detach + surface NoDistributor);
 *  - message → the `push` gate (ADR 0010 — the registry is the ONLY gate) is
 *    re-checked one-shot before anything surfaces, then
 *    [NotificationDispatcher.dispatchPluginPush]: the generic JSON payload
 *    (`title`/`body`/`kind`/`itemId`?) maps onto the same tray surface the
 *    SSE new-media path uses. A message that lands while the user's toggle
 *    reads off (or the server dropped the `push` key) drops quietly — a
 *    stale endpoint must keep notifying nobody.
 *
 * Deprecation note: the connector steers new code toward its PushService
 * shape; MessagingReceiver stays the supported receiver path (the embedded
 * forwarder yields to a manifest receiver with this intent filter) and is the
 * form that fits the app's existing broadcast-receiver idioms
 * (goAsync + Koin resolve, see DownloadActionReceiver).
 *
 * Deps resolve lazily from the Koin container on first use — the app
 * composition root starts it long before any broadcast can arrive.
 */
class JellyPlayUnifiedPushReceiver : MessagingReceiver() {

    private val pushRepository: JellyPushRepository by lazy { koin().get() }
    private val notificationDispatcher: NotificationDispatcher by lazy { koin().get() }
    private val featureGate: JellyPlayFeatureGate by lazy { koin().get() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        launchPending {
            pushRepository.onNewEndpoint(endpoint.url, instance)
        }
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        launchPending {
            pushRepository.onRegistrationFailed(reason.name, instance)
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        launchPending {
            pushRepository.onUnregistered(instance)
        }
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        launchPending {
            // The gate re-check (ADR 0010): a delivered message is not proof
            // the user still wants push — the toggle may have gone off while
            // the detach POST was in flight (or the endpoint was stale).
            if (!featureGate.isAvailableNow(JellyPlayPluginFeatures.Push)) {
                android.util.Log.d("JellyPlayUnifiedPush", "dropping push message: gate closed")
                return@launchPending
            }
            notificationDispatcher.dispatchPluginPush(message.content.decodeToString())
        }
    }

    /**
     * Runs [block] on this receiver's coroutine scope, keeping the broadcast
     * alive ([goAsync]) until it completes — the DownloadActionReceiver /
     * NotificationActionReceiver idiom (the repository's paths hit DataStore
     * and the network, neither allowed on the main thread).
     */
    private fun launchPending(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                // A push callback is best-effort live signal — never crash the
                // process for one; the inbox/messages surfaces are durable.
                android.util.Log.w("JellyPlayUnifiedPush", "push callback failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
