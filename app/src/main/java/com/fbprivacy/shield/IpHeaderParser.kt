package com.fbprivacy.shield

import java.nio.ByteBuffer

data class IpHeader(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val protocol: Int,
    val sourceIp: String,
    val destIp: String,
    val payloadOffset: Int
) {
    val isTcp: Boolean get() = protocol == PROTOCOL_TCP
    val isUdp: Boolean get() = protocol == PROTOCOL_UDP

    companion object {
        const val PROTOCOL_ICMP = 1
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
    }
}

object IpHeaderParser {

    fun parse(buffer: ByteBuffer): IpHeader? {
        if (buffer.remaining() < 20) return null

        val startPos = buffer.position()
        val versionAndIhl = buffer.get(startPos).toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) {
            // We focus on IPv4 for VPN loopback filtering
            return null
        }

        val ihl = versionAndIhl and 0x0F
        val headerLength = ihl * 4
        if (headerLength < 20 || buffer.remaining() < headerLength) {
            return null
        }

        val totalLength = buffer.getShort(startPos + 2).toInt() and 0xFFFF
        val protocol = buffer.get(startPos + 9).toInt() and 0xFF

        val srcIpBytes = ByteArray(4)
        for (i in 0 until 4) {
            srcIpBytes[i] = buffer.get(startPos + 12 + i)
        }
        val srcIp = formatIp(srcIpBytes)

        val dstIpBytes = ByteArray(4)
        for (i in 0 until 4) {
            dstIpBytes[i] = buffer.get(startPos + 16 + i)
        }
        val dstIp = formatIp(dstIpBytes)

        return IpHeader(
            version = version,
            headerLength = headerLength,
            totalLength = totalLength,
            protocol = protocol,
            sourceIp = srcIp,
            destIp = dstIp,
            payloadOffset = startPos + headerLength
        )
    }

    private fun formatIp(bytes: ByteArray): String {
        return "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"
    }
}
