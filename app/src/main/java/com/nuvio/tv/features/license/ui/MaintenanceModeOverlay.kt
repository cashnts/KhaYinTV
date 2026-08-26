@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.features.license.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.features.license.AdminControlRepository
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

@Composable
fun MaintenanceModeOverlay(
    notice: String = ""
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF181820))
                .padding(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(54.dp)
            )

            Text(
                text = "System Maintenance",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = notice.ifBlank {
                    "KhaYin TV is currently undergoing scheduled maintenance. Services will resume shortly."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        AdminControlRepository.fetchConfig()
                    }
                },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Primary,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Check Again",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
