package com.netmirror.netmirrortv.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages storing and retrieving recent play tokens per OTT provider.
 */
object RecentPlayStore {
    private const val PREFS_NAME = "netmirror_recentplay_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecentPlay(context: Context, ott: String): String {
        val effectiveOtt = ott.ifBlank { AppSession.currentOtt() }
        val key = if (effectiveOtt.isNotBlank()) "${effectiveOtt}_recentplay" else "recentplay"
        return prefs(context).getString(key, "") ?: ""
    }

    fun saveRecentPlay(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }
}
