package dev.khayin.app.features.license.qr

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName

data class QrSessionRequest(
    @SerializedName("type") val type: String = "tv",
    @SerializedName("deviceName") val deviceName: String = "Android TV",
    @SerializedName("metadata") val metadata: Map<String, String> = emptyMap()
)

data class QrSessionResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("pairingCode") val pairingCode: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("expiresAt") val expiresAt: Long? = null,
    @SerializedName("ttlMs") val ttlMs: Long? = null,
    @SerializedName("activateUrl") val activateUrl: String? = null,
    @SerializedName("sseUrl") val sseUrl: String? = null,
    @SerializedName("checkUrl") val checkUrl: String? = null,
    @SerializedName("qrDataUrl") val qrDataUrl: String? = null,
    @SerializedName("error") val error: String? = null
)

data class QrStatusResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("authPayload") val authPayload: QrAuthPayload? = null,
    @SerializedName("error") val error: String? = null
)

data class QrAuthPayload(
    @SerializedName("type") val type: String? = null,
    @SerializedName("license") val license: QrLicensePayload? = null,
    @SerializedName("token") val token: String? = null
)

data class QrLicensePayload(
    @SerializedName("key") val key: String = "",
    @SerializedName("customerName") val customerName: String? = null,
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null
)

sealed interface QrActivationUiState {
    data object Loading : QrActivationUiState
    data class Active(
        val sessionId: String,
        val pairingCode: String,
        val activateUrl: String,
        val expiresAt: Long,
        val qrBitmap: Bitmap?,
        val isScanned: Boolean = false
    ) : QrActivationUiState
    data class Approved(val license: QrLicensePayload) : QrActivationUiState
    data object Expired : QrActivationUiState
    data class Error(val message: String) : QrActivationUiState
}
