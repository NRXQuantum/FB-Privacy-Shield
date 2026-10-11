package com.fbprivacy.shield

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.fbprivacy.shield.ui.theme.RubyRed
import com.fbprivacy.shield.ui.theme.TextPrimary
import com.fbprivacy.shield.ui.theme.TextSecondary

@Composable
fun InfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val blockedEndpoints = listOf(
        "graph.facebook.com/logging_client_events",
        "graph.facebook.com/ajax/bz",
        "b-www.facebook.com/mobile/extra_data_collector",
        "b-www.facebook.com/mobile/parfait_trace_upload",
        "lookaside.facebook.com/ras/v2",
        "m.facebook.com/async/wbloks/log",
        "graph.facebook.com/mobile_crash_reports",
        "graph.facebook.com/dsp",
        "graph.facebook.com/ad_tracking",
        "graph.facebook.com/signal_collection"
    )

    val allowedDomains = listOf(
        "graph.facebook.com/graphql",
        "graph.facebook.com/graphqlbatch",
        "mqtt.facebook.com",
        "edge-mqtt.facebook.com",
        "*.fbcdn.net",
        "*.fbsbx.com",
        "*.whatsapp.net",
        "*.instagram.com"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("info_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "How It Works",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                InfoFeatureCard(
                    icon = Icons.Default.Security,
                    iconTint = Cyan40,
                    title = "Per-App Android VpnService",
                    description = "Unlike traditional system-wide VPNs, FB Privacy Shield configures 'addAllowedApplication()' to target ONLY com.facebook.katana (and Messenger). Your other apps, browser, banking, and games never pass through this tunnel."
                )

                Spacer(modifier = Modifier.height(12.dp))

                InfoFeatureCard(
                    icon = Icons.Default.CheckCircle,
                    iconTint = EmeraldGreen,
                    title = "100% Stock Android — No Root or Mods",
                    description = "Operates safely without root, without decompiling or modifying Facebook's APK, and without Xposed or LSPosed framework. Complies completely with Android 9+ security sandbox."
                )

                Spacer(modifier = Modifier.height(12.dp))

                InfoFeatureCard(
                    icon = Icons.Default.Lan,
                    iconTint = Cyan80,
                    title = "Localhost Port Pre-Binding",
                    description = "Facebook opens internal local socket listeners for telemetry across app instances. On service start, FB Privacy Shield pre-binds to 127.0.0.1 on TCP 12387, 12388 and UDP 12580..12585, rejecting tracker connections before Facebook can bind."
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "BLOCKED TRACKING ENDPOINTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = RubyRed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Packets to these endpoints are dropped immediately or answered with NXDOMAIN:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(blockedEndpoints) { endpoint ->
                EndpointItemRow(endpoint = endpoint, isBlocked = true)
                Spacer(modifier = Modifier.height(6.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "ALLOWED ESSENTIAL DOMAINS",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Feeds, messaging, photos, and video CDNs pass through transparently without disruption:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(allowedDomains) { domain ->
                EndpointItemRow(endpoint = domain, isBlocked = false)
                Spacer(modifier = Modifier.height(6.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "BLOCKED LOCALHOST PORTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cyan80,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardNavy),
                    modifier = Modifier.fillMaxWidth().border(1.dp, BorderCyan, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "• TCP Ports: 12387, 12388 (Connections accepted and immediately closed)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• UDP Ports: 12580, 12581, 12582, 12583, 12584, 12585 (Datagram probes drained/discarded)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InfoFeatureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
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
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun EndpointItemRow(endpoint: String, isBlocked: Boolean) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isBlocked) RubyRed else EmeraldGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = endpoint,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = if (isBlocked) TextPrimary else Cyan80,
                fontSize = 13.sp
            )
        }
    }
}
