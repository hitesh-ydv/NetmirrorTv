package com.netmirror.netmirrortv.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages user token storage and expiration in SharedPreferences.
 */
object TokenStore {
    private const val PREFS_NAME = "netmirror_prefs"
    private const val KEY_USER_TOKEN = "usertoken"
    private const val KEY_TOKEN_TIMESTAMP = "usertoken_timestamp"

    // Production expiration duration: 24 hours (24 * 60 * 60 * 1000L)
    const val EXPIRATION_24_HOURS_MS = 24 * 60 * 60 * 1000L

    // Testing expiration duration: 30 seconds (30 * 1000L)
    const val EXPIRATION_TESTING_30_SEC_MS = 30 * 1000L

    // Active expiration set to testing (30 sec). Swap with EXPIRATION_24_HOURS_MS for production.
    private const val CURRENT_EXPIRATION_MS = EXPIRATION_24_HOURS_MS

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserToken(context: Context, token: String) {
        Log.d("UserToken", "saveUserToken: $token")
        prefs(context).edit()
            .putString(KEY_USER_TOKEN, token)
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getUserToken(context: Context): String? {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_USER_TOKEN, null)
        if (token == null) {
            Log.d("UserToken", "getUserToken: null (no token saved)")
            return null
        }
        val savedTime = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        return if (currentTime - savedTime > CURRENT_EXPIRATION_MS) {
            Log.d("UserToken", "getUserToken: expired (clearing token)")
            // Token expired -> clear and return null
            clearToken(context)
            null
        } else {
            Log.d("UserToken", "getUserToken: valid token = '$token'")
            token
        }
    }

    fun clearToken(context: Context) {
        prefs(context).edit()
            .remove(KEY_USER_TOKEN)
            .remove(KEY_TOKEN_TIMESTAMP)
            .apply()
    }

    fun isTokenExpired(context: Context): Boolean {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_USER_TOKEN, null) ?: return true
        val savedTime = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)
        return System.currentTimeMillis() - savedTime > CURRENT_EXPIRATION_MS
    }
}