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
    private const val KEY_SUBTITLE_SIZE = "subtitle_font_size"
    private const val KEY_SUBTITLE_COLOR = "subtitle_color"
    private const val KEY_SUBTITLE_BG_COLOR = "subtitle_bg_color"
    private const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity"
    private const val KEY_SUBTITLE_SPACING = "subtitle_bottom_spacing"

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

    fun savePreferredSubtitleSize(context: Context, sizeSp: Float) {
        prefs(context).edit().putFloat(KEY_SUBTITLE_SIZE, sizeSp).apply()
    }

    fun getPreferredSubtitleSize(context: Context): Float {
        return prefs(context).getFloat(KEY_SUBTITLE_SIZE, 22f)
    }

    fun savePreferredSubtitleColor(context: Context, colorName: String) {
        prefs(context).edit().putString(KEY_SUBTITLE_COLOR, colorName).apply()
    }

    fun getPreferredSubtitleColor(context: Context): String? {
        return prefs(context).getString(KEY_SUBTITLE_COLOR, "White")
    }

    fun savePreferredSubtitleBgColor(context: Context, colorName: String) {
        prefs(context).edit().putString(KEY_SUBTITLE_BG_COLOR, colorName).apply()
    }

    fun getPreferredSubtitleBgColor(context: Context): String {
        return prefs(context).getString(KEY_SUBTITLE_BG_COLOR, "Black") ?: "Black"
    }

    fun savePreferredSubtitleBgOpacity(context: Context, opacity: Float) {
        prefs(context).edit().putFloat(KEY_SUBTITLE_BG_OPACITY, opacity).apply()
    }

    fun getPreferredSubtitleBgOpacity(context: Context): Float {
        return prefs(context).getFloat(KEY_SUBTITLE_BG_OPACITY, 1.0f)
    }

    fun savePreferredSubtitleSpacing(context: Context, spacingFraction: Float) {
        prefs(context).edit().putFloat(KEY_SUBTITLE_SPACING, spacingFraction).apply()
    }

    fun getPreferredSubtitleSpacing(context: Context): Float {
        val saved = prefs(context).getFloat(KEY_SUBTITLE_SPACING, 40f)
        val validSpacings = floatArrayOf(0f, 20f, 40f, 60f, 80f, 100f)
        return if (validSpacings.any { kotlin.math.abs(it - saved) < 0.5f }) saved else 40f
    }
}
