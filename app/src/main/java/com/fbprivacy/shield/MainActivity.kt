package com.fbprivacy.shield

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.fbprivacy.shield.ui.theme.CardNavy
import com.fbprivacy.shield.ui.theme.Cyan40
import com.fbprivacy.shield.ui.theme.FbPrivacyShieldTheme
import com.fbprivacy.shield.ui.theme.MutedSlate
import com.fbprivacy.shield.ui.theme.TextPrimary
import com.fbprivacy.shield.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class Screen {
    MAIN,
    SETTINGS,
    INFO
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FbPrivacyShieldTheme {
                MainAppHost(activity = this)
            }
        }
    }
}

@Composable
fun MainAppHost(activity: ComponentActivity) {
    val context = activity
    val scope = rememberCoroutineScope()
    val statsRepo = App.instance.statsRepository
    val prefsManager = App.instance.preferencesManager

    val isVpnActive by statsRepo.isVpnActive.collectAsState()
    val packetsBlocked by statsRepo.packetsBlocked.collectAsState()
    val bytesSaved by statsRepo.bytesSaved.collectAsState()
    val uptimeSeconds by statsRepo.uptimeSeconds.collectAsState()
    val recentEvents by statsRepo.recentEvents.collectAsState()

    val autoStartOnBoot by prefsManager.autoStartOnBootFlow.collectAsState(initial = true)
    val notificationsEnabled by prefsManager.notificationsEnabledFlow.collectAsState(initial = true)
    val customBlocklist by prefsManager.customBlocklistFlow.collectAsState(initial = emptySet())

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var showVpnDeniedDialog by remember { mutableStateOf(false) }

    // Launcher for VPN system permission dialog
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            FbVpnService.startService(context)
        } else {
            showVpnDeniedDialog = true
        }
    }

    // Launcher for POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications disabled. VPN will still run silently in background.", Toast.LENGTH_SHORT).show()
        }
    }

    val isFbInstalled = remember { App.isFacebookInstalled(context) }
    val fbPackages = remember { App.getInstalledFacebookPackages(context) }

    fun toggleShield() {
        if (isVpnActive) {
            FbVpnService.stopService(context)
        } else {
            // Request Notification permission if needed on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnLauncher.launch(vpnIntent)
            } else {
                FbVpnService.startService(context)
            }
        }
    }

    fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Battery optimization already disabled for FB Shield", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showVpnDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showVpnDeniedDialog = false },
            title = {
                Text(
                    text = "VPN Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "FB Privacy Shield uses Android's local VpnService loopback interface to isolate and drop Facebook tracking packets. No remote server is used. Please grant VPN permission to enable protection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVpnDeniedDialog = false
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnLauncher.launch(vpnIntent)
                        } else {
                            FbVpnService.startService(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan40),
                    modifier = Modifier.testTag("grant_vpn_permission_button")
                ) {
                    Text("Try Again", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVpnDeniedDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            },
            containerColor = CardNavy
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_navigation"
        ) { screen ->
            when (screen) {
                Screen.MAIN -> {
                    MainScreen(
                        isActive = isVpnActive,
                        isFacebookInstalled = isFbInstalled,
                        installedFbPackages = fbPackages,
                        packetsBlocked = packetsBlocked,
                        bytesSaved = bytesSaved,
                        uptimeSeconds = uptimeSeconds,
                        recentEvents = recentEvents,
                        onToggleShield = { toggleShield() },
                        onNavigateSettings = { currentScreen = Screen.SETTINGS },
                        onNavigateInfo = { currentScreen = Screen.INFO },
                        modifier = Modifier.testTag("main_screen")
                    )
                }
                Screen.SETTINGS -> {
                    SettingsScreen(
                        autoStartOnBoot = autoStartOnBoot,
                        notificationsEnabled = notificationsEnabled,
                        customBlocklist = customBlocklist,
                        onToggleAutoStart = { enabled ->
                            scope.launch { prefsManager.setAutoStartOnBoot(enabled) }
                        },
                        onToggleNotifications = { enabled ->
                            scope.launch { prefsManager.setNotificationsEnabled(enabled) }
                        },
                        onAddBlockRule = { rule ->
                            scope.launch {
                                prefsManager.addCustomBlockRule(rule)
                                App.instance.endpointBlocklist.updateCustomRules(
                                    customBlocklist + rule.trim().lowercase()
                                )
                            }
                        },
                        onRemoveBlockRule = { rule ->
                            scope.launch {
                                prefsManager.removeCustomBlockRule(rule)
                                App.instance.endpointBlocklist.updateCustomRules(
                                    customBlocklist - rule
                                )
                            }
                        },
                        onRequestBatteryOptimization = { requestBatteryOptimization() },
                        onResetStats = { statsRepo.resetStats() },
                        onNavigateBack = { currentScreen = Screen.MAIN },
                        modifier = Modifier.testTag("settings_screen")
                    )
                }
                Screen.INFO -> {
                    InfoScreen(
                        onNavigateBack = { currentScreen = Screen.MAIN },
                        modifier = Modifier.testTag("info_screen")
                    )
                }
            }
        }
    }
}
