package com.fbprivacy.shield

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream

class FbVpnService : VpnService() {

    private val tag = "FbVpnService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetRouter: PacketRouter? = null
    private var localhostBlocker: LocalhostBlocker? = null
    private var packetForwarder: PacketForwarder? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var notificationJob: Job? = null

    companion object {
        const val ACTION_START = "com.fbprivacy.shield.START_VPN"
        const val ACTION_STOP = "com.fbprivacy.shield.STOP_VPN"

        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, FbVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FbVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        Log.i(tag, "Starting FB Privacy Shield VPN Service...")

        val statsRepo = App.instance.statsRepository
        statsRepo.setVpnActive(true)

        // Show foreground notification immediately
        startForeground(App.NOTIFICATION_ID, buildNotification(statsRepo.packetsBlocked.value, statsRepo.bytesSaved.value))

        // Start localhost port blocker
        localhostBlocker = LocalhostBlocker(statsRepo).apply {
            start()
        }

        try {
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.198.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")

            // Configure per-app targeting
            val fbPackages = App.getInstalledFacebookPackages(this)
            var allowedAppConfigured = false

            for (pkg in fbPackages) {
                try {
                    builder.addAllowedApplication(pkg)
                    allowedAppConfigured = true
                    Log.i(tag, "Added allowed Facebook target application: $pkg")
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(tag, "Package $pkg not found for VPN filtering: ${e.message}")
                }
            }

            if (!allowedAppConfigured) {
                Log.w(tag, "Facebook application is not installed on this device. Running in simulation/audit mode for testing.")
                try {
                    builder.addAllowedApplication(packageName)
                } catch (_: Exception) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            val pfd = vpnInterface
            if (pfd == null) {
                Log.e(tag, "Failed to establish VPN interface (null ParcelFileDescriptor)")
                stopVpn()
                stopSelf()
                return
            }

            val tunOutputStream = FileOutputStream(pfd.fileDescriptor)
            packetForwarder = PacketForwarder(this, tunOutputStream, statsRepo)
            packetRouter = PacketRouter(
                vpnInterface = pfd,
                endpointBlocklist = App.instance.endpointBlocklist,
                statsRepository = statsRepo,
                packetForwarder = packetForwarder!!
            ).apply {
                start()
            }

            observeStatsForNotification()
            registerNetworkCallback()

        } catch (e: Exception) {
            Log.e(tag, "Fatal error setting up VPN interface: ${e.message}", e)
            stopVpn()
            stopSelf()
        }
    }

    private fun observeStatsForNotification() {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            val statsRepo = App.instance.statsRepository
            statsRepo.packetsBlocked.collectLatest { packets ->
                if (isActive && isRunning) {
                    val bytes = statsRepo.bytesSaved.value
                    val notification = buildNotification(packets, bytes)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    manager?.notify(App.NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(tag, "Underlying network changed: seamlessly continuing VPN tunnel on $network")
                    setUnderlyingNetworks(arrayOf(network))
                }

                override fun onLost(network: Network) {
                    Log.w(tag, "Underlying network lost: $network")
                    setUnderlyingNetworks(null)
                }
            }

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register network callback: ${e.message}")
        }
    }

    private fun buildNotification(packets: Long, bytes: Long): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, FbVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val formattedBytes = StatsRepository.formatBytes(bytes)
        val contentText = "$packets packets blocked | $formattedBytes saved"

        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("FB Privacy Shield Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun stopVpn() {
        Log.i(tag, "Stopping FB Privacy Shield VPN...")
        isRunning = false

        notificationJob?.cancel()
        notificationJob = null

        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null

        packetRouter?.stop()
        packetRouter = null

        localhostBlocker?.stop()
        localhostBlocker = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null

        App.instance.statsRepository.setVpnActive(false)
        stopForeground(true)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.w(tag, "VPN permission revoked by system or user")
        stopVpn()
        super.onRevoke()
    }
}
