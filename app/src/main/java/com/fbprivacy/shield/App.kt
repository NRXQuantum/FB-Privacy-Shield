package com.fbprivacy.shield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class App : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var statsRepository: StatsRepository
        private set
    lateinit var endpointBlocklist: EndpointBlocklist
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesManager = PreferencesManager(this)
        statsRepository = StatsRepository(preferencesManager)
        endpointBlocklist = EndpointBlocklist(this, preferencesManager)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fb_privacy_shield_channel"
        const val NOTIFICATION_ID = 1001

        const val PACKAGE_FACEBOOK_KATANA = "com.facebook.katana"
        const val PACKAGE_FACEBOOK_ORCA = "com.facebook.orca"
        const val PACKAGE_FACEBOOK_LITE = "com.facebook.lite"

        lateinit var instance: App
            private set

        fun isFacebookInstalled(context: Context): Boolean {
            val pm = context.packageManager
            val packagesToCheck = listOf(
                PACKAGE_FACEBOOK_KATANA,
                PACKAGE_FACEBOOK_ORCA,
                PACKAGE_FACEBOOK_LITE
            )
            for (pkg in packagesToCheck) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    return true
                } catch (_: PackageManager.NameNotFoundException) {}
            }
            return false
        }

        fun getInstalledFacebookPackages(context: Context): List<String> {
            val pm = context.packageManager
            val packagesToCheck = listOf(
                PACKAGE_FACEBOOK_KATANA,
                PACKAGE_FACEBOOK_ORCA,
                PACKAGE_FACEBOOK_LITE
            )
            val found = mutableListOf<String>()
            for (pkg in packagesToCheck) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    found.add(pkg)
                } catch (_: PackageManager.NameNotFoundException) {}
            }
            return found
        }
    }
}
