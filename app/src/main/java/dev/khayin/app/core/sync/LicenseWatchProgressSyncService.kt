package dev.khayin.app.core.sync

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.khayin.app.BuildConfig
import dev.khayin.app.core.profile.ProfileManager
import dev.khayin.app.core.security.KhaYinSecurityBridge
import dev.khayin.app.data.local.WatchProgressPreferences
import dev.khayin.app.data.local.WatchedItemsPreferences
import dev.khayin.app.domain.model.WatchProgress
import dev.khayin.app.domain.model.WatchedItem
import dev.khayin.app.features.license.LicenseRepository
import dev.khayin.app.features.license.LicenseState
import dev.khayin.app.features.license.LicenseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseWatchProgressSyncService @Inject constructor(
    private val watchProgressPreferences: WatchProgressPreferences,
    private val watchedItemsPreferences: WatchedItemsPreferences,
    private val profileManager: ProfileManager,
) {
    companion object {
        private const val TAG = "LicenseProgressSync"
        private const val PUSH_DEBOUNCE_MS = 2_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().create()
    private val syncMutex = Mutex()
    private var debouncedPushJob: Job? = null

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "KhaYin")
                .build()
            chain.proceed(request)
        }
        .build()

    fun getActiveLicenseKey(): String? {
        val keyFromState = (LicenseRepository.state.value as? LicenseState.Active)?.info?.key
        if (!keyFromState.isNullOrBlank()) return keyFromState
        val storedKey = LicenseStorage.loadLastKnownKey()
        return if (!storedKey.isNullOrBlank()) storedKey else null
    }

    private data class LicenseSyncPayload(
        val version: Int = 1,
        val updatedAt: Long = System.currentTimeMillis(),
        val watchProgress: List<WatchProgress> = emptyList(),
        val watchedItems: List<WatchedItem> = emptyList(),
    )

    private fun supabaseRestUrl(): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$base/rest/v1"
    }

    suspend fun pullRemote(profileId: Int = profileManager.activeProfileId.value): Result<Unit> = withContext(Dispatchers.IO) {
        val key = getActiveLicenseKey() ?: return@withContext Result.failure(IllegalStateException("No active license"))
        syncMutex.withLock {
            try {
                val restUrl = supabaseRestUrl()
                val apiKey = BuildConfig.SUPABASE_ANON_KEY
                val queryUrl = "$restUrl/license_keys?key=eq.$key&select=notes"

                val secHeaders = KhaYinSecurityBridge.buildSecureHeaders("GET", queryUrl, "")
                val reqBuilder = Request.Builder()
                    .url(queryUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                secHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val body = response.body.string()

                if (!response.isSuccessful || body.isBlank() || body == "[]") {
                    return@withContext Result.success(Unit)
                }

                val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                val rows = runCatching { gson.fromJson<List<Map<String, Any?>>>(body, type) }.getOrNull()
                val notesRaw = rows?.firstOrNull()?.get("notes") as? String

                if (notesRaw.isNullOrBlank()) {
                    return@withContext Result.success(Unit)
                }

                val payload = runCatching {
                    gson.fromJson(notesRaw, LicenseSyncPayload::class.java)
                }.getOrNull()

                if (payload != null) {
                    if (payload.watchProgress.isNotEmpty()) {
                        val remoteMap = payload.watchProgress.associateBy { progressKey(it) }
                        watchProgressPreferences.mergeRemoteEntries(
                            remoteEntries = remoteMap,
                            profileId = profileId,
                            removeMissingRemoteEntries = false
                        )
                        Log.d(TAG, "Successfully pulled ${payload.watchProgress.size} watch progress items for license $key")
                    }
                    if (payload.watchedItems.isNotEmpty()) {
                        watchedItemsPreferences.mergeRemoteItems(
                            remoteItems = payload.watchedItems,
                            profileId = profileId
                        )
                        Log.d(TAG, "Successfully pulled ${payload.watchedItems.size} watched items for license $key")
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Error pulling watch progress for license: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun pushRemote(profileId: Int = profileManager.activeProfileId.value): Result<Unit> = withContext(Dispatchers.IO) {
        val key = getActiveLicenseKey() ?: return@withContext Result.failure(IllegalStateException("No active license"))
        syncMutex.withLock {
            try {
                val restUrl = supabaseRestUrl()
                val apiKey = BuildConfig.SUPABASE_ANON_KEY
                val patchUrl = "$restUrl/license_keys?key=eq.$key"

                val localProgress = watchProgressPreferences.getAllRawEntries(profileId).values.toList()
                val localWatched = watchedItemsPreferences.getAllItems(profileId)

                val payload = LicenseSyncPayload(
                    version = 1,
                    updatedAt = System.currentTimeMillis(),
                    watchProgress = localProgress,
                    watchedItems = localWatched
                )
                val notesPayload = gson.toJson(payload)
                val patchBody = gson.toJson(mapOf("notes" to notesPayload))

                val secHeaders = KhaYinSecurityBridge.buildSecureHeaders("PATCH", patchUrl, patchBody)
                val reqBuilder = Request.Builder()
                    .url(patchUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(patchBody.toRequestBody("application/json".toMediaType()))
                secHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                if (response.isSuccessful) {
                    val now = System.currentTimeMillis()
                    watchProgressPreferences.setLastSuccessfulPushMs(now, profileId)
                    watchedItemsPreferences.setLastSuccessfulPushMs(now, profileId)
                    Log.d(TAG, "Successfully pushed ${localProgress.size} progress entries & ${localWatched.size} watched items to license $key")
                    Result.success(Unit)
                } else {
                    val err = "HTTP ${response.code}: ${response.body.string()}"
                    Log.w(TAG, "Failed to push watch progress: $err")
                    Result.failure(Exception(err))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push watch progress: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    fun scheduleDebouncedPush(profileId: Int = profileManager.activeProfileId.value) {
        debouncedPushJob?.cancel()
        debouncedPushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            pushRemote(profileId)
        }
    }

    suspend fun sync(profileId: Int = profileManager.activeProfileId.value) {
        pullRemote(profileId)
        pushRemote(profileId)
    }

    private fun progressKey(progress: WatchProgress): String {
        return if (progress.season != null && progress.episode != null) {
            "${progress.contentId}:${progress.season}:${progress.episode}"
        } else {
            progress.contentId
        }
    }
}
