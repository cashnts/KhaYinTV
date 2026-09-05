package dev.khayin.app.core.analytics

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

data class DeviceInfo(
    val deviceType: String,      // "tv", "tablet", "mobile"
    val platform: String,        // "Android TV", "Fire TV", "Android Tablet", "Android"
    val osName: String = "Android",
    val osVersion: String = Build.VERSION.RELEASE ?: "unknown",
    val model: String = Build.MODEL ?: "unknown",
    val brand: String = Build.BRAND ?: "unknown",
    val manufacturer: String = Build.MANUFACTURER ?: "unknown"
)

object DeviceDetector {
    @Volatile
    private var cachedInfo: DeviceInfo? = null

    fun init(context: Context) {
        if (cachedInfo == null) {
            getDeviceInfo(context)
        }
    }

    fun getDeviceInfo(context: Context? = null): DeviceInfo {
        cachedInfo?.let { return it }

        val isAmazon = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
                Build.MODEL.contains("AFT", ignoreCase = true)

        var isTv = false
        var isTablet = false

        if (context != null) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvUi = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val pm = context.packageManager
            val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            val hasTvFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            isTv = isTvUi || hasLeanback || hasTvFeature || isAmazon

            val screenLayout = context.resources.configuration.screenLayout
            val isLarge = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
            isTablet = !isTv && isLarge
        } else {
            isTv = isAmazon || Build.MODEL.contains("TV", ignoreCase = true) || Build.MODEL.contains("Box", ignoreCase = true)
        }

        val deviceType = when {
            isTv -> "tv"
            isTablet -> "tablet"
            else -> "mobile"
        }

        val platform = when {
            isAmazon -> "Fire TV"
            isTv -> "Android TV"
            isTablet -> "Android Tablet"
            else -> "Android"
        }

        val info = DeviceInfo(
            deviceType = deviceType,
            platform = platform,
            osName = "Android",
            osVersion = Build.VERSION.RELEASE ?: "unknown",
            model = Build.MODEL ?: "unknown",
            brand = Build.BRAND ?: "unknown",
            manufacturer = Build.MANUFACTURER ?: "unknown"
        )
        cachedInfo = info
        return info
    }
}
