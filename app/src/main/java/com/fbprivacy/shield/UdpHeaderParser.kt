package com.fbprivacy.shield

import java.nio.ByteBuffer

data class UdpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val length: Int,
    val checksum: Int,
    val payloadOffset: Int,
    val payloadLength: Int
) {
    val isDns: Boolean get() = sourcePort == 53 || destPort == 53
}

object UdpHeaderParser {

    fun parse(buffer: ByteBuffer, ipHeader: IpHeader): UdpHeader? {
        val udpStart = ipHeader.payloadOffset
        val available = buffer.limit() - udpStart
        if (available < 8) return null

        val srcPort = buffer.getShort(udpStart).toInt() and 0xFFFF
        val dstPort = buffer.getShort(udpStart + 2).toInt() and 0xFFFF
        val length = buffer.getShort(udpStart + 4).toInt() and 0xFFFF
        val checksum = buffer.getShort(udpStart + 6).toInt() and 0xFFFF

        if (length < 8) return null

        val payloadOffset = udpStart + 8
        val payloadLength = (length - 8).coerceAtMost(available - 8).coerceAtLeast(0)

        return UdpHeader(
            sourcePort = srcPort,
            destPort = dstPort,
            length = length,
            checksum = checksum,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength
        )
    }
}
