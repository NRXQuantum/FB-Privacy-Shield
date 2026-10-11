package com.fbprivacy.shield

import java.nio.ByteBuffer

data class DnsQuery(
    val transactionId: Int,
    val flags: Int,
    val questionName: String,
    val questionType: Int,
    val questionClass: Int
)

object DnsPacketParser {

    fun parseQuery(buffer: ByteBuffer, udpHeader: UdpHeader): DnsQuery? {
        val dnsStart = udpHeader.payloadOffset
        val available = udpHeader.payloadLength
        if (available < 12) return null

        val txId = buffer.getShort(dnsStart).toInt() and 0xFFFF
        val flags = buffer.getShort(dnsStart + 2).toInt() and 0xFFFF
        val qdCount = buffer.getShort(dnsStart + 4).toInt() and 0xFFFF

        if (qdCount < 1) return null

        var pos = dnsStart + 12
        val limit = dnsStart + available
        val sb = StringBuilder()

        while (pos < limit) {
            val len = buffer.get(pos).toInt() and 0xFF
            pos++
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                // Pointer compression (not expected in simple single query, skip)
                pos++
                break
            }
            if (pos + len > limit) return null

            if (sb.isNotEmpty()) sb.append(".")
            for (i in 0 until len) {
                sb.append(buffer.get(pos + i).toInt().toChar())
            }
            pos += len
        }

        if (pos + 4 > limit) {
            return DnsQuery(txId, flags, sb.toString(), 1, 1)
        }

        val qType = buffer.getShort(pos).toInt() and 0xFFFF
        val qClass = buffer.getShort(pos + 2).toInt() and 0xFFFF

        return DnsQuery(
            transactionId = txId,
            flags = flags,
            questionName = sb.toString(),
            questionType = qType,
            questionClass = qClass
        )
    }

    /**
     * Builds a DNS NXDOMAIN response packet that can be written back into TUN interface
     */
    fun buildNxDomainResponse(
        originalPacket: ByteBuffer,
        ipHeader: IpHeader,
        udpHeader: UdpHeader,
        txId: Int
    ): ByteArray? {
        try {
            val origTotalLen = ipHeader.totalLength
            val dnsOffset = udpHeader.payloadOffset
            val dnsLen = udpHeader.payloadLength

            // Response packet size: same as query packet (echoing question)
            val respBytes = ByteArray(origTotalLen)

            // Copy original packet
            val oldPos = originalPacket.position()
            originalPacket.position(0)
            originalPacket.get(respBytes, 0, origTotalLen)
            originalPacket.position(oldPos)

            val respBuf = ByteBuffer.wrap(respBytes)

            // Swap IP addresses
            // src IP offset: 12, dst IP offset: 16
            for (i in 0 until 4) {
                val src = respBytes[12 + i]
                respBytes[12 + i] = respBytes[16 + i]
                respBytes[16 + i] = src
            }

            // Swap UDP ports
            respBuf.putShort(ipHeader.payloadOffset, udpHeader.destPort.toShort())
            respBuf.putShort(ipHeader.payloadOffset + 2, udpHeader.sourcePort.toShort())

            // DNS Header at dnsOffset
            // Flags: QR=1 (response), AA=1, RD=1, RA=1, RCODE=3 (NXDOMAIN) -> 0x8183
            respBuf.putShort(dnsOffset, txId.toShort())
            respBuf.putShort(dnsOffset + 2, 0x8183.toShort())

            // Zero out checksums before computing
            respBuf.putShort(10, 0.toShort()) // IP checksum
            respBuf.putShort(ipHeader.payloadOffset + 6, 0.toShort()) // UDP checksum

            // Recalculate IPv4 checksum
            val ipChecksum = computeIpChecksum(respBytes, 0, ipHeader.headerLength)
            respBuf.putShort(10, ipChecksum.toShort())

            // In IPv4, UDP checksum 0 is valid and means no checksum
            respBuf.putShort(ipHeader.payloadOffset + 6, 0.toShort())

            return respBytes
        } catch (_: Exception) {
            return null
        }
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
