package com.fbprivacy.shield

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fbprivacy.shield.ui.theme.BorderCyan
import com.fbprivacy.shield.ui.theme.CardNavy
import com.fbprivacy.shield.ui.theme.Cyan40
import com.fbprivacy.shield.ui.theme.Cyan80
import com.fbprivacy.shield.ui.theme.DarkNavy
import com.fbprivacy.shield.ui.theme.EmeraldGreen
import com.fbprivacy.shield.ui.theme.MutedSlate
import com.fbprivacy.shield.ui.theme.RubyRed
import com.fbprivacy.shield.ui.theme.SurfaceNavy
import com.fbprivacy.shield.ui.theme.TextPrimary
import com.fbprivacy.shield.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    autoStartOnBoot: Boolean,
    notificationsEnabled: Boolean,
    customBlocklist: Set<String>,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onAddBlockRule: (String) -> Unit,
    onRemoveBlockRule: (String) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onResetStats: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    var showResetDialog by remember { mutableStateOf(false) }
    var newRuleText by remember { mutableStateOf("") }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Statistics?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to reset all blocked packet counts and bandwidth metrics back to zero?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetStats()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                    modifier = Modifier.testTag("confirm_reset_stats_button")
                ) {
                    Text("Reset Now", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            },
            containerColor = CardNavy
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Shield Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "AUTOMATION & SYSTEM",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cyan80,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Auto Start
                SettingToggleItem(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Auto-start on boot",
                    subtitle = "Automatically re-activate shield after device reboots",
                    checked = autoStartOnBoot,
                    onCheckedChange = onToggleAutoStart,
                    testTag = "auto_start_toggle"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Persistent Notification
                SettingToggleItem(
                    icon = Icons.Default.Notifications,
                    title = "Foreground Notification",
                    subtitle = "Keep live blocking statistics in status bar",
                    checked = notificationsEnabled,
                    onCheckedChange = onToggleNotifications,
                    testTag = "notifications_toggle"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Battery Optimization
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderCyan, RoundedCornerShape(16.dp))
                        .clickable(onClick = onRequestBatteryOptimization)
                        .testTag("battery_optimization_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = "Battery Optimization",
                            tint = Cyan40,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Optimization Exemption",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Prevents Android from killing VPN background service",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "CUSTOM BLOCKLIST RULES",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cyan80,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add extra keywords or subdomains to drop from Facebook traffic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Add rule text field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newRuleText,
                        onValueChange = { newRuleText = it },
                        placeholder = { Text("e.g. telemetry.example.com", color = MutedSlate) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_rule_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan40,
                            unfocusedBorderColor = BorderCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Cyan40
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newRuleText.isNotBlank()) {
                                onAddBlockRule(newRuleText.trim())
                                newRuleText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan40),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_rule_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = DarkNavy)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (customBlocklist.isEmpty()) {
                item {
                    Text(
                        text = "No custom rules added yet. Default rules cover all known Facebook trackers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(customBlocklist.toList()) { rule ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = rule,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { onRemoveBlockRule(rule) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove rule",
                                    tint = RubyRed
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "DATA MANAGEMENT",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cyan80,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CardNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RubyRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .testTag("reset_stats_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Stats",
                        tint = RubyRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset All Tracking Statistics",
                        color = RubyRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderCyan, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Cyan40,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkNavy,
                    checkedTrackColor = EmeraldGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceNavy
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
