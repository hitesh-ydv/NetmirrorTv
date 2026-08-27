package com.netmirror.netmirrortv.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages storing and retrieving watchlist tokens per OTT provider.
 */
object WatchlistStore {
    private const val PREFS_NAME = "netmirror_watchlist_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWatchlist(context: Context, ott: String): String {
        val effectiveOtt = ott.ifBlank { AppSession.currentOtt() }
        val key = if (effectiveOtt.isNotBlank()) "${effectiveOtt}_watchlist" else "watchlist"
        return prefs(context).getString(key, "") ?: ""
    }

    fun saveWatchlist(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun removeLocally(context: Context, ott: String, videoId: String) {
        val effectiveOtt = ott.ifBlank { AppSession.currentOtt() }
        val current = getWatchlist(context, effectiveOtt)
        val cleanId = videoId.removePrefix("SE_")
        val updated = current.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != videoId && it != cleanId && it != "SE_$cleanId" && it.removePrefix("SE_") != cleanId }
            .joinToString(",")
        val key = if (effectiveOtt.isNotBlank()) "${effectiveOtt}_watchlist" else "watchlist"
        saveWatchlist(context, key, updated)
    }

    fun addLocally(context: Context, ott: String, videoId: String) {
        val effectiveOtt = ott.ifBlank { AppSession.currentOtt() }
        val current = getWatchlist(context, effectiveOtt)
        if (isInWatchlist(context, effectiveOtt, videoId)) return
        val updated = if (current.isBlank()) videoId else "$current,$videoId"
        val key = if (effectiveOtt.isNotBlank()) "${effectiveOtt}_watchlist" else "watchlist"
        saveWatchlist(context, key, updated)
    }

    fun isInWatchlist(context: Context, ott: String, videoId: String): Boolean {
        val effectiveOtt = ott.ifBlank { AppSession.currentOtt() }
        val stored = getWatchlist(context, effectiveOtt)
        if (stored.isBlank() || videoId.isBlank()) return false
        val cleanId = videoId.removePrefix("SE_")
        return stored.split(",").map { it.trim().removePrefix("SE_") }.contains(cleanId)
    }
}

