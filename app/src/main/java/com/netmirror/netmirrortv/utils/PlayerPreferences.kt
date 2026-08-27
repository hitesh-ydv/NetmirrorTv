package com.netmirror.netmirrortv.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores and restores user's preferred player settings (Audio, Subtitle, Quality).
 */
object PlayerPreferences {
    private const val PREFS_NAME = "netmirror_player_prefs"
    private const val KEY_PREFERRED_AUDIO = "preferred_audio"
    private const val KEY_PREFERRED_SUBTITLE = "preferred_subtitle"
    private const val KEY_PREFERRED_QUALITY = "preferred_quality"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePreferredAudio(context: Context, audio: String) {
        prefs(context).edit().putString(KEY_PREFERRED_AUDIO, audio).apply()
    }

    fun getPreferredAudio(context: Context): String? {
        return prefs(context).getString(KEY_PREFERRED_AUDIO, null)
    }

    fun savePreferredSubtitle(context: Context, subtitle: String) {
        prefs(context).edit().putString(KEY_PREFERRED_SUBTITLE, subtitle).apply()
    }

    fun getPreferredSubtitle(context: Context): String? {
        return prefs(context).getString(KEY_PREFERRED_SUBTITLE, null)
    }

    fun savePreferredQuality(context: Context, quality: String) {
        prefs(context).edit().putString(KEY_PREFERRED_QUALITY, quality).apply()
    }

    fun getPreferredQuality(context: Context): String? {
        return prefs(context).getString(KEY_PREFERRED_QUALITY, null)
    }
}
