package com.raulshma.jellyplay.feature.player.video.cast

import android.util.Log
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.CastStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem

class LibVlcCastStrategy(
    private val libVlcProvider: () -> LibVLC?,
    private val mediaPlayerProvider: () -> MediaPlayer? = { null },
) : CastStrategy {

    companion object {
        private const val TAG = "LibVlcCastStrategy"
    }

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    override val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    private val discoverers = mutableListOf<RendererDiscoverer>()
    private val rendererItems = mutableListOf<RendererItem>()
    private var selectedRenderer: RendererItem? = null

    private val eventListener = RendererDiscoverer.EventListener { event ->
        when (event.type) {
            RendererDiscoverer.Event.ItemAdded -> {
                val item = event.getItem() ?: return@EventListener
                rendererItems.add(item)
                _isAvailable.value = true
                refreshDevices()
            }
            RendererDiscoverer.Event.ItemDeleted -> {
                val item = event.getItem() ?: return@EventListener
                rendererItems.remove(item)
                if (selectedRenderer === item) {
                    selectedRenderer?.release()
                    selectedRenderer = null
                    _isConnected.value = false
                }
                _isAvailable.value = rendererItems.isNotEmpty()
                refreshDevices()
            }
        }
    }

    private fun refreshDevices() {
        _discoveredDevices.value = rendererItems.mapIndexed { index, item ->
            CastDevice(
                id = "vlc_renderer_${item.name}_$index",
                name = item.displayName ?: item.name ?: "Unknown",
                type = item.type ?: "unknown",
                tag = item,
            )
        }
    }

    override fun startDiscovery(context: android.content.Context) {
        stopDiscovery()
        val vlc = libVlcProvider() ?: return
        try {
            val descriptions = RendererDiscoverer.list(vlc as ILibVLC)
            for (desc in descriptions) {
                try {
                    val rd = RendererDiscoverer(vlc as ILibVLC, desc.name)
                    rd.setEventListener(eventListener)
                    if (rd.start()) {
                        discoverers.add(rd)
                    } else {
                        Log.w(TAG, "Failed to start renderer discoverer: ${desc.name}")
                        rd.setEventListener(null)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error creating discoverer ${desc.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting renderer discovery", e)
        }
    }

    override fun stopDiscovery() {
        discoverers.forEach { rd ->
            try {
                rd.stop()
                rd.setEventListener(null)
            } catch (_: Exception) {}
        }
        discoverers.clear()
        rendererItems.clear()
        selectedRenderer?.release()
        selectedRenderer = null
        _discoveredDevices.value = emptyList()
        _isAvailable.value = false
    }

    override fun connect(context: android.content.Context, device: CastDevice) {
        val item = device.tag as? RendererItem ?: return
        selectedRenderer?.release()
        item.retain()
        selectedRenderer = item
        _isConnected.value = true
        applyRendererToPlayer()
    }

    override fun disconnect(context: android.content.Context) {
        selectedRenderer?.release()
        selectedRenderer = null
        _isConnected.value = false
        try {
            mediaPlayerProvider()?.setRenderer(null)
        } catch (_: Exception) {}
    }

    val currentRenderer: RendererItem?
        get() = selectedRenderer

    private fun applyRendererToPlayer() {
        val mp = mediaPlayerProvider() ?: return
        val renderer = selectedRenderer ?: return
        try {
            mp.setRenderer(renderer)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set renderer on MediaPlayer", e)
        }
    }
}
