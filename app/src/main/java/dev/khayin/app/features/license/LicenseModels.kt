package dev.khayin.app.features.license

import com.google.gson.annotations.SerializedName

data class LicenseInfo(
    @SerializedName("key") val key: String = "",
    @SerializedName("status") val status: String = "active", // "active", "expired", "revoked"
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("tier") val tier: String? = "standard",
    @SerializedName("expires_at") val expiresAt: String? = null, // null = Lifetime
    @SerializedName("max_devices") val maxDevices: Int = 1,
    @SerializedName("active_devices") val activeDevices: Int = 1,
    @SerializedName("nonce") val nonce: String? = null,
    @SerializedName("profile_name") val profileName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("notes") val notes: String? = null,
) {
    val isLifetime: Boolean
        get() = expiresAt.isNullOrBlank() || expiresAt.equals("lifetime", ignoreCase = true)

    val isPlus: Boolean
        get() = tier?.contains("plus", ignoreCase = true) == true ||
                tier?.contains("vip", ignoreCase = true) == true ||
                tier?.contains("premium", ignoreCase = true) == true
}

data class SupabaseLicenseRecord(
    @SerializedName("key") val key: String? = null,
    @SerializedName("status") val status: String? = "active",
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("tier") val tier: String? = "standard",
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("max_devices") val maxDevices: Int? = 1,
    @SerializedName("active_devices") val activeDevices: Int? = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("notes") val notes: String? = null,
) {
    fun toLicenseInfo(fallbackKey: String = ""): LicenseInfo = LicenseInfo(
        key = (key ?: fallbackKey).trim().uppercase(),
        status = status ?: "active",
        customerName = customerName,
        tier = tier ?: "standard",
        expiresAt = expiresAt,
        maxDevices = maxDevices ?: 1,
        activeDevices = activeDevices ?: 0,
        createdAt = createdAt,
        notes = notes,
    )
}

data class SupabaseErrorResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("details") val details: String? = null,
    @SerializedName("hint") val hint: String? = null,
    @SerializedName("code") val code: String? = null,
)

sealed interface LicenseState {
    data object Loading : LicenseState
    data object Unlicensed : LicenseState
    data object Free : LicenseState
    data class Active(val info: LicenseInfo) : LicenseState
    data class Expired(val info: LicenseInfo) : LicenseState
    data class Revoked(val info: LicenseInfo) : LicenseState
}

val LicenseState.isActive: Boolean
    get() = this is LicenseState.Active

val LicenseState.isFree: Boolean
    get() = this is LicenseState.Free || this is LicenseState.Unlicensed

val LicenseState.isLicensed: Boolean
    get() = this is LicenseState.Active

val LicenseState.hasAdFreeAccess: Boolean
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
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("max_device") val maxDevice: Int? = null,
    @SerializedName("max_devices") val maxDevices: Int? = null,
    @SerializedName("nonce") val nonce: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
) {
    val resolvedMaxDevices: Int
        get() = maxDevices ?: maxDevice ?: 1
}

data class RawHttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)
