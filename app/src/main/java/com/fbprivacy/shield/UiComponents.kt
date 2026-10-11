package com.fbprivacy.shield

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.fbprivacy.shield.ui.theme.EmeraldGreenGlow
import com.fbprivacy.shield.ui.theme.MutedSlate
import com.fbprivacy.shield.ui.theme.RubyRed
import com.fbprivacy.shield.ui.theme.RubyRedGlow
import com.fbprivacy.shield.ui.theme.TextPrimary
import com.fbprivacy.shield.ui.theme.TextSecondary

@Composable
fun ShieldGlowVisual(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.12f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowColor = if (isActive) EmeraldGreenGlow else RubyRedGlow
    val activeBorderColor = if (isActive) EmeraldGreen else RubyRed

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .clickable(onClick = onClick)
            .testTag("shield_visual_button")
    ) {
        // Outer pulsing glow circle
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(glowColor)
        )

        // Middle ring
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isActive) Color(0x2200E676) else Color(0x22FF5252),
                            DarkNavy
                        )
                    )
                )
                .border(2.dp, activeBorderColor.copy(alpha = 0.5f), CircleShape)
        )

        // Center button core
        Surface(
            shape = CircleShape,
            color = CardNavy,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(130.dp)
                .border(3.dp, activeBorderColor, CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield State Icon",
                        tint = if (isActive) EmeraldGreen else RubyRed,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActive) "ON" else "OFF",
                        color = if (isActive) EmeraldGreen else RubyRed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatsOverviewCard(
    packetsBlocked: Long,
    bytesSavedFormatted: String,
    uptimeFormatted: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderCyan, RoundedCornerShape(20.dp))
            .testTag("stats_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "PROTECTION TELEMETRY",
                style = MaterialTheme.typography.labelLarge,
                color = Cyan80,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    icon = Icons.Default.Block,
                    label = "Packets Blocked",
                    value = packetsBlocked.toString(),
                    valueColor = EmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Default.Security,
                    label = "Data Saved",
                    value = bytesSavedFormatted,
                    valueColor = Cyan40,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Default.Timer,
                    label = "Uptime",
                    value = uptimeFormatted,
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = valueColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MutedSlate,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
