package com.netmirror.netmirrortv.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores and retrieves playback progress (currentTime:duration) and
 * last-watched episode ID for series.
 */
object PlaybackProgressStore {
    private const val PREFS_NAME = "netmirror_playback_progress"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProgress(context: Context, id: String, currentSec: Long, durationSec: Long) {
        if (id.isBlank()) return
        prefs(context).edit().putString("progress_$id", "$currentSec:$durationSec").apply()
    }

    fun getProgress(context: Context, id: String): String? {
        if (id.isBlank()) return null
        val cleanId = id.removePrefix("SE_")
        return prefs(context).getString("progress_$id", null)
            ?: prefs(context).getString("progress_$cleanId", null)
            ?: prefs(context).getString("progress_SE_$cleanId", null)
    }

    fun removeProgress(context: Context, id: String) {
        if (id.isBlank()) return
        val cleanId = id.removePrefix("SE_")
        prefs(context).edit()
            .remove("progress_$id")
            .remove("progress_$cleanId")
            .remove("progress_SE_$cleanId")
            .remove("resume_title_$id")
            .remove("resume_title_$cleanId")
            .remove("resume_title_SE_$cleanId")
            .apply()
    }

    fun saveSeriesLastEpisode(context: Context, seriesId: String, episodeId: String) {
        if (seriesId.isBlank() || episodeId.isBlank()) return
        prefs(context).edit().putString("progress_$seriesId", episodeId).apply()
    }

    fun getSeriesLastEpisode(context: Context, seriesId: String): String? {
        if (seriesId.isBlank()) return null
        val cleanId = seriesId.removePrefix("SE_")
        return prefs(context).getString("progress_$seriesId", null)
            ?: prefs(context).getString("progress_$cleanId", null)
            ?: prefs(context).getString("progress_SE_$cleanId", null)
    }

    fun saveSeriesResumeTitle(context: Context, seriesId: String, title: String) {
        if (seriesId.isBlank() || title.isBlank()) return
        prefs(context).edit().putString("resume_title_$seriesId", title).apply()
    }

    fun getSeriesResumeTitle(context: Context, seriesId: String): String? {
        if (seriesId.isBlank()) return null
        val cleanId = seriesId.removePrefix("SE_")
        return prefs(context).getString("resume_title_$seriesId", null)
            ?: prefs(context).getString("resume_title_$cleanId", null)
            ?: prefs(context).getString("resume_title_SE_$cleanId", null)
    }

    fun parseResumePositionMs(progressString: String?): Long {
        if (progressString.isNullOrBlank()) return 0L
        val parts = progressString.split(":")
        if (parts.isEmpty()) return 0L
        val timeValue = parts[0].toDoubleOrNull() ?: return 0L
        val ms = if (timeValue > 100_000.0) timeValue.toLong() else (timeValue * 1000.0).toLong()
        return ms.coerceAtLeast(0L)
    }

    fun getProgressRatio(context: Context, id: String): Float? {
        if (id.isBlank()) return null
        val raw = getProgress(context, id)
        if (raw != null) {
            if (raw.contains(":")) {
                return calculateRatio(raw)
            } else {
                // raw is an episode ID stored for series under progress_$seriesId
                val epProgress = getProgress(context, raw)
                if (epProgress != null && epProgress.contains(":")) {
                    return calculateRatio(epProgress)
                }
            }
        }
        return null
    }

    private fun calculateRatio(progressStr: String): Float? {
        val parts = progressStr.split(":")
        if (parts.size < 2) return null
        val current = parts[0].toDoubleOrNull() ?: return null
        val duration = parts[1].toDoubleOrNull() ?: return null
        if (duration <= 0.0) return null
        val ratio = (current / duration).toFloat().coerceIn(0f, 1f)
        return if (ratio > 0f) ratio else null
    }

    fun getProgressInfo(
        context: Context,
        mainId: String,
        type: String? = null,
        lastEp: String? = null
    ): ProgressInfo? {
        if (mainId.isBlank()) return null
        val isSeries = type.equals("t", ignoreCase = true)
        val cleanId = mainId.removePrefix("SE_")

        val targetId = if (isSeries) {
            getSeriesLastEpisode(context, mainId)?.takeIf { it.isNotBlank() }
                ?: getSeriesLastEpisode(context, cleanId)?.takeIf { it.isNotBlank() }
                ?: lastEp?.takeIf { it.isNotBlank() }
                ?: mainId
        } else {
            mainId
        }

        var raw = getProgress(context, targetId)
        if (raw == null && isSeries && targetId != mainId) {
            raw = getProgress(context, mainId) ?: getProgress(context, cleanId)
        }
        if (raw == null && !isSeries) {
            raw = getProgress(context, cleanId) ?: getProgress(context, "SE_$cleanId")
        }
        if (raw == null || !raw.contains(":")) return null

        val parts = raw.split(":")
        if (parts.size < 2) return null
        val current = parts[0].toDoubleOrNull() ?: return null
        val duration = parts[1].toDoubleOrNull() ?: return null
        if (duration <= 0.0) return null

        val remainingSec = (duration - current).toLong().coerceAtLeast(0L)
        val ratio = (current / duration).toFloat().coerceIn(0f, 1f)

        if (ratio < 0.01f || remainingSec <= 15L) return null

        val remainingText = formatRemainingTime(remainingSec)
        return ProgressInfo(
            ratio = ratio,
            remainingSeconds = remainingSec,
            remainingText = remainingText,
            targetPlayId = targetId
        )
    }

    fun formatRemainingTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m left"
            hours > 0 -> "${hours}h left"
            minutes > 0 -> "${minutes}m left"
            else -> "${seconds}s left"
        }
    }
}

data class ProgressInfo(
    val ratio: Float,
    val remainingSeconds: Long,
    val remainingText: String,
    val targetPlayId: String
)

