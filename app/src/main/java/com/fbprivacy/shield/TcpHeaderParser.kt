package com.fbprivacy.shield

import java.nio.ByteBuffer

data class TcpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val sequenceNumber: Long,
    val ackNumber: Long,
    val headerLength: Int,
    val flags: Int,
    val isSyn: Boolean,
    val isAck: Boolean,
    val isFin: Boolean,
    val isRst: Boolean,
    val payloadOffset: Int,
    val payloadLength: Int
)

object TcpHeaderParser {

    fun parse(buffer: ByteBuffer, ipHeader: IpHeader): TcpHeader? {
        val tcpStart = ipHeader.payloadOffset
        val available = buffer.limit() - tcpStart
        if (available < 20) return null

        val srcPort = buffer.getShort(tcpStart).toInt() and 0xFFFF
        val dstPort = buffer.getShort(tcpStart + 2).toInt() and 0xFFFF
        val seq = buffer.getInt(tcpStart + 4).toLong() and 0xFFFFFFFFL
        val ack = buffer.getInt(tcpStart + 8).toLong() and 0xFFFFFFFFL

        val dataOffsetByte = buffer.get(tcpStart + 12).toInt() and 0xFF
        val headerLength = (dataOffsetByte shr 4) * 4
        if (headerLength < 20 || headerLength > available) return null

        val flags = buffer.get(tcpStart + 13).toInt() and 0xFF
        val isFin = (flags and 0x01) != 0
        val isSyn = (flags and 0x02) != 0
        val isRst = (flags and 0x04) != 0
        val isAck = (flags and 0x10) != 0

        val payloadOffset = tcpStart + headerLength
        val payloadLength = (ipHeader.totalLength - ipHeader.headerLength - headerLength).coerceAtLeast(0)

        return TcpHeader(
            sourcePort = srcPort,
            destPort = dstPort,
            sequenceNumber = seq,
            ackNumber = ack,
            headerLength = headerLength,
            flags = flags,
            isSyn = isSyn,
            isAck = isAck,
            isFin = isFin,
            isRst = isRst,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength
        )
    }

    /**
     * Extracts TLS ClientHello Server Name Indication (SNI) if present in payload
     */
    fun extractTlsSni(buffer: ByteBuffer, tcpHeader: TcpHeader): String? {
        if (tcpHeader.payloadLength < 44) return null
        val offset = tcpHeader.payloadOffset
        val limit = offset + tcpHeader.payloadLength
        if (limit > buffer.limit()) return null

        try {
            // Check TLS Handshake Record (Content Type 22 = 0x16)
            val contentType = buffer.get(offset).toInt() and 0xFF
            if (contentType != 0x16) return null

            // Handshake Type 1 = Client Hello
            val handshakeType = buffer.get(offset + 5).toInt() and 0xFF
            if (handshakeType != 0x01) return null

            // Skip: record header (5) + handshake type (1) + length (3) + client_version (2) + random (32) = 43
            var pos = offset + 43
            if (pos >= limit) return null

            // Session ID length
            val sessionIdLen = buffer.get(pos).toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos + 2 >= limit) return null

            // Cipher Suites length
            val cipherSuitesLen = buffer.getShort(pos).toInt() and 0xFFFF
            pos += 2 + cipherSuitesLen
            if (pos + 1 >= limit) return null

            // Compression methods length
            val compMethodsLen = buffer.get(pos).toInt() and 0xFF
            pos += 1 + compMethodsLen
            if (pos + 2 >= limit) return null

            // Extensions length
            val extensionsLen = buffer.getShort(pos).toInt() and 0xFFFF
            pos += 2
            val extensionsEnd = (pos + extensionsLen).coerceAtMost(limit)

            while (pos + 4 <= extensionsEnd) {
                val extType = buffer.getShort(pos).toInt() and 0xFFFF
                val extLen = buffer.getShort(pos + 2).toInt() and 0xFFFF
                pos += 4

                if (extType == 0x0000) { // server_name extension
                    if (pos + 2 > extensionsEnd) return null
                    // server_name_list length
                    pos += 2
                    if (pos + 3 > extensionsEnd) return null
                    val nameType = buffer.get(pos).toInt() and 0xFF
                    val nameLen = buffer.getShort(pos + 1).toInt() and 0xFFFF
                    pos += 3

                    if (nameType == 0 && pos + nameLen <= extensionsEnd) {
                        val hostBytes = ByteArray(nameLen)
                        for (i in 0 until nameLen) {
                            hostBytes[i] = buffer.get(pos + i)
                        }
                        return String(hostBytes, Charsets.UTF_8)
                    }
                }
                pos += extLen
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }

    /**
     * Extracts HTTP Request Line or Host header from plaintext HTTP packet
     */
    fun extractHttpTarget(buffer: ByteBuffer, tcpHeader: TcpHeader): String? {
        if (tcpHeader.payloadLength < 10) return null
        val offset = tcpHeader.payloadOffset
        val len = tcpHeader.payloadLength.coerceAtMost(1024)
        if (offset + len > buffer.limit()) return null

        try {
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                bytes[i] = buffer.get(offset + i)
            }
            val text = String(bytes, Charsets.ISO_8859_1)
            if (text.startsWith("GET ") || text.startsWith("POST ") || text.startsWith("HEAD ")) {
                val lines = text.split("\r\n")
                val requestLine = lines.firstOrNull() ?: ""
                val hostLine = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                val host = hostLine?.substringAfter(":")?.trim() ?: ""
                val path = requestLine.split(" ").getOrNull(1) ?: ""
                return if (host.isNotEmpty()) "$host$path" else path
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }
}
