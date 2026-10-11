package com.fbprivacy.shield

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isCurrentlyRunning = FbVpnService.isRunning

        if (isCurrentlyRunning) {
            FbVpnService.stopService(this)
            updateTile(Tile.STATE_INACTIVE)
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent == null) {
                FbVpnService.startService(this)
                updateTile(Tile.STATE_ACTIVE)
            } else {
                // Open MainActivity so user can grant VPN permission
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    private fun updateTileState() {
        val state = if (FbVpnService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        updateTile(state)
    }

    private fun updateTile(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = getString(R.string.qs_tile_label)
        tile.contentDescription = if (state == Tile.STATE_ACTIVE) {
            getString(R.string.status_protected)
        } else {
            getString(R.string.status_not_protected)
        }
        tile.updateTile()
    }
}
