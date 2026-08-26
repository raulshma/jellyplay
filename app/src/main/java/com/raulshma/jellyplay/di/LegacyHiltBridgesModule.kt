package com.raulshma.jellyplay.di

import com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.koin.mp.KoinPlatform

/**
 * TRANSITIONAL, deleted by app Hilt extinction (builder 8B).
 *
 * App-side sibling of :core:data's di/LegacyHiltBridgesModule: wave 8A moved
 * construction of these types to Koin (androidNotificationModule /
 * androidCoreUiModule), but they live in modules :core:data cannot see
 * (module cycle: :core:notification depends on :core:data), so their Hilt
 * bridges sit here in the composition root until :app's own Hilt removal
 * deletes this file. Same parameterless-fetch shape — Hilt never constructs
 * anything, both frameworks share the Koin singles.
 */
@Module
@InstallIn(SingletonComponent::class)
object LegacyHiltBridgesModule {

    private fun koin() = KoinPlatform.getKoin()
        ?: error("Koin not started — startKoin must run before super.onCreate()")

    @Provides
    @Singleton
    fun provideUserMessageBus(): UserMessageBus = koin().get()

    @Provides
    @Singleton
    fun provideNotificationScheduler(): NotificationScheduler = koin().get()

    @Provides
    @Singleton
    fun provideNotificationReconnectListener(): NotificationReconnectListener = koin().get()
}
