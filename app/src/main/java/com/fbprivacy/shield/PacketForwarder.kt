package com.fbprivacy.shield

import android.net.VpnService
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class ByteBufferPool(private val bufferCapacity: Int = 32768, private val maxPoolSize: Int = 64) {
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()

    fun acquire(): ByteBuffer {
        val buf = pool.poll()
        if (buf != null) {
            buf.clear()
            return buf
        }
        return ByteBuffer.allocate(bufferCapacity)
    }

    fun release(buf: ByteBuffer) {
        if (pool.size < maxPoolSize) {
            buf.clear()
            pool.offer(buf)
        }
    }
}

class PacketForwarder(
    private val vpnService: VpnService,
    private val tunOutputStream: FileOutputStream,
    private val statsRepository: StatsRepository
) {
    private val tag = "PacketForwarder"
    val byteBufferPool = ByteBufferPool()

    private val upstreamDnsIps = listOf("8.8.8.8", "1.1.1.1", "8.8.4.4")
    private val activeTcpChannels = ConcurrentHashMap<String, SocketChannel>()

    /**
     * Forwards an allowed DNS query directly to upstream DNS resolver using a protected DatagramSocket
     */
    fun forwardDnsQuery(
        packetBuffer: ByteBuffer,
        ipHeader: IpHeader,
        udpHeader: UdpHeader
    ) {
        try {
            val dnsOffset = udpHeader.payloadOffset
            val dnsLen = udpHeader.payloadLength
            if (dnsLen <= 0) return

            val dnsQueryBytes = ByteArray(dnsLen)
            val oldPos = packetBuffer.position()
            packetBuffer.position(dnsOffset)
            packetBuffer.get(dnsQueryBytes, 0, dnsLen)
            packetBuffer.position(oldPos)

            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 3000

            var responsePacket: DatagramPacket? = null
            val respBuffer = ByteArray(2048)

            for (dnsIp in upstreamDnsIps) {
                try {
                    val destAddress = InetAddress.getByName(dnsIp)
                    val sendPacket = DatagramPacket(dnsQueryBytes, dnsLen, destAddress, 53)
                    socket.send(sendPacket)

                    val inPacket = DatagramPacket(respBuffer, respBuffer.size)
                    socket.receive(inPacket)
                    responsePacket = inPacket
                    break
                } catch (e: Exception) {
                    // Retry with next upstream DNS
                    continue
                }
            }
            socket.close()

            if (responsePacket != null && responsePacket.length > 0) {
                val respDnsLen = responsePacket.length
                val newTotalLen = 20 + 8 + respDnsLen
                val rawRespBytes = ByteArray(newTotalLen)
                val respBuf = ByteBuffer.wrap(rawRespBytes)

                // IPv4 Header
                respBuf.put(0x45.toByte()) // Version 4, IHL 5
                respBuf.put(0x00.toByte()) // DSCP/ECN
                respBuf.putShort(newTotalLen.toShort()) // Total Length
                respBuf.putShort((1..30000).random().toShort()) // ID
                respBuf.putShort(0x4000.toShort()) // Don't fragment
                respBuf.put(64.toByte()) // TTL
                respBuf.put(17.toByte()) // Protocol UDP
                respBuf.putShort(0.toShort()) // Checksum placeholder

                // Source IP (original destination DNS IP)
                val dstIpParts = ipHeader.destIp.split(".").map { it.toInt().toByte() }
                for (b in dstIpParts) respBuf.put(b)

                // Destination IP (original client source IP)
                val srcIpParts = ipHeader.sourceIp.split(".").map { it.toInt().toByte() }
                for (b in srcIpParts) respBuf.put(b)

                // UDP Header
                respBuf.putShort(udpHeader.destPort.toShort()) // Source Port
                respBuf.putShort(udpHeader.sourcePort.toShort()) // Destination Port
                respBuf.putShort((8 + respDnsLen).toShort()) // Length
                respBuf.putShort(0.toShort()) // Checksum (0 = none)

                // DNS payload
                respBuf.put(responsePacket.data, 0, respDnsLen)

                // Calculate IPv4 Header checksum
                val ipChecksum = computeIpChecksum(rawRespBytes, 0, 20)
                respBuf.putShort(10, ipChecksum.toShort())

                synchronized(tunOutputStream) {
                    tunOutputStream.write(rawRespBytes, 0, newTotalLen)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to forward DNS query: ${e.message}")
        }
    }

    /**
     * Forwards allowed TCP payload or opens protected connection
     */
    fun forwardTcpPacket(
        packetBuffer: ByteBuffer,
        ipHeader: IpHeader,
        tcpHeader: TcpHeader
    ) {
        val flowKey = "${ipHeader.sourceIp}:${tcpHeader.sourcePort}->${ipHeader.destIp}:${tcpHeader.destPort}"

        if (tcpHeader.isFin || tcpHeader.isRst) {
            val channel = activeTcpChannels.remove(flowKey)
            try {
                channel?.close()
            } catch (_: Exception) {}
            return
        }

        if (tcpHeader.payloadLength <= 0) return

        try {
            var channel = activeTcpChannels[flowKey]
            if (channel == null || !channel.isOpen || !channel.isConnected) {
                channel = SocketChannel.open()
                vpnService.protect(channel.socket())
                channel.configureBlocking(true)
                channel.socket().soTimeout = 5000

                var connected = false
                var backoffMs = 50L
                for (attempt in 1..3) {
                    try {
                        val destAddress = InetSocketAddress(ipHeader.destIp, tcpHeader.destPort)
                        channel.connect(destAddress)
                        connected = true
                        break
                    } catch (e: IOException) {
                        Thread.sleep(backoffMs)
                        backoffMs *= 2
                    }
                }

                if (!connected) {
                    try { channel.close() } catch (_: Exception) {}
                    return
                }
                activeTcpChannels[flowKey] = channel
            }

            val payloadBytes = ByteArray(tcpHeader.payloadLength)
            val oldPos = packetBuffer.position()
            packetBuffer.position(tcpHeader.payloadOffset)
            packetBuffer.get(payloadBytes, 0, tcpHeader.payloadLength)
            packetBuffer.position(oldPos)

            val writeBuffer = ByteBuffer.wrap(payloadBytes)
            while (writeBuffer.hasRemaining()) {
                channel.write(writeBuffer)
            }
        } catch (e: Exception) {
            Log.w(tag, "TCP forwarding issue on $flowKey: ${e.message}")
            activeTcpChannels.remove(flowKey)?.let {
                try { it.close() } catch (_: Exception) {}
            }
        }
    }

    fun closeAll() {
        for ((_, channel) in activeTcpChannels) {
            try {
                channel.close()
            } catch (_: Exception) {}
        }
        activeTcpChannels.clear()
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
