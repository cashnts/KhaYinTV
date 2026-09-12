@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.khayin.app.features.license.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.khayin.app.ui.theme.NuvioTheme

@Composable
fun SportsPlusLockedDialog(
    onDismiss: () -> Unit,
    onUpgradeClick: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141416))
                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Glowing Lock Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.25f),
                                    Color(0xFFFF9500).copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFFF9500)),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Lock",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Plus Feature",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Upgrade to Plus to access sports streams and live TV channels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA0A0A8),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(focusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282830),
                            focusedContainerColor = Color(0xFF3A3A46),
                        ),
                    ) {
                        Text(
                            text = "Dismiss",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (onUpgradeClick != null) {
                        Button(
                            onClick = {
                                onUpgradeClick()
                                onDismiss()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFFFF9500),
                                focusedContainerColor = Color(0xFFFFB74D),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upgrade to Plus",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SportsLockPill(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Plus Locked",
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "PLUS",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}
