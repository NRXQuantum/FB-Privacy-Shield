package com.fbprivacy.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(tag, "Device boot completed, checking auto-start preference...")

            val app = context.applicationContext as? App ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val autoStart = app.preferencesManager.autoStartOnBootFlow.first()
                    if (autoStart) {
                        // Check if VPN permission has already been granted
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent == null) {
                            Log.i(tag, "Auto-starting FB Privacy Shield on boot...")
                            FbVpnService.startService(context)
                        } else {
                            Log.w(tag, "VPN permission not prepared; cannot auto-start service without user interaction.")
                        }
                    } else {
                        Log.d(tag, "Auto-start on boot is disabled in preferences.")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error handling boot broadcast: ${e.message}")
                }
            }
        }
    }
}
