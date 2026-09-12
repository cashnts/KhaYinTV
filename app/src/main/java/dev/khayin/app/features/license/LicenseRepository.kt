package dev.khayin.app.features.license

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.khayin.app.BuildConfig
import dev.khayin.app.core.security.KhaYinSecurityBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object LicenseRepository {
    private const val TAG = "LicenseRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().create()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<LicenseState>(LicenseState.Loading)
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val isPlusMember: Boolean
        get() {
            val current = (_state.value as? LicenseState.Active)?.info
            return current?.isPlus == true
        }

    val canAccessSports: Boolean
        get() {
            if (dev.khayin.app.core.analytics.PostHogAnalytics.isSportsFreeForAll()) return true
            return isPlusMember
        }

    val isLicensed: Boolean
        get() = _state.value is LicenseState.Active

    val isFreeUser: Boolean
        get() = _state.value !is LicenseState.Active

    val hasAdFreeAccess: Boolean
        get() = isLicensed

    private var initialized = false
    private var heartbeatJob: Job? = null

    fun getOrCreateDeviceId(): String {
        var deviceId = LicenseStorage.loadDeviceId()
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            LicenseStorage.saveDeviceId(deviceId)
        }
        return deviceId
    }

    private fun isExpiredTimestamp(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrBlank() || expiresAt.equals("lifetime", ignoreCase = true)) return false
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = isoFormat.parse(expiresAt.substringBefore("Z").substringBefore("+"))
            date != null && date.before(Date())
        } catch (e: Exception) {
            try {
                val simpleFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = simpleFormat.parse(expiresAt)
                date != null && date.before(Date())
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun saveSecureLicensePayload(info: LicenseInfo) {
        val safeKey = info.key.trim().uppercase()
        val safeInfo = if (info.key == safeKey) info else info.copy(key = safeKey)
        val jsonStr = gson.toJson(safeInfo)
        LicenseStorage.saveLastKnownKey(safeKey)
        val encrypted = KhaYinSecurityBridge.encryptPayload(jsonStr, safeKey)
        LicenseStorage.saveLicensePayload(encrypted)
    }

    private fun loadSecureLicensePayload(): LicenseInfo? {
        val raw = LicenseStorage.loadLicensePayload() ?: return null
        if (raw.isBlank()) return null
        if (raw.startsWith("{")) {
            return runCatching { gson.fromJson(raw, LicenseInfo::class.java) }.getOrNull()
        }
        val lastKey = LicenseStorage.loadLastKnownKey() ?: ""
        if (lastKey.isNotBlank()) {
            val decrypted = KhaYinSecurityBridge.decryptPayload(raw, lastKey)
            if (decrypted.startsWith("{")) {
                val parsed = runCatching { gson.fromJson(decrypted, LicenseInfo::class.java) }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        return runCatching { gson.fromJson(raw, LicenseInfo::class.java) }.getOrNull()
    }

    fun initialize() {
        if (initialized) return
        initialized = true

        val cachedInfo = loadSecureLicensePayload()
        if (cachedInfo != null && cachedInfo.key.isNotBlank()) {
            if (isExpiredTimestamp(cachedInfo.expiresAt)) {
                _state.value = LicenseState.Expired(cachedInfo)
            } else {
                _state.value = LicenseState.Active(cachedInfo.copy(status = "active"))
                dev.khayin.app.core.analytics.PostHogAnalytics.identify(cachedInfo.key, mapOf(
                    "tier" to (cachedInfo.tier ?: "standard"),
                    "customer_name" to (cachedInfo.customerName ?: "")
                ))
            }
        } else if (LicenseStorage.isFreeMode()) {
            _state.value = LicenseState.Free
        } else {
            _state.value = LicenseState.Unlicensed
        }

        startHeartbeat()
    }

    fun continueForFree() {
        LicenseStorage.saveFreeMode(true)
        _state.value = LicenseState.Free
        _error.value = null
    }

    fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            if (_state.value is LicenseState.Active) {
                verifyRemoteLicense()
            }
            while (true) {
                delay(30 * 60 * 1000L) // 30 mins heartbeat interval
                if (_state.value is LicenseState.Active) {
                    verifyRemoteLicense()
                }
            }
        }
    }

    private fun supabaseRestUrl(): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$base/rest/v1"
    }

    suspend fun activate(rawKey: String): Result<LicenseInfo> = withContext(Dispatchers.IO) {
        val key = rawKey.trim().uppercase()
        if (key.isBlank()) {
            _error.value = "Please enter a valid license key"
            return@withContext Result.failure(IllegalArgumentException(_error.value))
        }

        _error.value = null
        val restUrl = supabaseRestUrl()
        val deviceId = getOrCreateDeviceId()
        val activationNonce = KhaYinSecurityBridge.generateNonce()
        val apiKey = BuildConfig.SUPABASE_ANON_KEY

        runCatching {
            // First attempt: call Postgres activate_license RPC to register device
            var rpcNonce: String? = null
            var rpcTier: String? = null
            var rpcCustomerName: String? = null
            var rpcExpiresAt: String? = null
            var rpcMaxDevices: Int? = null

            try {
                val rpcPayloadMap = mapOf(
                    "p_key" to key,
                    "p_device_id" to deviceId,
                    "p_device_name" to "Android TV",
                )
                val rpcPayload = gson.toJson(rpcPayloadMap)
                val rpcUrl = "$restUrl/rpc/activate_license"

                val secHeaders = KhaYinSecurityBridge.buildSecureHeaders("POST", rpcUrl, rpcPayload)
                val reqBuilder = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toRequestBody("application/json".toMediaType()))

                secHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val rawBody = response.body?.string() ?: ""

                if (response.isSuccessful && rawBody.trim().startsWith("{")) {
                    val resp = gson.fromJson(rawBody.trim(), LicenseActivationResponse::class.java)
                    if (!resp.success) {
                        val err = resp.error ?: resp.message ?: "License activation failed."
                        _error.value = err
                        throw IllegalStateException(err)
                    }
                    rpcNonce = resp.nonce
                    rpcTier = resp.tier
                    rpcCustomerName = resp.customerName
                    rpcExpiresAt = resp.expiresAt
                    rpcMaxDevices = resp.resolvedMaxDevices
                }
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                Log.w(TAG, "activate_license RPC call warning: ${e.message}")
            }

            // Always query the license_keys table to get full metadata (tier, profile, notes, expiration)
            val queryUrl = "$restUrl/license_keys?key=eq.$key&select=*"
            val qSecHeaders = KhaYinSecurityBridge.buildSecureHeaders("GET", queryUrl, "")
            val qReq = Request.Builder()
                .url(queryUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
            qSecHeaders.forEach { (k, v) -> qReq.addHeader(k, v) }

            val qResp = httpClient.newCall(qReq.build()).execute()
            val qBody = qResp.body?.string() ?: ""

            val info: LicenseInfo = if (qResp.isSuccessful && !qBody.startsWith("<")) {
                val records = runCatching {
                    gson.fromJson(qBody, Array<SupabaseLicenseRecord>::class.java)?.toList()
                }.getOrNull()

                if (records.isNullOrEmpty()) {
                    val err = "License key '$key' was not found."
                    _error.value = err
                    throw IllegalStateException(err)
                }

                records.first().toLicenseInfo(fallbackKey = key).copy(
                    nonce = rpcNonce ?: activationNonce
                )
            } else {
                if (!qResp.isSuccessful && rpcNonce == null) {
                    val errObj = runCatching { gson.fromJson(qBody, SupabaseErrorResponse::class.java) }.getOrNull()
                    val msg = errObj?.message ?: errObj?.error ?: "Supabase error (HTTP ${qResp.code})"
                    throw IllegalStateException(msg)
                }
                LicenseInfo(
                    key = key,
                    status = "active",
                    customerName = rpcCustomerName,
                    tier = rpcTier ?: "plus",
                    expiresAt = rpcExpiresAt,
                    maxDevices = rpcMaxDevices ?: 1,
                    activeDevices = 1,
                    nonce = rpcNonce ?: activationNonce,
                )
            }

            if (info.status.equals("revoked", ignoreCase = true)) {
                saveSecureLicensePayload(info)
                _state.value = LicenseState.Revoked(info)
                val err = "This license key has been revoked."
                _error.value = err
                throw IllegalStateException(err)
            }

            if (isExpiredTimestamp(info.expiresAt)) {
                saveSecureLicensePayload(info)
                _state.value = LicenseState.Expired(info)
                val err = "This license key has expired."
                _error.value = err
                throw IllegalStateException(err)
            }

            saveSecureLicensePayload(info)
            LicenseStorage.saveFreeMode(false)
            _state.value = LicenseState.Active(info)
            _error.value = null
            dev.khayin.app.core.analytics.PostHogAnalytics.identify(info.key, mapOf(
                "tier" to (info.tier ?: "plus"),
                "customer_name" to (info.customerName ?: ""),
                "device_id" to deviceId
            ))
            dev.khayin.app.core.analytics.PostHogAnalytics.capture("license_activated", mapOf(
                "license_key" to info.key,
                "tier" to (info.tier ?: "plus"),
                "expires_at" to (info.expiresAt ?: "")
            ))
            info
        }.onFailure { e ->
            Log.e(TAG, "Activation failed: ${e.message}", e)
            _error.value = e.message
        }
    }

    suspend fun verifyRemoteLicense(): Unit = withContext(Dispatchers.IO) {
        val currentInfo = (_state.value as? LicenseState.Active)?.info ?: return@withContext
        try {
            val restUrl = supabaseRestUrl()
            val key = currentInfo.key.trim().uppercase()
            val apiKey = BuildConfig.SUPABASE_ANON_KEY
            val checkUrl = "$restUrl/license_keys?key=eq.$key&select=*"

            val secHeaders = KhaYinSecurityBridge.buildSecureHeaders("GET", checkUrl, "")
            val req = Request.Builder()
                .url(checkUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
            secHeaders.forEach { (k, v) -> req.addHeader(k, v) }

            val response = httpClient.newCall(req.build()).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && !body.startsWith("<")) {
                val records = runCatching {
                    gson.fromJson(body, Array<SupabaseLicenseRecord>::class.java)?.toList()
                }.getOrNull()

                if (!records.isNullOrEmpty()) {
                    val updated = records.first().toLicenseInfo(fallbackKey = key).let { rec ->
                        if (rec.nonce.isNullOrBlank() && !currentInfo.nonce.isNullOrBlank()) {
                            rec.copy(nonce = currentInfo.nonce)
                        } else {
                            rec
                        }
                    }
                    if (updated.status.equals("revoked", ignoreCase = true)) {
                        _state.value = LicenseState.Revoked(updated)
                    } else if (isExpiredTimestamp(updated.expiresAt)) {
                        _state.value = LicenseState.Expired(updated)
                    } else {
                        saveSecureLicensePayload(updated)
                        _state.value = LicenseState.Active(updated)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify remote license: ${e.message}")
        }
    }

    fun deactivate() {
        LicenseStorage.clearLicensePayload()
        LicenseStorage.saveFreeMode(false)
        _state.value = LicenseState.Unlicensed
        _error.value = null
    }
}

