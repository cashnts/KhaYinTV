package com.nuvio.tv.features.license

import com.google.gson.annotations.SerializedName

data class LicenseInfo(
    val key: String,
    val status: String = "active", // "active", "expired", "revoked"
    val customerName: String? = null,
    val tier: String? = "standard",
    val expiresAt: String? = null, // null = Lifetime
    val maxDevices: Int = 1,
    val activeDevices: Int = 1,
    val nonce: String? = null,
    val profileName: String? = null,
    val createdAt: String? = null,
    val notes: String? = null,
) {
    val isLifetime: Boolean
        get() = expiresAt.isNullOrBlank() || expiresAt.equals("lifetime", ignoreCase = true)

    val isPlus: Boolean
        get() = tier?.contains("plus", ignoreCase = true) == true ||
                tier?.contains("vip", ignoreCase = true) == true ||
                tier?.contains("premium", ignoreCase = true) == true
}

data class SupabaseLicenseRecord(
    val key: String,
    val status: String = "active",
    @SerializedName("customer_name") val customerName: String? = null,
    val tier: String? = "standard",
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("max_devices") val maxDevices: Int = 1,
    @SerializedName("active_devices") val activeDevices: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    val notes: String? = null,
) {
    fun toLicenseInfo(): LicenseInfo = LicenseInfo(
        key = key,
        status = status,
        customerName = customerName,
        tier = tier,
        expiresAt = expiresAt,
        maxDevices = maxDevices,
        activeDevices = activeDevices,
        createdAt = createdAt,
        notes = notes,
    )
}

data class SupabaseErrorResponse(
    val message: String? = null,
    val error: String? = null,
    val details: String? = null,
    val hint: String? = null,
    val code: String? = null,
)

sealed interface LicenseState {
    data object Loading : LicenseState
    data object Unlicensed : LicenseState
    data class Active(val info: LicenseInfo) : LicenseState
    data class Expired(val info: LicenseInfo) : LicenseState
    data class Revoked(val info: LicenseInfo) : LicenseState
}

val LicenseState.isActive: Boolean
    get() = this is LicenseState.Active

val LicenseState.isExpired: Boolean
    get() = this is LicenseState.Expired || this is LicenseState.Revoked

val LicenseState.licenseKey: String?
    get() = when (this) {
        is LicenseState.Active -> info.key
        is LicenseState.Expired -> info.key
        is LicenseState.Revoked -> info.key
        else -> null
    }

val LicenseState.activeInfo: LicenseInfo?
    get() = when (this) {
        is LicenseState.Active -> info
        is LicenseState.Expired -> info
        is LicenseState.Revoked -> info
        else -> null
    }

data class LicenseActivationResponse(
    val success: Boolean = true,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("max_device") val maxDevice: Int? = null,
    @SerializedName("max_devices") val maxDevices: Int? = null,
    val nonce: String? = null,
    val status: String? = null,
    val key: String? = null,
    @SerializedName("customer_name") val customerName: String? = null,
    val tier: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val resolvedMaxDevices: Int
        get() = maxDevices ?: maxDevice ?: 1
}

data class RawHttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)
