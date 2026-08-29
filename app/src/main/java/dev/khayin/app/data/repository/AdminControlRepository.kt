package dev.khayin.app.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdminControlRepository"

@Serializable
data class SystemServiceConfig(
    val maintenanceMode: Boolean = false,
    val maintenanceNotice: String = "",
    val streamingDisabled: Boolean = false,
    val streamingDisabledNotice: String = "",
    val broadcastTitle: String = "",
    val broadcastMessage: String = "",
    val broadcastSeverity: String = "INFO",
    val broadcastTimestamp: Long = 0L,
    val broadcastDismissable: Boolean = true,
    val broadcastActionUrl: String = "",
    val broadcastActionLabel: String = "",
    val enableDownloads: Boolean = true,
    val enablePlugins: Boolean = true,
    val enableP2p: Boolean = true,
    val enableDebrid: Boolean = true,
    val enableTrailerPlayback: Boolean = true,
    val enableSimklTracking: Boolean = true,
    val enableTraktTracking: Boolean = true,
    val presetAddons: List<String> = emptyList(),
    val disabledAddons: List<String> = emptyList(),
    val minSupportedVersion: String = "",
    val forceUpdateUrl: String = "",
    val dynamicConfig: Map<String, String> = emptyMap(),
)

@Singleton
class AdminControlRepository @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val _config = MutableStateFlow(SystemServiceConfig())
    val config: StateFlow<SystemServiceConfig> = _config.asStateFlow()

    private val _dismissedBroadcastTimestamp = MutableStateFlow(0L)
    val dismissedBroadcastTimestamp: StateFlow<Long> = _dismissedBroadcastTimestamp.asStateFlow()

    init {
        startPolling()
    }

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
        val value = _config.value.dynamicConfig[key]?.trim()?.lowercase() ?: return defaultValue
        return value == "true" || value == "1" || value == "yes" || value == "on"
    }

    fun dismissCurrentBroadcast() {
        _dismissedBroadcastTimestamp.value = _config.value.broadcastTimestamp
    }

    fun startPolling(intervalMs: Long = 300_000L) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (true) {
                fetchRemoteConfig()
                delay(intervalMs)
            }
        }
    }

    suspend fun fetchRemoteConfig(): Result<SystemServiceConfig> {
        return runCatching {
            // Can be fetched from Supabase app_settings or remote config URL
            _config.value
        }
    }
}
