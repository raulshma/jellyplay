package com.raulshma.jellyplay.widget

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import org.koin.mp.KoinPlatform

/**
 * [WorkerFactory] for the two app-widget recommendation workers (wave 8B —
 * Hilt removal: the former Hilt worker assisted-injection pair became plain
 * CoroutineWorker constructors, so WorkManager's reflection fallback can no
 * longer build them). Registered in JellyPlayApplication's
 * DelegatingWorkerFactory alongside the core-data and notification factories
 * (wave 8A). Explicit-when on the class name — unknown classes return null
 * so the delegate chain keeps walking.
 */
class AppWidgetWorkerFactory : WorkerFactory() {

    override fun createWorker(
        context: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val koin = KoinPlatform.getKoin() ?: return null
        return when (workerClassName) {
            LibraryRecommendationsWidgetWorker::class.java.name -> {
                val widgetDataStore: WidgetDataStore = koin.get()
                val mediaRepository: MediaRepository = koin.get()
                val playbackRepository: PlaybackRepository = koin.get()
                val authRepository: AuthRepository = koin.get()
                LibraryRecommendationsWidgetWorker(
                    appContext = context.applicationContext,
                    params = workerParameters,
                    widgetDataStore = widgetDataStore,
                    mediaRepository = mediaRepository,
                    playbackRepository = playbackRepository,
                    authRepository = authRepository,
                )
            }

            SeerrRecommendationsWidgetWorker::class.java.name -> {
                val widgetDataStore: WidgetDataStore = koin.get()
                val seerrPreferencesStore: SeerrPreferencesStore = koin.get()
                val seerrRepository: SeerrRepository = koin.get()
                SeerrRecommendationsWidgetWorker(
                    appContext = context.applicationContext,
                    params = workerParameters,
                    widgetDataStore = widgetDataStore,
                    seerrPreferencesStore = seerrPreferencesStore,
                    seerrRepository = seerrRepository,
                )
            }

            else -> null
        }
    }
}
