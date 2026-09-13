package dev.khayin.app.features.license

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SystemServiceConfig(
    // 1. Operational & Emergency Controls
    val maintenanceMode: Boolean = false,
    val maintenanceNotice: String = "",
    val streamingDisabled: Boolean = false,
    val streamingDisabledNotice: String = "",

    // 2. Global Announcements / Live Notice Banner
    val broadcastTitle: String = "",
    val broadcastMessage: String = "",
    val broadcastSeverity: String = "INFO", // "INFO", "WARNING", "CRITICAL", "PROMO"
    val broadcastTimestamp: Long = 0L,
    val broadcastDismissable: Boolean = true,
    val broadcastActionUrl: String = "",
    val broadcastActionLabel: String = "",

    // 3. Dynamic Feature Flags
    val enableDownloads: Boolean = true,
    val enablePlugins: Boolean = true,
    val enableP2p: Boolean = true,
    val enableDebrid: Boolean = true,
    val enableTrailerPlayback: Boolean = true,
    val enableSimklTracking: Boolean = true,
    val enableTraktTracking: Boolean = true,

    // 4. Over-The-Air Addon Management
    val presetAddons: List<String> = emptyList(),
    val disabledAddons: List<String> = emptyList(), // Instant remote blacklist for broken/malicious addons

    // 5. Version Gating
    val minSupportedVersion: String = "",
    val unsupportedVersionThreshold: String = "",
    val updateRequiredNotice: String = "",
    val updateDownloadUrl: String = "",
    val forceUpdateUrl: String = "",

    // 6. Extensible Live Key-Value Parameters
    val dynamicConfig: Map<String, String> = emptyMap(),
)

data class AppSettingsRecord(
    val id: String = "global",
    val config: Any? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

object AdminControlRepository {
    private const val TAG = "AdminControlRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val gson: Gson = GsonBuilder().create()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _config = MutableStateFlow(SystemServiceConfig())
    val config: StateFlow<SystemServiceConfig> = _config.asStateFlow()

    private val _dismissedBroadcastTimestamp = MutableStateFlow(0L)
    val dismissedBroadcastTimestamp: StateFlow<Long> = _dismissedBroadcastTimestamp.asStateFlow()

    fun isAddonBlocked(manifestOrTransportUrl: String): Boolean {
        if (manifestOrTransportUrl.isBlank()) return false
        val normalized = manifestOrTransportUrl.trim().lowercase()
        return _config.value.disabledAddons.any { disabled ->
            val d = disabled.trim().lowercase()
            d.isNotBlank() && (normalized.contains(d) || d.contains(normalized))
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return _config.value.dynamicConfig[key]?.takeIf { it.isNotBlank() } ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return _config.value.dynamicConfig[key]?.toBooleanStrictOrNull() ?: defaultValue
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return _config.value.dynamicConfig[key]?.toIntOrNull() ?: defaultValue
    }

    fun dismissBroadcast(timestamp: Long = 0L) {
        val target = if (timestamp > 0L) timestamp else _config.value.broadcastTimestamp
        _dismissedBroadcastTimestamp.value = target
        LicenseStorage.saveDismissedBroadcastTimestamp(target)
    }

    fun refreshDismissedTimestamp() {
        val stored = LicenseStorage.loadDismissedBroadcastTimestamp()
        if (stored > _dismissedBroadcastTimestamp.value) {
            _dismissedBroadcastTimestamp.value = stored
        }
    }

    fun isCurrentVersionUnsupported(): Boolean {
        return isVersionUnsupported(BuildConfig.VERSION_NAME)
    }

    fun isVersionUnsupported(clientVersion: String?): Boolean {
        val current = normalizeVersion(clientVersion)
        if (current.isBlank()) return false

        val cfg = _config.value
        val threshold = normalizeVersion(cfg.unsupportedVersionThreshold)
        if (threshold.isNotBlank()) {
            if (compareVersions(current, threshold) <= 0) {
                return true
            }
        }

        val minVer = normalizeVersion(cfg.minSupportedVersion)
        if (minVer.isNotBlank()) {
            if (compareVersions(current, minVer) < 0) {
                return true
            }
        }

        return false
    }

    private fun normalizeVersion(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    private fun parseVersionParts(raw: String?): List<Int>? {
        val normalized = normalizeVersion(raw)
        if (normalized.isBlank()) return null
        val parts = normalized.split('.', '-', '_')
            .filter { it.isNotBlank() }
            .mapNotNull { token -> token.takeWhile { it.isDigit() }.toIntOrNull() }
        return parts.takeIf { it.isNotEmpty() }
    }

    private fun compareVersions(v1: String?, v2: String?): Int {
        val p1 = parseVersionParts(v1)
        val p2 = parseVersionParts(v2)
        if (p1 == null || p2 == null) {
            val s1 = normalizeVersion(v1)
            val s2 = normalizeVersion(v2)
            return s1.compareTo(s2)
        }
        val max = maxOf(p1.size, p2.size)
        for (i in 0 until max) {
            val n1 = p1.getOrElse(i) { 0 }
            val n2 = p2.getOrElse(i) { 0 }
            if (n1 != n2) return n1.compareTo(n2)
        }
        return 0
    }

    fun startPolling() {
        refreshDismissedTimestamp()
        if (pollingJob != null) return
        pollingJob = scope.launch {
            fetchConfig()
            while (true) {
                delay(45 * 1000L) // Poll remote service config every 45s for responsive controls
                fetchConfig()
            }
        }
    }

    private fun supabaseRestUrl(): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$base/rest/v1"
    }

    suspend fun fetchConfig(): SystemServiceConfig {
        refreshDismissedTimestamp()
        return try {
            val restUrl = supabaseRestUrl()
            val appSettingsUrl = "$restUrl/app_settings?id=eq.global&select=*"
            val apiKey = BuildConfig.SUPABASE_ANON_KEY

            val secHeaders = KhaYinSecurityBridge.buildSecureHeaders("GET", appSettingsUrl, "")
            val reqBuilder = Request.Builder()
                .url(appSettingsUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")

            secHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && !body.startsWith("<")) {
                val records = runCatching {
                    gson.fromJson(body, Array<AppSettingsRecord>::class.java)?.toList()
                }.getOrNull()

                if (!records.isNullOrEmpty()) {
                    val rawConfig = records.first().config
                    val configJson = gson.toJson(rawConfig)
                    val parsed = runCatching {
                        gson.fromJson(configJson, SystemServiceConfig::class.java)
                    }.getOrNull()

                    if (parsed != null) {
                        if (_config.value != parsed) {
                            _config.value = parsed
                        }
                        return parsed
                    }
                }
            }

            // Fallback: query license_keys for SYSTEM_CONFIG row
            val legacyUrl = "$restUrl/license_keys?key=eq.SYSTEM_CONFIG&select=*"
            val legacySecHeaders = KhaYinSecurityBridge.buildSecureHeaders("GET", legacyUrl, "")
            val legacyReq = Request.Builder()
                .url(legacyUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
            legacySecHeaders.forEach { (k, v) -> legacyReq.addHeader(k, v) }

            val legacyResp = httpClient.newCall(legacyReq.build()).execute()
            val legacyBody = legacyResp.body?.string() ?: ""
            if (legacyResp.isSuccessful && !legacyBody.startsWith("<")) {
                val records = runCatching {
                    gson.fromJson(legacyBody, Array<SupabaseLicenseRecord>::class.java)?.toList()
                }.getOrNull()
                if (!records.isNullOrEmpty()) {
                    val notes = records.first().notes
                    if (!notes.isNullOrBlank()) {
                        val parsed = runCatching {
                            gson.fromJson(notes, SystemServiceConfig::class.java)
                        }.getOrNull()
                        if (parsed != null) {
                            _config.value = parsed
                            return parsed
                        }
                    }
                }
            }
            _config.value
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote config: ${e.message}")
            _config.value
        }
    }
}
