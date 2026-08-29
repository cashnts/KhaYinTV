@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.khayin.app.features.license.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.khayin.app.features.license.AdminControlRepository
import dev.khayin.app.features.license.SystemServiceConfig
import dev.khayin.app.ui.theme.NuvioTheme

@Composable
fun BroadcastNoticeBanner(
    config: SystemServiceConfig,
    modifier: Modifier = Modifier
) {
    if (config.broadcastMessage.isBlank()) return

    val severity = config.broadcastSeverity.uppercase()
    val (bgColor, borderColor, icon) = when (severity) {
        "CRITICAL", "ERROR" -> Triple(Color(0xFF2A1010), Color(0xFFFF5252), Icons.Default.Warning)
        "WARNING" -> Triple(Color(0xFF2A2010), Color(0xFFFFB74D), Icons.Default.Warning)
        "PROMO" -> Triple(Color(0xFF1E1430), Color(0xFFAB47BC), Icons.Default.Campaign)
        else -> Triple(Color(0xFF101C2A), Color(0xFF42A5F5), Icons.Default.Info)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                if (config.broadcastTitle.isNotBlank()) {
                    Text(
                        text = config.broadcastTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = config.broadcastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE0E0E0)
                )
            }

            if (config.broadcastDismissable) {
                Button(
                    onClick = { AdminControlRepository.dismissBroadcast(config.broadcastTimestamp) },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
