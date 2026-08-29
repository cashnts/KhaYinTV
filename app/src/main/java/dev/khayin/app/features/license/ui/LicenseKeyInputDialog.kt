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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.khayin.app.features.license.LicenseRepository
import dev.khayin.app.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

@Composable
fun LicenseKeyInputDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {}
) {
    var keyText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141416))
                .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = NuvioTheme.colors.Primary
                    )
                    Text(
                        text = "Activate KhaYin License",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Enter your license key (e.g. KHAYIN-XXXX-XXXX-XXXX) to unlock full access across all your devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA0A0A8)
                )

                // Input Box
                BasicTextField(
                    value = keyText,
                    onValueChange = { keyText = it.uppercase() },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (keyText.isNotBlank() && !isLoading) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    LicenseRepository.activate(keyText).fold(
                                        onSuccess = {
                                            isLoading = false
                                            onSuccess()
                                            onDismiss()
                                        },
                                        onFailure = { err ->
                                            isLoading = false
                                            errorMessage = err.message ?: "Invalid license key."
                                        }
                                    )
                                }
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E24))
                        .border(1.dp, Color(0xFF3E3E48), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282830),
                            focusedContainerColor = Color(0xFF3A3A46)
                        )
                    ) {
                        Text(text = "Cancel", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (keyText.isNotBlank() && !isLoading) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    LicenseRepository.activate(keyText).fold(
                                        onSuccess = {
                                            isLoading = false
                                            onSuccess()
                                            onDismiss()
                                        },
                                        onFailure = { err ->
                                            isLoading = false
                                            errorMessage = err.message ?: "Invalid license key."
                                        }
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Primary,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground
                        )
                    ) {
                        Text(
                            text = if (isLoading) "Validating..." else "Activate",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
