package com.raulshma.jellyplay.core.push

import android.content.Context
import com.raulshma.jellyplay.core.data.di.koin
import com.raulshma.jellyplay.core.data.receiver.runWithBroadcastScope
import com.raulshma.jellyplay.core.data.repository.JellyPushRepository
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * The UnifiedPush connector's BroadcastReceiver for JellyPlay (the plugin's
 * push support): the distributor delivers NEW_ENDPOINT / MESSAGE / UNREGISTERED /
 * REGISTRATION_FAILED broadcasts here (manifest-declared with the connector's
 * action intent filter, exported — the sender is the distributor app).
 *
 * Routing:
 *  - endpoint/unregistration/failed-registration → [JellyPushRepository]'s
 *    state machine (persist + `registerDevice` with the push field, or
 *    detach + surface NoDistributor);
 *  - the SILENT `sync-nudge` kind (registry v7's caps-gated data-only push)
 *    → the sync engine's requestSync — never a notification, never gated on
 *    the `push` toggle (it targets the SYNC toggle);
 *  - other messages → the `push` gate (ADR 0010 — the registry is the ONLY
 *    gate) is re-checked one-shot before anything surfaces, then
 *    [NotificationDispatcher.dispatchPluginPush]: the generic JSON payload
 *    (`title`/`body`/`kind`/`itemId`?) maps onto the same tray surface the
 *    SSE new-media path uses. A message that lands while the user's toggle
 *    reads off (or the server dropped the `push` key) drops quietly — a
 *    stale endpoint must keep notifying nobody.
 *
 * The message verdict itself is [PushMessageIntake.verdict] — the GLOSSARY
 * "Receiver intake fold": nudge/gate decoded pure (JVM-tested), this body is
 * fold + dispatch only.
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

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        runWithBroadcastScope(LOG_TAG) {
            pushRepository.onNewEndpoint(endpoint.url, instance)
        }
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        runWithBroadcastScope(LOG_TAG) {
            pushRepository.onRegistrationFailed(reason.name, instance)
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        runWithBroadcastScope(LOG_TAG) {
            pushRepository.onUnregistered(instance)
        }
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        runWithBroadcastScope(LOG_TAG) {
            val content = message.content.decodeToString()

            // The gate is resolved lazily and only for non-nudges: `&&`
            // short-circuits, so a nudge never pays the (DataStore-reading)
            // `push` gate probe — it targets the SYNC toggle, not the push
            // one, and the fold re-checks the nudge question first anyway.
            // The SILENT nudge fold itself (requestSync, the engine's own
            // gates — opt-in, probe, mutex — authoritative) is the push twin
            // of the JellyPlayLiveResyncConnector fold: no tray notification,
            // a disabled engine makes requestSync the documented no-op.
            when (val verdict = PushMessageIntake.verdict(
                payloadJson = content,
                pushGateOpen = !PushMessageIntake.isSyncNudge(content) &&
                    featureGate.isAvailableNow(JellyPlayPluginFeatures.Push),
            )) {
                is PushMessageVerdict.SyncNudge -> {
                    android.util.Log.d(LOG_TAG, "sync-nudge received: requesting sync")
                    koin().get<ProfileSyncRepository>().requestSync()
                }
                is PushMessageVerdict.GateClosed -> {
                    // The gate re-check (ADR 0010): a delivered message is not
                    // proof the user still wants push — the toggle may have
                    // gone off while the detach POST was in flight (or the
                    // endpoint was stale).
                    android.util.Log.d(LOG_TAG, "dropping push message: gate closed")
                }
                is PushMessageVerdict.SurfaceInTray ->
                    notificationDispatcher.dispatchPluginPush(content)
            }
        }
    }

    companion object {
        private const val LOG_TAG = "JellyPlayUnifiedPush"
    }
}
