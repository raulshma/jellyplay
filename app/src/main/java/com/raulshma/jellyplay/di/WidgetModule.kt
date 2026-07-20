package com.raulshma.jellyplay.di

import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.widget.ContinueWatchingBroadcasterImpl
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import com.raulshma.jellyplay.widget.WidgetWorkSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetWorkScheduler(impl: WidgetWorkSchedulerImpl): WidgetWorkScheduler

    @Binds
    @Singleton
    abstract fun bindContinueWatchingBroadcaster(impl: ContinueWatchingBroadcasterImpl): ContinueWatchingBroadcaster
}
