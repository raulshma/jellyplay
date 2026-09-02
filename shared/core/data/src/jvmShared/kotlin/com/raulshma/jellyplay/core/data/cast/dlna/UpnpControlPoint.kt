package com.raulshma.jellyplay.core.data.cast.dlna

import com.raulshma.jellyplay.core.data.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object UpnpControlPoint {

    private const val TAG = "UpnpControlPoint"
    private val SOAP_XML = "text/xml; charset=utf-8".toMediaType()

    private const val AV_TRANSPORT_NS = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RENDERING_CONTROL_NS = "urn:schemas-upnp-org:service:RenderingControl:1"

    private val docBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()

    suspend fun setAvTransportUri(
        controlUrl: String,
        instanceId: Int = 0,
        uri: String,
        metadata: String? = null,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val meta = metadata ?: buildDidlLite(uri, "", "")
        val body = buildSoapAction(
            "SetAVTransportURI",
            AV_TRANSPORT_NS,
            """
            <InstanceID>$instanceId</InstanceID>
            <CurrentURI>$uri</CurrentURI>
            <CurrentURIMetaData>${escapeXml(meta)}</CurrentURIMetaData>
            """
        )
        invokeSoapAction(controlUrl, "SetAVTransportURI", AV_TRANSPORT_NS, body, client)
    }

    suspend fun setNextAvTransportUri(
        controlUrl: String,
        instanceId: Int = 0,
        uri: String,
        metadata: String? = null,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val meta = metadata ?: ""
        val body = buildSoapAction(
            "SetNextAVTransportURI",
            AV_TRANSPORT_NS,
            """
            <InstanceID>$instanceId</InstanceID>
            <NextURI>$uri</NextURI>
            <NextURIMetaData>${escapeXml(meta)}</NextURIMetaData>
            """
        )
        invokeSoapAction(controlUrl, "SetNextAVTransportURI", AV_TRANSPORT_NS, body, client)
    }

    suspend fun play(
        controlUrl: String,
        instanceId: Int = 0,
        speed: String = "1",
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = buildSoapAction(
            "Play",
            AV_TRANSPORT_NS,
            """
            <InstanceID>$instanceId</InstanceID>
            <Speed>$speed</Speed>
            """
        )
        invokeSoapAction(controlUrl, "Play", AV_TRANSPORT_NS, body, client)
    }

    suspend fun pause(
        controlUrl: String,
        instanceId: Int = 0,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = buildSoapAction(
            "Pause",
            AV_TRANSPORT_NS,
            "<InstanceID>$instanceId</InstanceId>"
        )
        invokeSoapAction(controlUrl, "Pause", AV_TRANSPORT_NS, body, client)
    }

    suspend fun stop(
        controlUrl: String,
        instanceId: Int = 0,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = buildSoapAction(
            "Stop",
            AV_TRANSPORT_NS,
            "<InstanceID>$instanceId</InstanceId>"
        )
        invokeSoapAction(controlUrl, "Stop", AV_TRANSPORT_NS, body, client)
    }

    suspend fun seek(
        controlUrl: String,
        instanceId: Int = 0,
        positionMs: Long,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val target = formatTime(positionMs)
        val body = buildSoapAction(
            "Seek",
            AV_TRANSPORT_NS,
            """
            <InstanceID>$instanceId</InstanceID>
            <Unit>REL_TIME</Unit>
            <Target>$target</Target>
            """
        )
        invokeSoapAction(controlUrl, "Seek", AV_TRANSPORT_NS, body, client)
    }

    suspend fun getPositionInfo(
        controlUrl: String,
        instanceId: Int = 0,
        client: OkHttpClient,
    ): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val body = buildSoapAction(
            "GetPositionInfo",
            AV_TRANSPORT_NS,
            "<InstanceID>$instanceId</InstanceID>"
        )
        val response = invokeSoapActionRaw(controlUrl, "GetPositionInfo", AV_TRANSPORT_NS, body, client)
            ?: return@withContext null
        parsePositionInfo(response)
    }

    suspend fun getTransportInfo(
        controlUrl: String,
        instanceId: Int = 0,
        client: OkHttpClient,
    ): TransportState = withContext(Dispatchers.IO) {
        val body = buildSoapAction(
            "GetTransportInfo",
            AV_TRANSPORT_NS,
            "<InstanceID>$instanceId</InstanceID>"
        )
        val response = invokeSoapActionRaw(controlUrl, "GetTransportInfo", AV_TRANSPORT_NS, body, client)
            ?: return@withContext TransportState.UNKNOWN
        parseTransportState(response)
    }

    suspend fun getVolume(
        controlUrl: String,
        instanceId: Int = 0,
        channel: String = "Master",
        client: OkHttpClient,
    ): Float = withContext(Dispatchers.IO) {
        val body = buildSoapAction(
            "GetVolume",
            RENDERING_CONTROL_NS,
            """
            <InstanceID>$instanceId</InstanceID>
            <Channel>$channel</Channel>
            """
        )
        val response = invokeSoapActionRaw(controlUrl, "GetVolume", RENDERING_CONTROL_NS, body, client)
            ?: return@withContext 1f
        parseVolume(response)
    }

    suspend fun setVolume(
        controlUrl: String,
        instanceId: Int = 0,
        channel: String = "Master",
        volume: Float,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        val volumeInt = (volume.coerceIn(0f, 1f) * 100).toInt()
        val body = buildSoapAction(
            "SetVolume",
            RENDERING_CONTROL_NS,
            """
            <InstanceID>$instanceId</InstanceID>
            <Channel>$channel</Channel>
            <DesiredVolume>$volumeInt</DesiredVolume>
            """
        )
        invokeSoapAction(controlUrl, "SetVolume", RENDERING_CONTROL_NS, body, client)
    }

    private fun buildSoapAction(action: String, namespace: String, arguments: String): String {
        return """
            <?xml version="1.0" encoding="utf-8" standalone="yes"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:$action xmlns:u="$namespace">
                        $arguments
                    </u:$action>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private suspend fun invokeSoapAction(
        controlUrl: String,
        action: String,
        namespace: String,
        soapBody: String,
        client: OkHttpClient,
    ): Boolean {
        val response = invokeSoapActionRaw(controlUrl, action, namespace, soapBody, client) ?: return false
        return !response.contains("errorCode", ignoreCase = true)
    }

    private fun invokeSoapActionRaw(
        controlUrl: String,
        action: String,
        namespace: String,
        soapBody: String,
        client: OkHttpClient,
    ): String? {
        return try {
            val request = Request.Builder()
                .url(controlUrl)
                .post(soapBody.toRequestBody(SOAP_XML))
                .header("SOAPAction", "\"$namespace#$action\"")
                .header("Content-Type", "text/xml; charset=utf-8")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful && body.isNullOrBlank()) {
                Log.d(TAG, "SOAP $action failed: ${response.code}")
                return null
            }
            body
        } catch (e: Exception) {
            Log.d(TAG, "SOAP $action error", e)
            null
        }
    }

    private fun parsePositionInfo(xml: String): Pair<Long, Long>? {
        return try {
            val builder = docBuilderFactory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            val relTime = getTagText(doc, "RelTime") ?: return null
            val duration = getTagText(doc, "Duration") ?: "00:00:00"

            val positionMs = parseTimeToMs(relTime)
            val durationMs = parseTimeToMs(duration)
            Pair(positionMs, durationMs)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse position info", e)
            null
        }
    }

    private fun parseTransportState(xml: String): TransportState {
        return try {
            val builder = docBuilderFactory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            val state = getTagText(doc, "CurrentTransportState") ?: return TransportState.UNKNOWN
            when (state) {
                "PLAYING" -> TransportState.PLAYING
                "PAUSED_PLAYBACK" -> TransportState.PAUSED
                "STOPPED" -> TransportState.STOPPED
                "TRANSITIONING" -> TransportState.TRANSITIONING
                "NO_MEDIA_PRESENT" -> TransportState.NO_MEDIA
                else -> TransportState.UNKNOWN
            }
        } catch (e: Exception) {
            TransportState.UNKNOWN
        }
    }

    private fun parseVolume(xml: String): Float {
        return try {
            val builder = docBuilderFactory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            val volumeStr = getTagText(doc, "CurrentVolume") ?: return 1f
            val volumeInt = volumeStr.toIntOrNull() ?: return 1f
            volumeInt.coerceIn(0, 100) / 100f
        } catch (e: Exception) {
            1f
        }
    }

    private fun getTagText(doc: org.w3c.dom.Document, tagName: String): String? {
        val nodes = doc.getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun parseTimeToMs(time: String): Long {
        if (time.isBlank() || time == "NOT_IMPLEMENTED") return 0L
        val parts = time.split(":")
        if (parts.size != 3) return 0L
        return try {
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].split(".").firstOrNull()?.toLongOrNull() ?: 0L
            (hours * 3600 + minutes * 60 + seconds) * 1000
        } catch (_: Exception) {
            0L
        }
    }

    fun buildDidlLite(url: String, title: String, artist: String = ""): String {
        val escapedTitle = escapeXml(title)
        val escapedArtist = escapeXml(artist)
        val escapedUrl = escapeXml(url)
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                       xmlns:dc="http://purl.org/dc/elements/1.1/"
                       xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                       xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">
                <item id="0" parentID="-1" restricted="1">
                    <dc:title>$escapedTitle</dc:title>
                    <upnp:class>object.item.videoItem</upnp:class>
                    <upnp:artist>$escapedArtist</upnp:artist>
                    <res protocolInfo="http-get:*:video/*:DLNA.ORG_OP=01">$escapedUrl</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
