package ru.echo.radar.discovery

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UdpDiscovery {
    /** Returns validated HTTP LAN origins; timeout bounds both discovery and cancellation latency. */
    suspend fun discover(port: Int = 45454): Set<String> = withContext(Dispatchers.IO) {
        val found = mutableSetOf<String>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val payload = "ECHO_DISCOVER_V1".toByteArray(Charsets.US_ASCII)
            val broadcasts = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }.flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }.toSet() + InetAddress.getByName("255.255.255.255")
            broadcasts.forEach { socket.send(DatagramPacket(payload, payload.size, it, port)) }
            val until = System.nanoTime() + 1_500_000_000L
            while (System.nanoTime() < until) {
                val packet = DatagramPacket(ByteArray(128), 128)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                val reply = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                val httpPort = reply.takeIf { it.startsWith("ECHO_V1:") }?.substringAfter(':')?.toIntOrNull()
                if (httpPort != null && httpPort in 1..65535 && packet.address.isSiteLocalAddress) {
                    found += "http://${packet.address.hostAddress}:$httpPort"
                }
            }
        }
        found
    }
}
