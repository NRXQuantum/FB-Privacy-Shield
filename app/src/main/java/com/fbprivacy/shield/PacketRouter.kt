package com.fbprivacy.shield

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class PacketRouter(
    private val vpnInterface: ParcelFileDescriptor,
    private val endpointBlocklist: EndpointBlocklist,
    private val statsRepository: StatsRepository,
    private val packetForwarder: PacketForwarder
) {
    private val tag = "PacketRouter"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var routingJob: Job? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.i(tag, "Starting packet routing loop...")

        routingJob = scope.launch {
            val fileDescriptor = vpnInterface.fileDescriptor
            val inputStream = FileInputStream(fileDescriptor)
            val outputStream = FileOutputStream(fileDescriptor)
            val pool = packetForwarder.byteBufferPool

            while (isActive && isRunning) {
                val buffer = pool.acquire()
                try {
                    val bytesRead = inputStream.read(buffer.array(), 0, buffer.capacity())
                    if (bytesRead > 0) {
                        buffer.limit(bytesRead)
                        buffer.position(0)
                        processPacket(buffer, bytesRead, outputStream)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.w(tag, "Error reading from TUN interface: ${e.message}")
                    }
                    break
                } finally {
                    pool.release(buffer)
                }
            }
        }
    }

    private fun processPacket(
        buffer: ByteBuffer,
        packetLength: Int,
        tunOutputStream: FileOutputStream
    ) {
        val ipHeader = IpHeaderParser.parse(buffer) ?: return

        when (ipHeader.protocol) {
            IpHeader.PROTOCOL_UDP -> {
                handleUdpPacket(buffer, ipHeader, packetLength, tunOutputStream)
            }
            IpHeader.PROTOCOL_TCP -> {
                handleTcpPacket(buffer, ipHeader, packetLength)
            }
            else -> {
                // Drop other raw protocols
            }
        }
    }

    private fun handleUdpPacket(
        buffer: ByteBuffer,
        ipHeader: IpHeader,
        packetLength: Int,
        tunOutputStream: FileOutputStream
    ) {
        val udpHeader = UdpHeaderParser.parse(buffer, ipHeader) ?: return

        // Localhost port check
        if (endpointBlocklist.isBlockedPort(udpHeader.destPort, isTcp = false)) {
            statsRepository.recordBlockedPacket(
                bytes = packetLength,
                reason = "Blocked Facebook localhost telemetry UDP port ${udpHeader.destPort}",
                destination = "${ipHeader.destIp}:${udpHeader.destPort}",
                protocol = "UDP"
            )
            return // Dropped
        }

        if (udpHeader.isDns) {
            val dnsQuery = DnsPacketParser.parseQuery(buffer, udpHeader)
            if (dnsQuery != null) {
                val domain = dnsQuery.questionName
                if (endpointBlocklist.isDomainBlocked(domain)) {
                    // Blocked tracker DNS query!
                    statsRepository.recordBlockedPacket(
                        bytes = packetLength,
                        reason = "DNS Tracker Query Blocked ($domain)",
                        destination = domain,
                        protocol = "DNS"
                    )

                    // Synthesize immediate NXDOMAIN response so Facebook tracking drops fast
                    val nxDomainResp = DnsPacketParser.buildNxDomainResponse(
                        originalPacket = buffer,
                        ipHeader = ipHeader,
                        udpHeader = udpHeader,
                        txId = dnsQuery.transactionId
                    )
                    if (nxDomainResp != null) {
                        try {
                            synchronized(tunOutputStream) {
                                tunOutputStream.write(nxDomainResp)
                            }
                        } catch (_: Exception) {}
                    }
                    return // Dropped
                } else {
                    // Allowed domain DNS query -> Forward to upstream DNS resolver
                    packetForwarder.forwardDnsQuery(buffer, ipHeader, udpHeader)
                    return
                }
            }
        }
    }

    private fun handleTcpPacket(
        buffer: ByteBuffer,
        ipHeader: IpHeader,
        packetLength: Int
    ) {
        val tcpHeader = TcpHeaderParser.parse(buffer, ipHeader) ?: return

        // Localhost port check
        if (endpointBlocklist.isBlockedPort(tcpHeader.destPort, isTcp = true)) {
            statsRepository.recordBlockedPacket(
                bytes = packetLength,
                reason = "Blocked Facebook localhost tracking TCP port ${tcpHeader.destPort}",
                destination = "${ipHeader.destIp}:${tcpHeader.destPort}",
                protocol = "TCP"
            )
            return // Dropped
        }

        // Deep Packet Inspection for TLS SNI
        val sni = TcpHeaderParser.extractTlsSni(buffer, tcpHeader)
        if (sni != null) {
            if (endpointBlocklist.isDomainBlocked(sni)) {
                statsRepository.recordBlockedPacket(
                    bytes = packetLength,
                    reason = "TLS SNI Tracker Blocked ($sni)",
                    destination = sni,
                    protocol = "HTTPS"
                )
                return // Dropped!
            }
        }

        // HTTP Inspection for plaintext endpoints
        val httpTarget = TcpHeaderParser.extractHttpTarget(buffer, tcpHeader)
        if (httpTarget != null) {
            if (endpointBlocklist.shouldBlockUrlOrPayload(httpTarget)) {
                statsRepository.recordBlockedPacket(
                    bytes = packetLength,
                    reason = "HTTP Tracker Endpoint Blocked ($httpTarget)",
                    destination = httpTarget,
                    protocol = "HTTP"
                )
                return // Dropped!
            }
        }

        // Allowed traffic -> forward
        packetForwarder.forwardTcpPacket(buffer, ipHeader, tcpHeader)
    }

    fun stop() {
        isRunning = false
        routingJob?.cancel()
        routingJob = null
        packetForwarder.closeAll()
        Log.i(tag, "Packet routing loop stopped.")
    }
}
