package com.fbprivacy.shield

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException

class LocalhostBlocker(
    private val statsRepository: StatsRepository
) {
    private val tag = "LocalhostBlocker"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val tcpPorts = listOf(12387, 12388)
    private val udpPorts = listOf(12580, 12581, 12582, 12583, 12584, 12585)

    private val serverSockets = mutableListOf<ServerSocket>()
    private val datagramSockets = mutableListOf<DatagramSocket>()
    private var isRunning = false

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true
        Log.i(tag, "Starting localhost port blocking...")

        val localhost = try {
            InetAddress.getByName("127.0.0.1")
        } catch (e: Exception) {
            InetAddress.getLoopbackAddress()
        }

        // Bind TCP ServerSockets
        for (port in tcpPorts) {
            try {
                val serverSocket = ServerSocket()
                serverSocket.reuseAddress = true
                serverSocket.bind(InetSocketAddress(localhost, port), 50)
                serverSockets.add(serverSocket)
                Log.d(tag, "Successfully bound TCP port $port")

                scope.launch {
                    listenAndCloseTcp(serverSocket, port)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to bind TCP port $port (may already be in use): ${e.message}")
            }
        }

        // Bind UDP DatagramSockets
        for (port in udpPorts) {
            try {
                val socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(localhost, port))
                datagramSockets.add(socket)
                Log.d(tag, "Successfully bound UDP port $port")

                scope.launch {
                    drainUdp(socket, port)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to bind UDP port $port (may already be in use): ${e.message}")
            }
        }
    }

    private fun listenAndCloseTcp(serverSocket: ServerSocket, port: Int) {
        while (isRunning && !serverSocket.isClosed) {
            try {
                val clientSocket = serverSocket.accept()
                statsRepository.recordBlockedPacket(
                    bytes = 54, // standard SYN-ACK size
                    reason = "Blocked localhost TCP tracker listener",
                    destination = "127.0.0.1:$port",
                    protocol = "TCP"
                )
                try {
                    clientSocket.close()
                } catch (_: Exception) {}
            } catch (e: SocketException) {
                if (!isRunning) break
            } catch (e: Exception) {
                Log.e(tag, "Error handling TCP client on port $port: ${e.message}")
            }
        }
    }

    private fun drainUdp(socket: DatagramSocket, port: Int) {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isRunning && !socket.isClosed) {
            try {
                socket.receive(packet)
                val len = packet.length
                statsRepository.recordBlockedPacket(
                    bytes = len,
                    reason = "Blocked localhost UDP tracker probe",
                    destination = "127.0.0.1:$port",
                    protocol = "UDP"
                )
            } catch (e: SocketException) {
                if (!isRunning) break
            } catch (e: Exception) {
                Log.e(tag, "Error draining UDP port $port: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        Log.i(tag, "Stopping localhost port blocking...")

        for (ss in serverSockets) {
            try {
                ss.close()
            } catch (_: Exception) {}
        }
        serverSockets.clear()

        for (ds in datagramSockets) {
            try {
                ds.close()
            } catch (_: Exception) {}
        }
        datagramSockets.clear()
    }
}
