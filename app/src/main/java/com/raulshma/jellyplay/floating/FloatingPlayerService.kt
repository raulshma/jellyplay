package com.raulshma.jellyplay.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.app.NotificationCompat
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import org.koin.mp.KoinPlatform
import kotlin.math.abs

/**
 * Foreground service that renders a floating, draggable media-controller overlay
 * on top of other apps using [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY].
 *
 * The overlay shows the current playback title, artwork, and play/pause controls.
 * It allows the user to keep media controls visible after leaving the app.
 *
 * **Lifecycle:**
 * - [onCreate] — creates the notification channel, posts the foreground
 *   notification, inflates the [ComposeView], and adds it to [WindowManager].
 * - [onDestroy] — removes the view, releases resources, and clears the
 *   [FloatingPlayerState].
 *
 * **Permission:** Requires [Settings.ACTION_MANAGE_OVERLAY_PERMISSION]. The
 * caller must verify with [OverlayPermissionChecker.canDrawOverlays] before
 * starting the service.
 */
class FloatingPlayerService : Service() {

    // Koin single (wave 8B — Hilt removal); lazy keeps construction off the
    // service's creation path until the overlay actually reads state.
    private val floatingPlayerState: FloatingPlayerState by lazy { KoinPlatform.getKoin()!!.get() }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private val overlayLifecycleOwner = ServiceLifecycleOwner()
    private val overlayViewModelStoreOwner = ServiceViewModelStoreOwner()

    companion object {
        private const val CHANNEL_ID = "jellyplay_floating_player"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.raulshma.jellyplay.action.START_FLOATING_PLAYER"
        const val ACTION_STOP = "com.raulshma.jellyplay.action.STOP_FLOATING_PLAYER"

        /**
         * Convenience launcher — checks overlay permission before starting.
         * Returns `true` if the service was started, `false` if permission is
         * missing (the caller should prompt the user).
         */
        fun start(context: Context): Boolean {
            if (!OverlayPermissionChecker.canDrawOverlays(context)) return false
            context.startForegroundService(
                Intent(context, FloatingPlayerService::class.java).setAction(ACTION_START)
            )
            return true
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingPlayerService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        if (overlayView == null) {
            showOverlay()
            floatingPlayerState.onOverlayShown()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        floatingPlayerState.onOverlayHidden()
    }

    // ── Overlay window ────────────────────────────────────────────────

    private fun showOverlay() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 100
        }
        overlayParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayViewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                JellyPlayTheme {
                    FloatingPlayerOverlay(
                        state = floatingPlayerState,
                        onClose = { stopSelf() },
                    )
                }
            }
        }

        attachDragHandler(view, params)
        windowManager.addView(view, params)
        overlayView = view

        overlayLifecycleOwner.performRestore(null)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.removeView(view)
        }
        overlayView = null
        overlayParams = null
        // Clear the ViewModelStore so ViewModels created in the overlay's
        // Compose tree (and their injected collaborators/scopes) are torn down
        // rather than surviving across service show/hide cycles. The lifecycle
        // ON_DESTROY above only notifies the LifecycleOwner; without this the
        // store keeps every ViewModel alive until the process dies.
        overlayViewModelStoreOwner.store.clear()
    }

    /**
     * Attaches a touch listener that lets the user drag the overlay around
     * the screen. A tap (no significant movement) is treated as a click and
     * is allowed to pass through to the Compose UI.
     */
    private fun attachDragHandler(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var layoutFramePending = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    if (!layoutFramePending) {
                        layoutFramePending = true
                        view.postOnAnimation {
                            layoutFramePending = false
                            windowManager.updateViewLayout(view, params)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (layoutFramePending) {
                        layoutFramePending = false
                        windowManager.updateViewLayout(view, params)
                    }
                    if (!isDragging) {
                        v.performClick()
                    }
                }
            }
            true
        }
    }

    // ── Foreground notification ───────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_player_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.floating_player_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.floating_player_notification_title))
            .setContentText(getString(R.string.floating_player_notification_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.media_close), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

// ── Lifecycle plumbing for Compose outside an Activity ────────────────

private class ServiceLifecycleOwner : SavedStateRegistryOwner, LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: android.os.Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}

private class ServiceViewModelStoreOwner : ViewModelStoreOwner {
    val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store
}
