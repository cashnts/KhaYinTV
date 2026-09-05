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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenTelemetry (OTLP) HTTP trace forwarder for PostHog Tracing.
 * Batches structured spans and exports them to PostHog's `/i/v1/traces` endpoint.
 */
object PostHogTracer {
    private const val TAG = "PostHogTracer"
    private const val ENDPOINT = "https://us.i.posthog.com/i/v1/traces"
    private const val FLUSH_INTERVAL_MS = 3_000L
    private const val MAX_BATCH_SIZE = 50
    private const val MAX_QUEUE_CAPACITY = 300

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val spanQueue = ConcurrentLinkedQueue<SpanRecord>()
    private val isFlushing = AtomicBoolean(false)
    private var isStarted = false
    var isEnabled: Boolean = true

    private val random = SecureRandom()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    enum class SpanKind(val value: Int) {
        INTERNAL(1),
        SERVER(2),
        CLIENT(3),
        PRODUCER(4),
        CONSUMER(5)
    }

    enum class StatusCode(val value: Int) {
        UNSET(0),
        OK(1),
        ERROR(2)
    }

    data class SpanRecord(
        val traceId: String,
        val spanId: String,
        val parentSpanId: String? = null,
        val name: String,
        val kind: SpanKind = SpanKind.INTERNAL,
        val startTimeNano: Long,
        val endTimeNano: Long,
        val attributes: Map<String, Any> = emptyMap(),
        val statusCode: StatusCode = StatusCode.OK,
        val statusMessage: String? = null,
        val exception: Throwable? = null
    )

    class Span(
        val traceId: String,
        val spanId: String,
        val parentSpanId: String? = null,
        val name: String,
        val kind: SpanKind = SpanKind.INTERNAL,
        val startTimeNano: Long = System.currentTimeMillis() * 1_000_000L
    ) {
        private val attributes = mutableMapOf<String, Any>()
        private var statusCode = StatusCode.OK
        private var statusMessage: String? = null
        private var exception: Throwable? = null
        private var ended = false

        fun setAttribute(key: String, value: Any): Span {
            attributes[key] = value
            return this
        }

        fun setAttributes(map: Map<String, Any>): Span {
            attributes.putAll(map)
            return this
        }

        fun recordException(throwable: Throwable): Span {
            this.exception = throwable
            this.statusCode = StatusCode.ERROR
            this.statusMessage = throwable.message ?: throwable.javaClass.simpleName
            attributes["exception.type"] = throwable.javaClass.name
            attributes["exception.message"] = throwable.message ?: ""
            return this
        }

        fun setStatus(code: StatusCode, message: String? = null): Span {
            this.statusCode = code
            this.statusMessage = message
            return this
        }

        fun end() {
            if (ended) return
            ended = true
            val endTimeNano = System.currentTimeMillis() * 1_000_000L
            val record = SpanRecord(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parentSpanId,
                name = name,
                kind = kind,
                startTimeNano = startTimeNano,
                endTimeNano = endTimeNano,
                attributes = attributes.toMap(),
                statusCode = statusCode,
                statusMessage = statusMessage,
                exception = exception
            )
            enqueue(record)
        }
    }

    fun start(): PostHogTracer {
        if (isStarted) return this
        isStarted = true
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                if (spanQueue.isNotEmpty()) {
                    flushInternal()
                }
            }
        }
        return this
    }

    fun generateTraceId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateSpanId(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun startSpan(
        name: String,
        traceId: String = generateTraceId(),
        parentSpanId: String? = null,
        kind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, Any> = emptyMap()
    ): Span {
        val span = Span(
            traceId = traceId,
            spanId = generateSpanId(),
            parentSpanId = parentSpanId,
            name = name,
            kind = kind
        )
        if (attributes.isNotEmpty()) {
            span.setAttributes(attributes)
        }
        return span
    }

    inline fun <T> withSpan(
        name: String,
        parentSpanId: String? = null,
        traceId: String = generateTraceId(),
        kind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, Any> = emptyMap(),
        block: (Span) -> T
    ): T {
        val span = startSpan(name = name, traceId = traceId, parentSpanId = parentSpanId, kind = kind, attributes = attributes)
        return try {
            block(span)
        } catch (e: Throwable) {
            span.recordException(e)
            throw e
        } finally {
            span.end()
        }
    }

    private fun enqueue(record: SpanRecord) {
        if (!isEnabled) return
        if (spanQueue.size >= MAX_QUEUE_CAPACITY) {
            spanQueue.poll()
        }
        spanQueue.offer(record)
    }

    fun flush() {
        if (!isEnabled || spanQueue.isEmpty()) return
        scope.launch {
            flushInternal()
        }
    }

    private suspend fun flushInternal() {
        if (!isFlushing.compareAndSet(false, true)) return
        try {
            val batch = mutableListOf<SpanRecord>()
            while (batch.size < MAX_BATCH_SIZE) {
                val record = spanQueue.poll() ?: break
                batch.add(record)
            }
            if (batch.isEmpty()) return

            val payload = buildOtlpTracesJson(batch)
            sendPayload(payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to flush PostHog traces batch", e)
        } finally {
            isFlushing.set(false)
        }
    }

    private fun buildOtlpTracesJson(records: List<SpanRecord>): JSONObject {
        val deviceInfo = DeviceDetector.getDeviceInfo()
        val distinctId = LicenseStorage.loadLastKnownKey()?.takeIf { it.isNotBlank() }

        val resourceAttributes = JSONArray().apply {
            put(JSONObject().apply {
                put("key", "service.name")
                put("value", JSONObject().put("stringValue", "khayin-tv"))
            })
            put(JSONObject().apply {
                put("key", "platform")
                put("value", JSONObject().put("stringValue", deviceInfo.platform))
            })
            put(JSONObject().apply {
                put("key", "device.type")
                put("value", JSONObject().put("stringValue", deviceInfo.deviceType))
            })
            put(JSONObject().apply {
                put("key", "device.model")
                put("value", JSONObject().put("stringValue", deviceInfo.model))
            })
            put(JSONObject().apply {
                put("key", "device.brand")
                put("value", JSONObject().put("stringValue", deviceInfo.brand))
            })
            put(JSONObject().apply {
                put("key", "os.name")
                put("value", JSONObject().put("stringValue", deviceInfo.osName))
            })
            put(JSONObject().apply {
                put("key", "os.version")
                put("value", JSONObject().put("stringValue", deviceInfo.osVersion))
            })
            put(JSONObject().apply {
                put("key", "app.version")
                put("value", JSONObject().put("stringValue", BuildConfig.VERSION_NAME))
            })
            if (distinctId != null) {
                put(JSONObject().apply {
                    put("key", "distinct_id")
                    put("value", JSONObject().put("stringValue", distinctId))
                })
            }
        }

        val spansArray = JSONArray()
        records.forEach { record ->
            val spanJson = JSONObject().apply {
                put("traceId", record.traceId)
                put("spanId", record.spanId)
                if (record.parentSpanId != null) {
                    put("parentSpanId", record.parentSpanId)
                }
                put("name", record.name)
                put("kind", record.kind.value)
                put("startTimeUnixNano", record.startTimeNano.toString())
                put("endTimeUnixNano", record.endTimeNano.toString())

                val attrsArray = JSONArray()
                record.attributes.forEach { (key, value) ->
                    val attrVal = JSONObject()
                    when (value) {
                        is Boolean -> attrVal.put("boolValue", value)
                        is Number -> attrVal.put("intValue", value.toLong())
                        else -> attrVal.put("stringValue", value.toString())
                    }
                    attrsArray.put(JSONObject().apply {
                        put("key", key)
                        put("value", attrVal)
                    })
                }
                put("attributes", attrsArray)

                val statusJson = JSONObject().apply {
                    put("code", record.statusCode.value)
                    if (record.statusMessage != null) {
                        put("message", record.statusMessage)
                    }
                }
                put("status", statusJson)

                if (record.exception != null) {
                    val eventsArray = JSONArray()
                    val exceptionEvent = JSONObject().apply {
                        put("timeUnixNano", record.endTimeNano.toString())
                        put("name", "exception")
                        val eventAttrs = JSONArray().apply {
                            put(JSONObject().apply {
                                put("key", "exception.type")
                                put("value", JSONObject().put("stringValue", record.exception.javaClass.name))
                            })
                            put(JSONObject().apply {
                                put("key", "exception.message")
                                put("value", JSONObject().put("stringValue", record.exception.message ?: ""))
                            })
                            put(JSONObject().apply {
                                put("key", "exception.stacktrace")
                                put("value", JSONObject().put("stringValue", record.exception.stackTraceToString()))
                            })
                        }
                        put("attributes", eventAttrs)
                    }
                    eventsArray.put(exceptionEvent)
                    put("events", eventsArray)
                }
            }
            spansArray.put(spanJson)
        }

        val scopeSpans = JSONArray().apply {
            put(JSONObject().apply {
                put("scope", JSONObject().apply {
                    put("name", "khayin-tracer")
                    put("version", BuildConfig.VERSION_NAME)
                })
                put("spans", spansArray)
            })
        }

        val resourceSpans = JSONArray().apply {
            put(JSONObject().apply {
                put("resource", JSONObject().apply {
                    put("attributes", resourceAttributes)
                })
                put("scopeSpans", scopeSpans)
            })
        }

        return JSONObject().apply {
            put("resourceSpans", resourceSpans)
        }
    }

    private fun sendPayload(payload: JSONObject) {
        val url = "$ENDPOINT?token=${PostHogAnalytics.API_KEY}"
        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${PostHogAnalytics.API_KEY}")
            .addHeader("User-Agent", "KhaYin-TV/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "PostHog traces export returned HTTP ${response.code}: ${response.message}")
            }
        }
    }
}
