@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.khayin.app.features.license.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.khayin.app.R
import dev.khayin.app.features.license.LicenseRepository
import dev.khayin.app.features.license.LicenseState
import dev.khayin.app.ui.components.BrandWordmark
import dev.khayin.app.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

private val GateBackground = Color(0xFF08090C)
private val GateCardBackground = Color(0xFF12141A)
private val GateCardBorder = Color(0xFF262933)
private val GateInputBackground = Color(0xFF1A1C24)
private val GateInputBorder = Color(0xFF323644)
private val TextPrimary = Color(0xFFF0F3F8)
private val TextSecondary = Color(0xFF8E93A2)
private val AccentGreen = Color(0xFF00E676)

@Composable
fun LicenseGateScreen(
    onExit: () -> Unit
) {
    val licenseState by LicenseRepository.state.collectAsState()
    val repoError by LicenseRepository.error.collectAsState()
    var keyText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }

    BackHandler {
        onExit()
    }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }

    val displayedError = localError ?: repoError ?: when (licenseState) {
        is LicenseState.Expired -> "Your license key has expired. Please enter a renewed key."
        is LicenseState.Revoked -> "This license key has been revoked. Please enter a valid key."
        else -> null
    }

    fun submitKey() {
        val trimmed = keyText.trim().uppercase()
        if (trimmed.isBlank()) {
            localError = "Please enter your license key."
            return
        }
        isLoading = true
        localError = null
        scope.launch {
            LicenseRepository.activate(trimmed).fold(
                onSuccess = {
                    isLoading = false
                    localError = null
                },
                onFailure = { err ->
                    isLoading = false
                    localError = err.message ?: "License activation failed. Please check your key."
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GateBackground)
            .drawBehind {
                // Subtle radial ambient glow in the center-top
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentGreen.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height * 0.35f),
                        radius = size.width * 0.4f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left info column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                BrandWordmark(
                    contentDescription = stringResource(R.string.cd_nuvio),
                    modifier = Modifier.height(56.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.15f))
                            .border(1.dp, AccentGreen.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "ACTIVATION REQUIRED",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Welcome to KhaYin TV",
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = TextPrimary,
                        fontSize = 38.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "A valid KhaYin license key is required to unlock streaming access, live media hubs, and multi-device cloud synchronization.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Need a license key? Visit https://stream.khayin.net or contact your administrator.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    )
                }
            }

            // Right form card
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GateCardBackground)
                    .border(1.dp, GateCardBorder, RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = AccentGreen
                        )
                        Text(
                            text = "Enter License Key",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 20.sp
                        )
                    }

                    Text(
                        text = "Type your license key below and press Activate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    // Input Box
                    BasicTextField(
                        value = keyText,
                        onValueChange = {
                            keyText = it.uppercase()
                            localError = null
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(AccentGreen),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitKey() }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GateInputBackground)
                                    .border(
                                        width = if (isInputFocused) 2.dp else 1.dp,
                                        color = if (isInputFocused) AccentGreen else GateInputBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (keyText.isEmpty()) {
                                    Text(
                                        text = "KHAYIN-XXXX-XXXX-XXXX",
                                        color = TextSecondary.copy(alpha = 0.45f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocusRequester)
                            .onFocusChanged { isInputFocused = it.isFocused }
                    )

                    // Error Message
                    if (!displayedError.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FF5252))
                                .border(1.dp, Color(0x66FF5252), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = displayedError,
                                color = Color(0xFFFF6E6E),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onExit,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                focusedContainerColor = Color.White,
                                contentColor = TextPrimary,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Exit", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = { submitKey() },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.colors(
                                containerColor = AccentGreen,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black,
                                disabledContainerColor = AccentGreen.copy(alpha = 0.35f)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isLoading) "Validating..." else "Activate",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
