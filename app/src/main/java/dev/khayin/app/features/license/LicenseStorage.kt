package dev.khayin.app.features.license

import android.content.Context
import android.content.SharedPreferences

object LicenseStorage {
    private const val preferencesName = "nuvio_license_cache"
    private const val payloadKey = "license_payload"
    private const val lastKnownKey = "last_known_license_key"
    private const val deviceIdKey = "device_unique_id"
    private const val dismissedBroadcastKey = "dismissed_broadcast_timestamp"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    fun loadLicensePayload(): String? =
        preferences?.getString(payloadKey, null)

    fun saveLicensePayload(payload: String) {
        preferences?.edit()?.putString(payloadKey, payload)?.apply()
    }

    fun clearLicensePayload() {
        preferences?.edit()?.remove(payloadKey)?.apply()
    }

    fun loadLastKnownKey(): String? =
        preferences?.getString(lastKnownKey, null)

    fun saveLastKnownKey(key: String) {
        preferences?.edit()?.putString(lastKnownKey, key)?.apply()
    }

    fun loadDeviceId(): String? =
        preferences?.getString(deviceIdKey, null)

    fun saveDeviceId(deviceId: String) {
        preferences?.edit()?.putString(deviceIdKey, deviceId)?.apply()
    }

    fun loadDismissedBroadcastTimestamp(): Long =
        preferences?.getLong(dismissedBroadcastKey, 0L) ?: 0L

    fun saveDismissedBroadcastTimestamp(timestamp: Long) {
        preferences?.edit()?.putLong(dismissedBroadcastKey, timestamp)?.apply()
    }
}
