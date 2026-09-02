package com.raulshma.jellyplay.core.data.cast.dlna

import com.raulshma.jellyplay.core.data.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import java.net.URI
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object UpnpDeviceParser {

    private const val TAG = "UpnpDeviceParser"

    private val AV_TRANSPORT_TYPE = "urn:schemas-upnp-org:service:AVTransport"
    private val RENDERING_CONTROL_TYPE = "urn:schemas-upnp-org:service:RenderingControl"
    private val CONNECTION_MANAGER_TYPE = "urn:schemas-upnp-org:service:ConnectionManager"

    private val docBuilderFactory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = true }

    suspend fun fetchAndParse(
        locationUrl: String,
        client: OkHttpClient,
    ): UpnpDevice? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(locationUrl).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                response.close()
                return@withContext null
            }
            parseDeviceXml(body, locationUrl)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch device description from $locationUrl", e)
            null
        }
    }

    internal fun parseDeviceXml(xml: String, locationUrl: String): UpnpDevice? {
        return try {
            val builder = docBuilderFactory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            val root = doc.documentElement

            val deviceNode = root.getElementsByTagName("device").item(0) ?: return null
            val deviceElement = deviceNode as? org.w3c.dom.Element ?: return null

            val udn = getTagValue(deviceElement, "UDN") ?: return null
            val friendlyName = getTagValue(deviceElement, "friendlyName") ?: "Unknown DLNA Device"
            val modelName = getTagValue(deviceElement, "modelName") ?: ""
            val manufacturer = getTagValue(deviceElement, "manufacturer") ?: ""
            val modelDescription = getTagValue(deviceElement, "modelDescription") ?: ""

            val baseUrl = getTagValue(root, "URLBase") ?: locationUrl

            val iconUrl = resolveUrl(baseUrl, parseBestIcon(deviceElement))

            val services = parseServiceList(deviceElement)
            val avTransportUrl = serviceControlUrl(services, AV_TRANSPORT_TYPE)?.let { resolveUrl(baseUrl, it) }
            val renderingControlUrl = serviceControlUrl(services, RENDERING_CONTROL_TYPE)?.let { resolveUrl(baseUrl, it) }
            val connectionManagerUrl = serviceControlUrl(services, CONNECTION_MANAGER_TYPE)?.let { resolveUrl(baseUrl, it) }

            if (avTransportUrl == null) {
                Log.d(TAG, "Device $friendlyName has no AVTransport service, skipping")
                return null
            }

            UpnpDevice(
                udn = udn,
                friendlyName = friendlyName,
                modelName = modelName,
                manufacturer = manufacturer,
                modelDescription = modelDescription,
                locationUrl = locationUrl,
                iconUrl = iconUrl,
                avTransportControlUrl = avTransportUrl,
                renderingControlUrl = renderingControlUrl,
                connectionManagerUrl = connectionManagerUrl,
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse device XML", e)
            null
        }
    }

    private fun parseBestIcon(deviceElement: org.w3c.dom.Element): String? {
        val iconList = deviceElement.getElementsByTagName("iconList").item(0) as? org.w3c.dom.Element
            ?: return null

        val icons = iconList.getElementsByTagName("icon")
        var bestUrl: String? = null
        var bestSize = 0

        for (i in 0 until icons.length) {
            val iconEl = icons.item(i) as? org.w3c.dom.Element ?: continue
            val url = getTagValue(iconEl, "url") ?: continue
            val width = getTagValue(iconEl, "width")?.toIntOrNull() ?: 0
            val height = getTagValue(iconEl, "height")?.toIntOrNull() ?: 0
            val size = width * height
            if (size > bestSize && size <= 256 * 256) {
                bestSize = size
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun parseServiceList(deviceElement: org.w3c.dom.Element): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val serviceList = deviceElement.getElementsByTagName("serviceList").item(0) as? org.w3c.dom.Element
            ?: return result

        val services = serviceList.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val serviceEl = services.item(i) as? org.w3c.dom.Element ?: continue
            val serviceType = getTagValue(serviceEl, "serviceType") ?: continue
            val controlUrl = getTagValue(serviceEl, "controlURL") ?: continue
            result[serviceType] = controlUrl
        }
        return result
    }

    /**
     * Device descriptions declare versioned service types
     * (`urn:schemas-upnp-org:service:AVTransport:1`), so the version-less
     * constants must match by prefix — an exact-map lookup never hits and
     * would reject every real device as "no AVTransport service".
     */
    private fun serviceControlUrl(services: Map<String, String>, serviceType: String): String? {
        return services[serviceType]
            ?: services.entries.firstOrNull { it.key.startsWith("$serviceType:") }?.value
    }

    private fun getTagValue(element: org.w3c.dom.Element, tagName: String): String? {
        val nodes = element.getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun resolveUrl(baseUrl: String, relativePath: String?): String? {
        if (relativePath.isNullOrBlank()) return null
        return try {
            val uri = URI(relativePath)
            if (uri.isAbsolute) return relativePath
            val base = URL(baseUrl)
            URL(base, relativePath).toString()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to resolve URL: base=$baseUrl, relative=$relativePath", e)
            null
        }
    }
}
