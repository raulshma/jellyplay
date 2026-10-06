package com.raulshma.jellyplay.core.notification.di

import android.content.Context
import com.raulshma.jellyplay.core.notification.channel.NotificationChannelManager
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * core-side Hilt extinction: Koin owns the :core:notification
 * singletons. Ctor shapes are byte-identical to the old Hilt graph; the old
 * `@ApplicationScope CoroutineScope` edge (NotificationReconnectListener)
 * maps onto [DatastoreQualifiers.applicationScope]. The dead
 * NotificationModule.provideNotificationManagerCompat provider (zero
 * injectors) died with the module instead of gaining a Koin def.
 *
 * The :app consumes these singles directly from its startKoin module
 * list (app Hilt went extinct with).
 */
fun androidNotificationModule(context: Context): Module = module {

    single { NotificationChannelManager(context = context) }

    single {
        NotificationDispatcher(
            context = context,
            channelManager = get(),
        )
    }

    // The companion-plugin's SSE new-media pushes (ADR 0010) ride the same
    // tray path. The events session controller resolves this seam with
    // getOrNull and degrades to log-only on platforms that register nothing
    // (desktop has no tray-notification surface).
    single<com.raulshma.jellyplay.core.data.notification.JellyPlayNewMediaNotifier> {
        val dispatcher: NotificationDispatcher = get()
        com.raulshma.jellyplay.core.data.notification.JellyPlayNewMediaNotifier { event ->
            dispatcher.dispatchPluginNewMedia(event)
        }
    }

    // The push wave's platform half: the UnifiedPush connector over the user's
    // distributor app (ntfy & co). The commonMain push repository resolves
    // this seam getOrNull — desktop registers nothing and parks on
    // NoDistributor; the push receiver routes the connector's broadcasts into
    // the repository / the dispatcher here.
    single<com.raulshma.jellyplay.core.data.repository.JellyPushDistributor> {
        com.raulshma.jellyplay.core.push.AndroidJellyPushDistributor(context = context)
    }

    single {
        NotificationScheduler(
            context = context,
            notificationStore = get(),
        )
    }

    single {
        NotificationReconnectListener(
            networkMonitor = get(),
            offlineModeManager = get(),
            notificationScheduler = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
