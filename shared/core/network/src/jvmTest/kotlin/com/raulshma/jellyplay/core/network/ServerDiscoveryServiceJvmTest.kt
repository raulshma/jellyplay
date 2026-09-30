package com.raulshma.jellyplay.core.network

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * End-to-end discovery loop against the REAL production code path
 * ([ServerDiscoveryService] → Jellyfin SDK `LocalServerDiscovery` → UDP
 * broadcast on 7359), with a fake Jellyfin UDP responder playing the server.
 *
 * The fake replays the exact payload shape a Jellyfin container emits behind
 * a Docker bridge: the JSON `Address` points at the container-internal IP
 * (172.17.0.x) because that is the interface the server sees, while the
 * datagram itself arrives from the host's LAN IP (the bridge NAT). That split
 * is the Docker bug users hit: a discovery entry that lists the unreachable
 * container address, or nothing at all when the app drops the entry.
 *
 * The SDK rewrites `ServerDiscoveryInfo.endpointAddress` to the datagram's
 * source host — this test pins that the service surfaces THAT address (with
 * the payload's scheme/port re-applied) so discovery yields a connectable
 * endpoint.
 */
class ServerDiscoveryServiceJvmTest {

    private companion object {
        const val DISCOVERY_PORT = 7359
        const val CONTAINER_INTERNAL_ADDRESS = "http://172.17.0.2:8096"
        const val SERVER_ID = "9cd3d9be17d54f6a8e7d8f0f2b1c4a55"
        const val SERVER_NAME = "JellyfinDocker"

        fun dockerBridgePayload(): String =
            """{"Address":"$CONTAINER_INTERNAL_ADDRESS","Id":"$SERVER_ID","Name":"$SERVER_NAME"}"""
    }

    /** Fake Jellyfin UDP responder: answers queries from the same socket, like Jellyfin's UdpServer. */
    private class FakeJellyfinResponder : AutoCloseable {
        val socket: DatagramSocket = try {
            DatagramSocket(DISCOVERY_PORT, InetAddress.getByName("0.0.0.0"))
        } catch (e: Exception) {
            throw IllegalStateException(
                "Cannot bind UDP $DISCOVERY_PORT — another responder is running on this host?", e,
            )
        }
        /** Source IP the discovery query arrived from — the NAT-visible, connectable host. */
        @Volatile var lastQuerySourceIp: String? = null
        private val thread = Thread {
            try {
                socket.soTimeout = 10_000
                val buffer = ByteArray(1024)
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) {
                        break // timeout or closed
                    }
                    val message = String(packet.data, 0, packet.length)
                    if (!message.contains("JellyfinServer")) continue
                    lastQuerySourceIp = packet.address.hostAddress
                    val payload = dockerBridgePayload().toByteArray()
                    socket.send(DatagramPacket(payload, payload.size, packet.address, packet.port))
                }
            } catch (_: Exception) {
                // socket closed
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            socket.close()
        }
    }

    private val responder = FakeJellyfinResponder()

    private val service = ServerDiscoveryService(
        jellyfin = org.jellyfin.sdk.createJellyfin {
            clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "JellyPlayTest", version = "1.0.0")
            deviceInfo = org.jellyfin.sdk.model.DeviceInfo(id = "discovery-test", name = "DiscoveryTest")
        },
        multicastGuard = NoopDiscoveryMulticastGuard(),
    )

    @AfterTest
    fun tearDown() {
        responder.close()
    }

    @Test
    fun `discovery surfaces the reachable NAT source address, not the Docker container address`() = runBlocking {
        val found = withTimeout(15_000) {
            service.discoverLocalServers(timeoutMs = 1_000, maxServers = 1).firstOrNull()
        }

        val sourceIp = responder.lastQuerySourceIp
        assertNotNull(found, "No server discovered — UDP broadcast/response loop failed")
        assertNotNull(sourceIp, "Responder never received the discovery query")

        assertEquals(
            SERVER_ID,
            found.id,
            "Wrong server id surfaced",
        )
        assertEquals(
            "http://$sourceIp:8096",
            found.address,
            "Discovered address must be the connectable host the datagram came from, " +
                "not the container-internal '$CONTAINER_INTERNAL_ADDRESS' the payload reports",
        )
    }

    @Test
    fun `payload address survives verbatim when it already matches the datagram source (host network)`() = runBlocking {
        // Docker --network=host or bare-metal server: the payload address host
        // equals the packet source. The rewritten address must not drift from it.
        val found = withTimeout(15_000) {
            service.discoverLocalServers(timeoutMs = 1_000, maxServers = 1).firstOrNull()
        }
        val sourceIp = responder.lastQuerySourceIp ?: fail("Responder never received the discovery query")
        assertNotNull(found)
        assertEquals(SERVER_ID, found.id)
        assertEquals("http://$sourceIp:8096", found.address)
    }
}
