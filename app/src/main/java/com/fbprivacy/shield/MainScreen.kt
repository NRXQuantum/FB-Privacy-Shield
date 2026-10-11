package com.fbprivacy.shield

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    isActive: Boolean,
    isFacebookInstalled: Boolean,
    installedFbPackages: List<String>,
    packetsBlocked: Long,
    bytesSaved: Long,
    uptimeSeconds: Long,
    recentEvents: List<BlockedEvent>,
    onToggleShield: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = if (isActive) EmeraldGreen else RubyRed,
        label = "status_color"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield Icon",
                    tint = Cyan40,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "FB Privacy Shield",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Per-App Tracking Blocker",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onNavigateInfo,
                    modifier = Modifier.testTag("info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "How it works",
                        tint = Cyan80
                    )
                }
                IconButton(
                    onClick = onNavigateSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Cyan80
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Facebook Package Detection Status Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFacebookInstalled) SurfaceNavy else CardNavy
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isFacebookInstalled) EmeraldGreen.copy(alpha = 0.5f) else Color(0xFFFFB74D),
                    RoundedCornerShape(12.dp)
                )
                .testTag("facebook_status_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFacebookInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Facebook Status",
                    tint = if (isFacebookInstalled) EmeraldGreen else Color(0xFFFFB74D),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isFacebookInstalled) {
                            "Targeting: ${installedFbPackages.joinToString(", ")}"
                        } else {
                            "Facebook App Not Detected (com.facebook.katana)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isFacebookInstalled) {
                            "Only Facebook traffic will be filtered. Other apps bypass the VPN."
                        } else {
                            "Shield runs in simulation/audit mode for testing."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Center Pulsing Shield Visual
        ShieldGlowVisual(
            isActive = isActive,
            onClick = onToggleShield
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Big Status Indicator & Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isActive) "Protected" else "Not Protected",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isActive,
                onCheckedChange = { onToggleShield() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkNavy,
                    checkedTrackColor = EmeraldGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CardNavy
                ),
                modifier = Modifier.testTag("shield_toggle_switch")
            )
        }

        Text(
            text = if (isActive) {
                "Intercepting Facebook telemetry and dropping tracking packets"
            } else {
                "Tap shield or toggle to activate per-app privacy protection"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MutedSlate,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Telemetry Stats Overview Card
        StatsOverviewCard(
            packetsBlocked = packetsBlocked,
            bytesSavedFormatted = StatsRepository.formatBytes(bytesSaved),
            uptimeFormatted = StatsRepository.formatUptime(uptimeSeconds)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live Blocked Feed Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INTERCEPTED LOG",
                style = MaterialTheme.typography.labelLarge,
                color = Cyan80,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "${recentEvents.size} events",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Intercepted Events List
        if (recentEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceNavy)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isActive) {
                        "Waiting for Facebook tracking traffic...\nOpen Facebook to see packets actively dropped."
                    } else {
                        "Activate shield to start blocking Facebook telemetry."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceNavy)
                    .padding(8.dp)
                    .testTag("intercepted_events_list")
            ) {
                items(recentEvents, key = { it.id }) { event ->
                    EventRow(event)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: BlockedEvent) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(event.timestamp))

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.destination,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${event.reason} • ${event.protocol}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RubyRed,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${event.bytesDropped} B",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cyan80,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
