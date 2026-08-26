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
 * Wave 8A core-side Hilt extinction: Koin owns the :core:notification
 * singletons. Ctor shapes are byte-identical to the old Hilt graph; the old
 * `@ApplicationScope CoroutineScope` edge (NotificationReconnectListener)
 * maps onto [DatastoreQualifiers.applicationScope]. The dead
 * NotificationModule.provideNotificationManagerCompat provider (zero
 * injectors) died with the module instead of gaining a Koin def.
 *
 * The :app consumes these singles directly from its startKoin module
 * list (app Hilt went extinct with wave 8B).
 */
fun androidNotificationModule(context: Context): Module = module {

    single { NotificationChannelManager(context = context) }

    single {
        NotificationDispatcher(
            context = context,
            channelManager = get(),
        )
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
