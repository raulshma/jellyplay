package com.raulshma.jellyplay.screensaver

import android.service.dreams.DreamService
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.model.DreamImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private class DreamLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun dispatchEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore() {
        savedStateController.performRestore(null)
    }
}

class JellyPlayDreamService : DreamService() {

    // Koin singles (wave 8B — Hilt removal): resolved lazily straight from
    // the application container, same deferred timing the EntryPoint-backed
    // fields had.
    private val koin by lazy { org.koin.mp.KoinPlatform.getKoin()!! }
    private val authRepository: AuthRepository by lazy { koin.get() }
    private val mediaRepository: MediaRepository by lazy { koin.get() }
    private val imageUrlProvider: ImageUrlProvider by lazy { koin.get() }
    private val preferencesStore: ScreensaverStore by lazy { koin.get() }
    private val imageProvider by lazy {
        DreamImageProvider(mediaRepository, imageUrlProvider, applicationContext)
    }

    private val dreamLifecycle = DreamLifecycleOwner()
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var images by mutableStateOf<List<DreamImage>>(emptyList())
    private var fetchJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        dreamLifecycle.performRestore()
        dreamLifecycle.dispatchEvent(Lifecycle.Event.ON_CREATE)
        dreamLifecycle.dispatchEvent(Lifecycle.Event.ON_START)
        dreamLifecycle.dispatchEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this)
        composeView.setViewTreeLifecycleOwner(dreamLifecycle)
        composeView.setViewTreeSavedStateRegistryOwner(dreamLifecycle)
        setContentView(composeView)

        composeView.setContent {
            MaterialTheme {
                val prefs by preferencesStore.screensaver.collectAsState(
                    initial = com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice(),
                )
                DreamSlideshow(
                    images = images,
                    intervalMs = prefs.dreamSlideshowIntervalMs,
                    kenBurnsEnabled = prefs.dreamKenBurnsEnabled,
                    transitionStyle = prefs.dreamTransitionStyle,
                    showTitle = prefs.dreamShowTitle,
                )
            }
        }

        fetchImages()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        if (images.size < 10) fetchImages()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        fetchJob?.cancel()
    }

    override fun onDetachedFromWindow() {
        dreamLifecycle.dispatchEvent(Lifecycle.Event.ON_PAUSE)
        dreamLifecycle.dispatchEvent(Lifecycle.Event.ON_STOP)
        dreamLifecycle.dispatchEvent(Lifecycle.Event.ON_DESTROY)
        fetchJob?.cancel()
        serviceScope.cancel()
        super.onDetachedFromWindow()
    }

    private fun fetchImages() {
        fetchJob?.cancel()
        fetchJob = serviceScope.launch {
            try {
                authRepository.restoreSession()
                val prefs = preferencesStore.screensaver.first()
                val fetched = imageProvider.fetchImages(
                    categories = prefs.dreamImageCategories,
                    count = 25,
                )
                images = fetched
                if (fetched.size > 3) {
                    imageProvider.prefetchImages(fetched.take(3).map { it.backdropUrl })
                }
            } catch (_: Exception) {
                images = emptyList()
            }
        }
    }
}
