@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.khayin.app.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Composable
fun PrerollNoticeOverlay(
    visible: Boolean,
    title: String?,
    notice: String?,
    canSkip: Boolean,
    skippableAfter: Int = 5,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val skipFocusRequester = remember { FocusRequester() }
    var isSkipFocused by remember { mutableStateOf(false) }

    val initialSeconds = skippableAfter.coerceAtLeast(0)
    var secondsLeft by remember(skippableAfter) { mutableIntStateOf(initialSeconds) }
    LaunchedEffect(visible, skippableAfter) {
        secondsLeft = initialSeconds
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    LaunchedEffect(canSkip) {
        if (canSkip) {
            runCatching { skipFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Top-left notice banner: "Spotlight • Movie starts shortly"
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xEE1E1F24),
                            Color(0xCC2A2B32)
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, Color(0x33FFFFFF)),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300))
                )
                Column {
                    Text(
                        text = title?.takeIf { it.isNotBlank() } ?: "KhaYin Spotlight",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = notice?.takeIf { it.isNotBlank() } ?: "Spotlight • Movie starts shortly",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFB0B3C0),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        // Bottom-right Skip Intro button (or countdown)
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            if (canSkip) {
                Card(
                    onClick = onSkip,
                    modifier = Modifier
                        .focusRequester(skipFocusRequester)
                        .onFocusChanged { isSkipFocused = it.isFocused },
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = if (isSkipFocused) Color(0xFFE5A00D) else Color(0xDD1E1F24),
                        focusedContainerColor = Color(0xFFE5A00D)
                    ),
                    border = CardDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = BorderStroke(
                                width = if (isSkipFocused) 2.dp else 1.dp,
                                color = if (isSkipFocused) Color.White else Color(0x66FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(12.dp)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Skip Intro",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSkipFocused) Color.Black else Color.White
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Intro",
                            tint = if (isSkipFocused) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xAA1E1F24))
                        .border(
                            BorderStroke(1.dp, Color(0x22FFFFFF)),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Skip in ${secondsLeft}s",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
