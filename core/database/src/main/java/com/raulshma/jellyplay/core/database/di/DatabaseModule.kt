package com.raulshma.jellyplay.core.database.di

import android.content.Context
import androidx.room.Room
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): JellyPlayDatabase = Room.databaseBuilder(
        context,
        JellyPlayDatabase::class.java,
        "jellyplay.db",
    ).build()

    @Provides
    fun provideServerDao(database: JellyPlayDatabase) = database.serverDao()

    @Provides
    fun provideDownloadDao(database: JellyPlayDatabase) = database.downloadDao()
}
