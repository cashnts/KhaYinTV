package dev.khayin.app.features.license

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks and enforces Myanmar subtitle (MMSub) quotas:
 * - Plus License: Unlimited access
 * - Standard License: Excluded (no MMSub)
 * - Free Tier: Capped at 2 distinct movies per calendar day
 */
object MyanmarSubLimiter {
    private const val PREFS_NAME = "nuvio_mmsub_quota"
    private const val KEY_DATE = "mmsub_date"
    private const val KEY_MOVIE_IDS = "mmsub_movie_ids"
    const val MAX_DAILY_MOVIES = 2

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @Synchronized
    fun canAccessMyanmarSub(context: Context, contentId: String?): Boolean {
        // Plus license has unlimited access
        if (LicenseRepository.isPlusMember) return true

        // Standard license does NOT include MMSub
        if (LicenseRepository.isLicensed) return false

        // Free tier: allow up to MAX_DAILY_MOVIES per day
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DATE, null)
        val currentSet = if (savedDate == today) {
            prefs.getStringSet(KEY_MOVIE_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        } else {
            mutableSetOf()
        }

        // If this movie has already been counted today, allow it
        if (!contentId.isNullOrBlank() && currentSet.contains(contentId)) {
            return true
        }

        return currentSet.size < MAX_DAILY_MOVIES
    }

    @Synchronized
    fun recordMyanmarSubUsed(context: Context, contentId: String?) {
        if (contentId.isNullOrBlank()) return
        if (LicenseRepository.isLicensed) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DATE, null)
        val currentSet = if (savedDate == today) {
            prefs.getStringSet(KEY_MOVIE_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        } else {
            mutableSetOf()
        }

        if (!currentSet.contains(contentId)) {
            currentSet.add(contentId)
            prefs.edit()
                .putString(KEY_DATE, today)
                .putStringSet(KEY_MOVIE_IDS, currentSet)
                .apply()
        }
    }

    @Synchronized
    fun recordMovieAccess(context: Context, contentId: String?) {
        recordMyanmarSubUsed(context, contentId)
    }

    @Synchronized
    fun getRemainingMoviesToday(context: Context): Int {
        if (LicenseRepository.isPlusMember) return Int.MAX_VALUE
        if (LicenseRepository.isLicensed) return 0

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DATE, null)
        val count = if (savedDate == today) {
            prefs.getStringSet(KEY_MOVIE_IDS, emptySet())?.size ?: 0
        } else {
            0
        }
        return (MAX_DAILY_MOVIES - count).coerceAtLeast(0)
    }
}
