package com.raulshma.jellyplay.core.data.cast.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.CastStrategy
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlnaCastStrategy @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val appRuntimeStateStore: com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore,
) : CastStrategy {

    companion object {
        private const val TAG = "DlnaCastStrategy"
        private const val STRATEGY_NAME = "dlna"
    }

    private val wifiManager: WifiManager by lazy {
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val discoveryMutex = Mutex()

    private val ssdpDiscovery = SsdpDiscovery { wifiManager }

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    override val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    private val _rendererPositionMs = MutableStateFlow(0L)
    val rendererPositionMs: StateFlow<Long> = _rendererPositionMs.asStateFlow()

    private val _rendererDurationMs = MutableStateFlow(0L)
    val rendererDurationMs: StateFlow<Long> = _rendererDurationMs.asStateFlow()

    private val _rendererIsPlaying = MutableStateFlow(false)
    val rendererIsPlaying: StateFlow<Boolean> = _rendererIsPlaying.asStateFlow()

    private val _rendererVolume = MutableStateFlow(1f)
    val rendererVolume: StateFlow<Float> = _rendererVolume.asStateFlow()

    @Volatile
    private var connectedDevice: UpnpDevice? = null

    private var deviceFetchJob: Job? = null
    private val deviceCache = mutableMapOf<String, UpnpDevice>()

    @Volatile
    private var discoveryActive = false

    override fun startDiscovery(context: Context) {
        if (discoveryActive) return
        discoveryActive = true
        ssdpDiscovery.startPeriodicDiscovery(scope)
        startDeviceObserver()
    }

    override fun stopDiscovery() {
        discoveryActive = false
        ssdpDiscovery.stopDiscovery()
        deviceFetchJob?.cancel()
        deviceFetchJob = null
        scope.launch {
            discoveryMutex.withLock {
                deviceCache.clear()
            }
        }
        _discoveredDevices.value = emptyList()
        _isAvailable.value = false
    }

    private fun startDeviceObserver() {
        deviceFetchJob?.cancel()
        deviceFetchJob = scope.launch {
            ssdpDiscovery.discoveredLocations.collect { locationMap ->
                refreshDeviceList(locationMap.values.toList())
            }
        }
    }

    private suspend fun refreshDeviceList(devices: List<SsdpDiscovery.DiscoveredDevice>) {
        val castDevices = mutableListOf<CastDevice>()
        val currentLocations = mutableSetOf<String>()

        for (discovered in devices) {
            currentLocations.add(discovered.locationUrl)
            val cached = deviceCache[discovered.locationUrl]
            if (cached != null) {
                castDevices.add(
                    CastDevice(
                        id = cached.udn,
                        name = cached.friendlyName,
                        type = "dlna",
                        tag = cached,
                        strategyName = STRATEGY_NAME,
                    )
                )
            } else {
                castDevices.add(
                    CastDevice(
                        id = discovered.usn.ifBlank { discovered.locationUrl },
                        name = extractHostFromUrl(discovered.locationUrl),
                        type = "dlna",
                        tag = null,
                        strategyName = STRATEGY_NAME,
                    )
                )
                fetchDeviceDetails(discovered.locationUrl)
            }
        }

        _discoveredDevices.value = castDevices
        _isAvailable.value = castDevices.isNotEmpty()
    }

    private suspend fun fetchDeviceDetails(locationUrl: String) {
        try {
            val device = UpnpDeviceParser.fetchAndParse(locationUrl, okHttpClient) ?: return
            discoveryMutex.withLock {
                deviceCache[locationUrl] = device
            }
            if (discoveryActive) {
                val currentLocations = ssdpDiscovery.discoveredLocations.value.values.toList()
                refreshDeviceList(currentLocations)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch device details from $locationUrl", e)
        }
    }

    override fun connect(context: Context, device: CastDevice) {
        if (_isConnected.value) {
            disconnect(context)
        }

        val upnpDevice = resolveDevice(device) ?: run {
            Log.w(TAG, "Cannot connect: device descriptor not available for ${device.name}")
            return
        }

        _isConnecting.value = true
        connectedDevice = upnpDevice
        _isConnected.value = true
        _isConnecting.value = false

        scope.launch {
            saveRecentDevice(upnpDevice)
        }

        Log.i(TAG, "Connected to DLNA renderer: ${upnpDevice.friendlyName}")
    }

    override fun disconnect(context: Context) {
        val device = connectedDevice
        if (device != null && device.avTransportControlUrl != null) {
            scope.launch {
                try {
                    UpnpControlPoint.stop(device.avTransportControlUrl!!, client = okHttpClient)
                } catch (_: Exception) {
                }
            }
        }
        connectedDevice = null
        _isConnected.value = false
        _isConnecting.value = false
        _rendererPositionMs.value = 0L
        _rendererDurationMs.value = 0L
        _rendererIsPlaying.value = false
        Log.i(TAG, "Disconnected from DLNA renderer")
    }

    fun loadMedia(url: String, title: String, positionMs: Long = 0) {
        val device = connectedDevice ?: run {
            Log.w(TAG, "loadMedia: no device connected")
            return
        }
        val controlUrl = device.avTransportControlUrl ?: return

        scope.launch {
            val metadata = UpnpControlPoint.buildDidlLite(url, title)
            val setResult = UpnpControlPoint.setAvTransportUri(
                controlUrl, uri = url, metadata = metadata, client = okHttpClient
            )
            if (!setResult) {
                Log.w(TAG, "SetAVTransportURI failed")
                return@launch
            }
            val playResult = UpnpControlPoint.play(controlUrl, client = okHttpClient)
            if (!playResult) {
                Log.w(TAG, "Play failed after SetAVTransportURI")
                return@launch
            }
            if (positionMs > 0) {
                kotlinx.coroutines.delay(500)
                UpnpControlPoint.seek(controlUrl, positionMs = positionMs, client = okHttpClient)
            }
            _rendererIsPlaying.value = true
        }
    }

    fun play() {
        val controlUrl = connectedDevice?.avTransportControlUrl ?: return
        scope.launch {
            UpnpControlPoint.play(controlUrl, client = okHttpClient)
            _rendererIsPlaying.value = true
        }
    }

    fun pause() {
        val controlUrl = connectedDevice?.avTransportControlUrl ?: return
        scope.launch {
            UpnpControlPoint.pause(controlUrl, client = okHttpClient)
            _rendererIsPlaying.value = false
        }
    }

    fun stop() {
        val controlUrl = connectedDevice?.avTransportControlUrl ?: return
        scope.launch {
            UpnpControlPoint.stop(controlUrl, client = okHttpClient)
            _rendererIsPlaying.value = false
        }
    }

    fun seekTo(positionMs: Long) {
        val controlUrl = connectedDevice?.avTransportControlUrl ?: return
        scope.launch {
            UpnpControlPoint.seek(controlUrl, positionMs = positionMs, client = okHttpClient)
            _rendererPositionMs.value = positionMs
        }
    }

    suspend fun refreshPlaybackState() {
        val device = connectedDevice ?: return
        val controlUrl = device.avTransportControlUrl ?: return

        try {
            val positionInfo = UpnpControlPoint.getPositionInfo(controlUrl, client = okHttpClient)
            if (positionInfo != null) {
                _rendererPositionMs.value = positionInfo.first
                _rendererDurationMs.value = positionInfo.second
            }

            val state = UpnpControlPoint.getTransportInfo(controlUrl, client = okHttpClient)
            _rendererIsPlaying.value = state == TransportState.PLAYING

            if (device.renderingControlUrl != null) {
                val volume = UpnpControlPoint.getVolume(
                    device.renderingControlUrl, client = okHttpClient
                )
                _rendererVolume.value = volume
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to refresh playback state", e)
        }
    }

    fun setRendererVolume(volume: Float) {
        val controlUrl = connectedDevice?.renderingControlUrl ?: return
        scope.launch {
            UpnpControlPoint.setVolume(controlUrl, volume = volume, client = okHttpClient)
            _rendererVolume.value = volume.coerceIn(0f, 1f)
        }
    }

    private suspend fun saveRecentDevice(device: UpnpDevice) {
        try {
            appRuntimeStateStore.addRecentDlnaDevice(
                DlnaDeviceRef(
                    id = device.udn,
                    name = device.friendlyName,
                    locationUrl = device.locationUrl,
                )
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to save recent device", e)
        }
    }

    private fun resolveDevice(device: CastDevice): UpnpDevice? {
        device.tag?.let { return it as? UpnpDevice }

        val byId = deviceCache.values.find { it.udn == device.id }
        if (byId != null) return byId

        val byLocation = deviceCache[device.id]
        if (byLocation != null) return byLocation

        return null
    }

    private fun extractHostFromUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.host}:${uri.port}"
        } catch (_: Exception) {
            url.take(30)
        }
    }
}
