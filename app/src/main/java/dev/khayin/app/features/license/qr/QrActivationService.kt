package dev.khayin.app.features.license.qr

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.khayin.app.BuildConfig
import dev.khayin.app.core.qr.QrCodeGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object QrActivationService {
    private const val TAG = "QrActivationService"
    private const val AUTH_BASE_URL = "https://auth.khayin.net"

    private val gson: Gson = GsonBuilder().create()

    private val standardHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val sseHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for SSE stream
        .build()

    suspend fun createSession(deviceName: String = "Android TV"): Result<QrSessionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$AUTH_BASE_URL/api/auth/qr/session"
            val reqPayload = QrSessionRequest(
                type = "tv",
                deviceName = deviceName.ifBlank { "Android TV (${Build.MODEL})" },
                metadata = mapOf(
                    "platform" to "Android TV",
                    "model" to Build.MODEL,
                    "version" to BuildConfig.VERSION_NAME
                )
            )
            val jsonBody = gson.toJson(reqPayload)
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val rawBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errResp = runCatching { gson.fromJson(rawBody, QrSessionResponse::class.java) }.getOrNull()
                val msg = errResp?.error ?: "Failed to create QR session (HTTP ${response.code})"
                throw IllegalStateException(msg)
            }

            val sessionResp = gson.fromJson(rawBody, QrSessionResponse::class.java)
            if (!sessionResp.success || sessionResp.sessionId.isNullOrBlank()) {
                throw IllegalStateException(sessionResp.error ?: "Invalid session response from auth server")
            }
            sessionResp
        }.onFailure { e ->
            Log.e(TAG, "createSession error: ${e.message}", e)
        }
    }

    suspend fun generateQrBitmap(url: String) = withContext(Dispatchers.Default) {
        runCatching {
            QrCodeGenerator.generate(url, size = 440, margin = 1)
        }.getOrNull()
    }

    suspend fun checkStatus(sessionId: String): Result<QrStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$AUTH_BASE_URL/api/auth/qr/status/$sessionId"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val rawBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errResp = runCatching { gson.fromJson(rawBody, QrStatusResponse::class.java) }.getOrNull()
                val msg = errResp?.error ?: "Status check error (HTTP ${response.code})"
                throw IllegalStateException(msg)
            }

            gson.fromJson(rawBody, QrStatusResponse::class.java)
        }
    }

    /**
     * Starts listening for activation events via SSE stream with a concurrent polling fallback.
     * Completes or cancels when onApproved or onExpired is triggered, or scope is cancelled.
     */
    fun listenForApproval(
        scope: CoroutineScope,
        sessionId: String,
        expiresAt: Long,
        onScanned: () -> Unit,
        onApproved: (QrLicensePayload) -> Unit,
        onExpired: () -> Unit,
        onError: (String) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            var approvedHandled = false

            fun handleApproval(license: QrLicensePayload?) {
                if (approvedHandled || license == null || license.key.isBlank()) return
                approvedHandled = true
                Log.d(TAG, "Session $sessionId approved! Key=${license.key}")
                onApproved(license)
            }

            // 1. SSE Stream Job
            val sseJob = launch {
                try {
                    val streamUrl = "$AUTH_BASE_URL/api/auth/qr/stream/$sessionId"
                    val sseReq = Request.Builder()
                        .url(streamUrl)
                        .addHeader("Accept", "text/event-stream")
                        .addHeader("Cache-Control", "no-cache")
                        .build()

                    val call = sseHttpClient.newCall(sseReq)
                    val sseResponse = call.execute()

                    if (sseResponse.isSuccessful && sseResponse.body != null) {
                        val reader = BufferedReader(InputStreamReader(sseResponse.body!!.byteStream()))
                        var currentEvent: String? = null
                        val currentData = StringBuilder()

                        while (isActive && !approvedHandled) {
                            val line = reader.readLine() ?: break
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) {
                                // Dispatch accumulated event
                                val eventType = currentEvent ?: "message"
                                val dataStr = currentData.toString().trim()
                                currentEvent = null
                                currentData.setLength(0)

                                if (dataStr.isNotEmpty()) {
                                    handleSseEvent(eventType, dataStr, onScanned, ::handleApproval)
                                }
                                continue
                            }

                            if (trimmed.startsWith("event:")) {
                                currentEvent = trimmed.substring(6).trim()
                            } else if (trimmed.startsWith("data:")) {
                                if (currentData.isNotEmpty()) currentData.append("\n")
                                currentData.append(trimmed.substring(5).trim())
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // Normal cancellation
                } catch (e: Exception) {
                    Log.w(TAG, "SSE stream connection issue: ${e.message}. Falling back to polling.")
                }
            }

            // 2. Polling Fallback Job
            val pollingJob = launch {
                while (isActive && !approvedHandled) {
                    delay(2500L)
                    if (approvedHandled) break

                    if (System.currentTimeMillis() >= expiresAt) {
                        Log.d(TAG, "Session $sessionId expired")
                        onExpired()
                        break
                    }

                    checkStatus(sessionId).fold(
                        onSuccess = { resp ->
                            if (resp.status.equals("scanned", ignoreCase = true)) {
                                onScanned()
                            } else if (resp.status.equals("approved", ignoreCase = true)) {
                                val lic = resp.authPayload?.license
                                if (lic != null) {
                                    handleApproval(lic)
                                }
                            }
                        },
                        onFailure = { err ->
                            if (err.message?.contains("expired", ignoreCase = true) == true ||
                                err.message?.contains("404", ignoreCase = true) == true) {
                                onExpired()
                            }
                        }
                    )
                }
            }

            try {
                // Wait while jobs are running
                while (isActive && !approvedHandled) {
                    if (System.currentTimeMillis() >= expiresAt) {
                        onExpired()
                        break
                    }
                    delay(1000L)
                }
            } finally {
                sseJob.cancel()
                pollingJob.cancel()
            }
        }
    }

    private fun handleSseEvent(
        eventType: String,
        data: String,
        onScanned: () -> Unit,
        onApproved: (QrLicensePayload) -> Unit
    ) {
        try {
            if (eventType.equals("scanned", ignoreCase = true)) {
                onScanned()
                return
            }

            val jsonObject = runCatching {
                gson.fromJson(data, QrStatusResponse::class.java)
            }.getOrNull()

            val status = jsonObject?.status ?: if (eventType.equals("approved", ignoreCase = true)) "approved" else ""

            if (status.equals("scanned", ignoreCase = true)) {
                onScanned()
            } else if (status.equals("approved", ignoreCase = true)) {
                val lic = jsonObject?.authPayload?.license
                if (lic != null && lic.key.isNotBlank()) {
                    onApproved(lic)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling SSE event '$eventType': ${e.message}")
        }
    }
}
