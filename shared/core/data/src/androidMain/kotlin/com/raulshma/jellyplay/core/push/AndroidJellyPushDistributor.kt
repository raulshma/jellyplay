package com.raulshma.jellyplay.core.push

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.JellyPushDistributor
import org.unifiedpush.android.connector.UnifiedPush

/**
 * The Android [JellyPushDistributor] over the UnifiedPush connector: talks to
 * whichever distributor app the user has installed (ntfy & co). Registered in
 * the Android notification Koin module (desktop registers nothing, so the
 * push repository's getOrNull seam resolves null there and the machine parks
 * on NoDistributor).
 *
 * [UnifiedPush.register] is fire-and-forget: it broadcasts the registration
 * request to the saved distributor and the answer lands asynchronously
 * through [JellyPlayUnifiedPushReceiver] (NEW_ENDPOINT → the push
 * repository's onNewEndpoint). No distributor saved → the broadcast is a
 * no-op, which is why [hasDistributor] gates the request upstream.
 */
class AndroidJellyPushDistributor(private val context: Context) : JellyPushDistributor {

    override fun hasDistributor(): Boolean = UnifiedPush.getDistributors(context).isNotEmpty()

    override fun register(instance: String) {
        // The messageForDistributor label the distributor UI shows for this
        // registration (the ntfy app lists subscriptions by it).
        UnifiedPush.register(context, instance, "JellyPlay")
    }

    override fun unregister(instance: String) {
        UnifiedPush.unregister(context, instance)
    }
}
