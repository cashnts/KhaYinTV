package dev.khayin.app.core.analytics

import android.os.Build
import android.util.Log
import dev.khayin.app.BuildConfig
import dev.khayin.app.features.license.LicenseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance OpenTelemetry (OTLP) HTTP log forwarder for PostHog Logs.
 * Batches structured application logs and sends them to PostHog's `/i/v1/logs` endpoint.
 */
object PostHogLogger {
    private const val TAG = "PostHogLogger"
    private const val ENDPOINT = "https://us.i.posthog.com/i/v1/logs"
    private const val FLUSH_INTERVAL_MS = 4_000L
    private const val MAX_BATCH_SIZE = 40
    private const val MAX_QUEUE_CAPACITY = 200

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logQueue = ConcurrentLinkedQueue<LogRecord>()
    private val isFlushing = AtomicBoolean(false)
    private var isStarted = false
    var isEnabled: Boolean = true

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    internal data class LogRecord(
        val timestampNano: Long = System.currentTimeMillis() * 1_000_000L,
        val severityNumber: Int,
        val severityText: String,
        val message: String,
        val tag: String,
        val attributes: Map<String, Any> = emptyMap()
    )

    fun start() {
        if (isStarted) return
        isStarted = true
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                if (logQueue.isNotEmpty()) {
                    flushInternal()
                }
            }
        }
    }

    fun log(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any>? = null
    ) {
        if (!isEnabled) return

        val normLevel = level.uppercase()
        val (sevNum, sevText) = when (normLevel) {
            "TRACE" -> 1 to "TRACE"
            "DEBUG" -> 5 to "DEBUG"
            "INFO" -> 9 to "INFO"
            "WARN", "WARNING" -> 13 to "WARN"
            "ERROR" -> 17 to "ERROR"
            "FATAL" -> 21 to "FATAL"
            else -> 9 to "INFO"
        }

        val deviceInfo = DeviceDetector.getDeviceInfo()
        val enrichedAttributes = mutableMapOf<String, Any>()
        enrichedAttributes["tag"] = tag
        enrichedAttributes["platform"] = deviceInfo.platform
        enrichedAttributes["device.type"] = deviceInfo.deviceType
        
        val distinctId = LicenseStorage.loadLastKnownKey()?.takeIf { it.isNotBlank() }
        if (distinctId != null) {
            enrichedAttributes["distinct_id"] = distinctId
        }

        if (attributes != null) {
            enrichedAttributes.putAll(attributes)
        }

        if (throwable != null) {
            enrichedAttributes["exception.type"] = throwable.javaClass.simpleName
            enrichedAttributes["exception.message"] = throwable.message ?: ""
            enrichedAttributes["exception.stacktrace"] = throwable.stackTraceToString().take(4000)
        }

        if (logQueue.size >= MAX_QUEUE_CAPACITY) {
            logQueue.poll() // Drop oldest to avoid unbounded memory growth
        }

        logQueue.offer(
            LogRecord(
                severityNumber = sevNum,
                severityText = sevText,
                message = message,
                tag = tag,
                attributes = enrichedAttributes
            )
        )

        // Immediate flush for error/fatal logs or when batch fills up
        if (sevNum >= 17 || logQueue.size >= MAX_BATCH_SIZE) {
            scope.launch {
                flushInternal()
            }
        }
    }

    fun flush() {
        scope.launch {
            flushInternal()
        }
    }

    private suspend fun flushInternal() {
        if (logQueue.isEmpty() || !isFlushing.compareAndSet(false, true)) return
        try {
            val batch = ArrayList<LogRecord>(MAX_BATCH_SIZE)
            while (batch.size < MAX_BATCH_SIZE) {
                val record = logQueue.poll() ?: break
                batch.add(record)
            }
            if (batch.isEmpty()) return

            val payload = buildOtlpPayload(batch)
            val url = "$ENDPOINT?token=${PostHogAnalytics.API_KEY}"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "KhaYin-TV/${BuildConfig.VERSION_NAME}")
                .build()

            withContext(Dispatchers.IO) {
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Failed to send logs to PostHog: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Network error sending logs to PostHog: ${e.message}")
                }
            }
        } finally {
            isFlushing.set(false)
        }
    }

    private fun buildOtlpPayload(records: List<LogRecord>): JSONObject {
        val deviceInfo = DeviceDetector.getDeviceInfo()
        val resourceAttrs = JSONArray().apply {
            put(buildAttr("service.name", "khayin-tv"))
            put(buildAttr("service.version", BuildConfig.VERSION_NAME))
            put(buildAttr("deployment.environment", if (BuildConfig.DEBUG) "debug" else "production"))
            put(buildAttr("os.name", deviceInfo.osName))
            put(buildAttr("os.version", deviceInfo.osVersion))
            put(buildAttr("device.type", deviceInfo.deviceType))
            put(buildAttr("device.platform", deviceInfo.platform))
            put(buildAttr("device.model", deviceInfo.model))
            put(buildAttr("device.brand", deviceInfo.brand))
            put(buildAttr("device.manufacturer", deviceInfo.manufacturer))
        }

        val logRecords = JSONArray()
        for (r in records) {
            val recObj = JSONObject().apply {
                put("timeUnixNano", r.timestampNano.toString())
                put("observedTimeUnixNano", r.timestampNano.toString())
                put("severityNumber", r.severityNumber)
                put("severityText", r.severityText.lowercase())
                put("body", JSONObject().put("stringValue", r.message))
                
                val attrsArray = JSONArray()
                for ((k, v) in r.attributes) {
                    attrsArray.put(buildAttr(k, v))
                }
                put("attributes", attrsArray)
            }
            logRecords.put(recObj)
        }

        val scopeLogs = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("scope", JSONObject().put("name", "khayin-tv-logger"))
                    put("logRecords", logRecords)
                }
            )
        }

        val resourceLogs = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("resource", JSONObject().put("attributes", resourceAttrs))
                    put("scopeLogs", scopeLogs)
                }
            )
        }

        return JSONObject().put("resourceLogs", resourceLogs)
    }

    private fun buildAttr(key: String, value: Any): JSONObject {
        val valObj = JSONObject()
        when (value) {
            is Boolean -> valObj.put("boolValue", value)
            is Number -> valObj.put("intValue", value.toLong())
            else -> valObj.put("stringValue", value.toString())
        }
        return JSONObject().apply {
            put("key", key)
            put("value", valObj)
        }
    }
}
