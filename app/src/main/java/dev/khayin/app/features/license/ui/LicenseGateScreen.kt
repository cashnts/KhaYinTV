@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.khayin.app.features.license.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import dev.khayin.app.features.license.qr.QrActivationService
import dev.khayin.app.features.license.qr.QrActivationUiState
import dev.khayin.app.ui.components.BrandWordmark
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val GateBackground = Color(0xFF08090C)
private val GateCardBackground = Color(0xFF12141A)
private val GateCardBorder = Color(0xFF262933)
private val GateInputBackground = Color(0xFF1A1C24)
private val GateInputBorder = Color(0xFF323644)
private val TextPrimary = Color(0xFFF0F3F8)
private val TextSecondary = Color(0xFF8E93A2)
private val AccentGreen = Color(0xFF00E676)

private fun formatPairingCode(code: String): String {
    val clean = code.trim()
    return if (clean.length == 6 && !clean.contains("-")) {
        "${clean.substring(0, 3)}-${clean.substring(3)}"
    } else {
        clean
    }
}

@Composable
fun LicenseGateScreen(
    onExit: () -> Unit,
    onContinueForFree: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var qrState by remember { mutableStateOf<QrActivationUiState>(QrActivationUiState.Loading) }
    var showManualInput by remember { mutableStateOf(false) }

    // Manual input state
    var keyText by remember { mutableStateOf("") }
    var isManualLoading by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }
    val manualInputFocusRequester = remember { FocusRequester() }
    var isManualInputFocused by remember { mutableStateOf(false) }

    var listenerJob by remember { mutableStateOf<Job?>(null) }

    fun startQrSession() {
        listenerJob?.cancel()
        qrState = QrActivationUiState.Loading
        scope.launch {
            QrActivationService.createSession().fold(
                onSuccess = { sessionResp ->
                    val sessionId = sessionResp.sessionId ?: return@fold
                    val pairingCode = sessionResp.pairingCode ?: ""
                    val activateUrl = sessionResp.activateUrl ?: "https://auth.khayin.net/activate"
                    val expiresAt = sessionResp.expiresAt ?: (System.currentTimeMillis() + (sessionResp.ttlMs ?: 300000L))

                    val bitmap = QrActivationService.generateQrBitmap(activateUrl)

                    qrState = QrActivationUiState.Active(
                        sessionId = sessionId,
                        pairingCode = pairingCode,
                        activateUrl = activateUrl,
                        expiresAt = expiresAt,
                        qrBitmap = bitmap,
                        isScanned = false
                    )

                    listenerJob = QrActivationService.listenForApproval(
                        scope = scope,
                        sessionId = sessionId,
                        expiresAt = expiresAt,
                        onScanned = {
                            val current = qrState
                            if (current is QrActivationUiState.Active) {
                                qrState = current.copy(isScanned = true)
                            }
                        },
                        onApproved = { license ->
                            qrState = QrActivationUiState.Approved(license)
                            scope.launch {
                                LicenseRepository.applyApprovedLicense(
                                    key = license.key,
                                    customerName = license.customerName,
                                    tier = license.tier,
                                    expiresAt = license.expiresAt
                                )
                            }
                        },
                        onExpired = {
                            qrState = QrActivationUiState.Expired
                        },
                        onError = { errMsg ->
                            qrState = QrActivationUiState.Error(errMsg)
                        }
                    )
                },
                onFailure = { e ->
                    qrState = QrActivationUiState.Error(e.message ?: "Failed to generate pairing code")
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        startQrSession()
    }

    DisposableEffect(Unit) {
        onDispose {
            listenerJob?.cancel()
        }
    }

    BackHandler {
        if (showManualInput) {
            showManualInput = false
        } else {
            onExit()
        }
    }

    fun submitManualKey() {
        val trimmed = keyText.trim().uppercase()
        if (trimmed.isBlank()) {
            manualError = "Please enter your license key."
            return
        }
        isManualLoading = true
        manualError = null
        scope.launch {
            LicenseRepository.activate(trimmed).fold(
                onSuccess = {
                    isManualLoading = false
                    manualError = null
                },
                onFailure = { err ->
                    isManualLoading = false
                    manualError = err.message ?: "License activation failed. Please check your key."
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GateBackground)
            .drawBehind {
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
                .padding(horizontal = 64.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Branding, instructions, pairing code, actions
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                BrandWordmark(
                    contentDescription = stringResource(R.string.cd_nuvio),
                    modifier = Modifier.height(48.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Step 1: Open URL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Open",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "auth.khayin.net/activate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        fontSize = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 2: Code
                Text(
                    text = "2. Enter Pairing Code:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                val currentCode = when (val s = qrState) {
                    is QrActivationUiState.Active -> formatPairingCode(s.pairingCode)
                    is QrActivationUiState.Approved -> s.license.key.take(7)
                    else -> "— — —"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GateCardBackground)
                        .border(1.dp, GateCardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = currentCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Real-time Status Pill
                val (statusText, dotColor, badgeBg) = when (val s = qrState) {
                    is QrActivationUiState.Loading -> Triple(
                        "Connecting to auth server...",
                        Color(0xFFFFD54F),
                        Color(0x22FFD54F)
                    )
                    is QrActivationUiState.Active -> if (s.isScanned) {
                        Triple("Phone connected! Complete activation on phone...", Color(0xFF42A5F5), Color(0x2242A5F5))
                    } else {
                        Triple("Waiting for code entry...", AccentGreen, Color(0x2200E676))
                    }
                    is QrActivationUiState.Approved -> Triple(
                        "Device approved! Unlocking TV...",
                        AccentGreen,
                        Color(0x3300E676)
                    )
                    is QrActivationUiState.Expired -> Triple(
                        "Pairing code expired",
                        Color(0xFFFF5252),
                        Color(0x22FF5252)
                    )
                    is QrActivationUiState.Error -> Triple(
                        s.message,
                        Color(0xFFFF5252),
                        Color(0x22FF5252)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeBg)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = statusText,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showManualInput = !showManualInput },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White,
                            contentColor = TextPrimary,
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (showManualInput) Icons.Default.QrCode else Icons.Default.VpnKey,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showManualInput) "Show QR Code" else "Enter Key Manually",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (qrState is QrActivationUiState.Expired || qrState is QrActivationUiState.Error) {
                        Button(
                            onClick = { startQrSession() },
                            colors = ButtonDefaults.colors(
                                containerColor = AccentGreen,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Refresh Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onContinueForFree,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = AccentGreen,
                            contentColor = TextPrimary,
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Continue for Free (with Ads)", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = onExit,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.04f),
                            focusedContainerColor = Color(0xFFFF5252),
                            contentColor = TextSecondary,
                            focusedContentColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
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
                }
            }

            // Right Column: QR Code Display OR Manual Input Form
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (!showManualInput) {
                    // QR Code Card
                    Box(
                        modifier = Modifier
                            .size(360.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GateCardBackground)
                            .border(1.dp, GateCardBorder, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val s = qrState) {
                            is QrActivationUiState.Loading -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = AccentGreen,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "Loading QR code...",
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            is QrActivationUiState.Active -> {
                                if (s.qrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(310.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White)
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = s.qrBitmap.asImageBitmap(),
                                            contentDescription = "Scan QR to activate",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        color = AccentGreen,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            is QrActivationUiState.Approved -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Text(
                                        text = "Activated!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                            is QrActivationUiState.Expired -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "Pairing code expired",
                                        color = TextSecondary,
                                        fontSize = 15.sp
                                    )
                                    Button(
                                        onClick = { startQrSession() },
                                        colors = ButtonDefaults.colors(
                                            containerColor = AccentGreen,
                                            focusedContainerColor = Color.White,
                                            contentColor = Color.Black,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                                    ) {
                                        Text(
                                            text = "Refresh",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            is QrActivationUiState.Error -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = s.message,
                                        color = Color(0xFFFF6E6E),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { startQrSession() },
                                        colors = ButtonDefaults.colors(
                                            containerColor = AccentGreen,
                                            focusedContainerColor = Color.White,
                                            contentColor = Color.Black,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                                    ) {
                                        Text(
                                            text = "Retry",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Manual Input Form
                    Box(
                        modifier = Modifier
                            .width(420.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GateCardBackground)
                            .border(1.dp, GateCardBorder, RoundedCornerShape(24.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = AccentGreen
                                )
                                Text(
                                    text = "Enter License Key",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 18.sp
                                )
                            }

                            BasicTextField(
                                value = keyText,
                                onValueChange = {
                                    keyText = it.uppercase()
                                    manualError = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 15.sp,
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
                                    onDone = { submitManualKey() }
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GateInputBackground)
                                            .border(
                                                width = if (isManualInputFocused) 2.dp else 1.dp,
                                                color = if (isManualInputFocused) AccentGreen else GateInputBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (keyText.isEmpty()) {
                                            Text(
                                                text = "KHAYIN-XXXX-XXXX-XXXX",
                                                color = TextSecondary.copy(alpha = 0.45f),
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(manualInputFocusRequester)
                                    .onFocusChanged { isManualInputFocused = it.isFocused }
                            )

                            if (!manualError.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x22FF5252))
                                        .border(1.dp, Color(0x66FF5252), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = manualError ?: "",
                                        color = Color(0xFFFF6E6E),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showManualInput = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.06f),
                                        focusedContainerColor = Color.White,
                                        contentColor = TextPrimary,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                                ) {
                                    Text(
                                        text = "Back to QR",
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                Button(
                                    onClick = { submitManualKey() },
                                    enabled = !isManualLoading,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = AccentGreen,
                                        focusedContainerColor = Color.White,
                                        contentColor = Color.Black,
                                        focusedContentColor = Color.Black,
                                        disabledContainerColor = AccentGreen.copy(alpha = 0.35f)
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                                ) {
                                    Text(
                                        text = if (isManualLoading) "Activating..." else "Activate",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
