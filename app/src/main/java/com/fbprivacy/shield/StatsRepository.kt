package com.fbprivacy.shield

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class BlockedEvent(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val destination: String,
    val reason: String,
    val protocol: String,
    val bytesDropped: Int
)

class StatsRepository(private val preferencesManager: PreferencesManager) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _packetsBlocked = MutableStateFlow(0L)
    val packetsBlocked: StateFlow<Long> = _packetsBlocked.asStateFlow()

    private val _bytesSaved = MutableStateFlow(0L)
    val bytesSaved: StateFlow<Long> = _bytesSaved.asStateFlow()

    private val _uptimeSeconds = MutableStateFlow(0L)
    val uptimeSeconds: StateFlow<Long> = _uptimeSeconds.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<BlockedEvent>>(emptyList())
    val recentEvents: StateFlow<List<BlockedEvent>> = _recentEvents.asStateFlow()

    private var uptimeJob: Job? = null
    private var vpnStartTimeMs: Long = 0L

    init {
        scope.launch {
            try {
                val initialPackets = preferencesManager.totalPacketsBlockedFlow.first()
                val initialBytes = preferencesManager.totalBytesBlockedFlow.first()
                _packetsBlocked.value = initialPackets
                _bytesSaved.value = initialBytes
            } catch (_: Exception) {
            }
        }
    }

    fun setVpnActive(active: Boolean) {
        _isVpnActive.value = active
        if (active) {
            vpnStartTimeMs = System.currentTimeMillis()
            startUptimeTicker()
        } else {
            uptimeJob?.cancel()
            uptimeJob = null
            _uptimeSeconds.value = 0L
            persistStats()
        }
    }

    private fun startUptimeTicker() {
        uptimeJob?.cancel()
        uptimeJob = scope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - vpnStartTimeMs) / 1000L
                _uptimeSeconds.value = if (elapsed > 0) elapsed else 0L
                delay(1000L)
                if (elapsed % 30 == 0L) {
                    persistStats()
                }
            }
        }
    }

    fun recordBlockedPacket(bytes: Int, reason: String, destination: String, protocol: String = "TCP") {
        val newPackets = _packetsBlocked.value + 1L
        val newBytes = _bytesSaved.value + bytes.toLong()
        _packetsBlocked.value = newPackets
        _bytesSaved.value = newBytes

        val event = BlockedEvent(
            destination = destination,
            reason = reason,
            protocol = protocol,
            bytesDropped = bytes
        )
        val currentList = _recentEvents.value.toMutableList()
        currentList.add(0, event)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _recentEvents.value = currentList
    }

    fun resetStats() {
        _packetsBlocked.value = 0L
        _bytesSaved.value = 0L
        _recentEvents.value = emptyList()
        scope.launch {
            preferencesManager.resetStats()
        }
    }

    private fun persistStats() {
        val packets = _packetsBlocked.value
        val bytes = _bytesSaved.value
        scope.launch {
            preferencesManager.saveCumulativeStats(packets, bytes)
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }

        fun formatUptime(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        }
    }
}
