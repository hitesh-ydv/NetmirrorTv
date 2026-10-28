package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for GET /newtv/player.php?id={id}
 *
 * Example payload:
 * {
 *     "status": "otp",
 *     "ott": "nf",
 *     "usertoken": "none",
 *     "video_link": "https://tv.imgcdn.kim/newtv/hls/nf/70301870.m3u8",
 *     "referer": "https://net52.cc",
 *     "title": "Vikings",
 *     "ep": "2013",
 *     "ep_title": "MOVIE",
 *     "t": "https://whos.amung.us/widget/iyify2030nf.png"
 * }
 */
data class PlayerResponse(
    @SerializedName("status")
    val status: String? = null,

    @SerializedName("ott")
    val ott: String? = null,

    @SerializedName("usertoken")
    val usertoken: String? = null,

    @SerializedName("video_link")
    val videoLink: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("stream_url")
    val streamUrl: String? = null,

    @SerializedName("link")
    val link: String? = null,

    @SerializedName("video_url")
    val videoUrl: String? = null,

    @SerializedName("file")
    val file: String? = null,

    @SerializedName("referer")
    val referer: String? = null,

    @SerializedName("img_referer")
    val imgReferer: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("title_name")
    val titleName: String? = null,

    @SerializedName("ep")
    val ep: String? = null,

    @SerializedName("ep_title")
    val epTitle: String? = null,

    @SerializedName("poster")
    val poster: String? = null,

    @SerializedName("t")
    val t: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName(value = "server", alternate = ["active_server", "activeServer", "active"])
    val server: String? = null,

    @SerializedName(value = "servers", alternate = ["server_list", "serverList"])
    val servers: List<ServerItem>? = null
) {
    fun getEffectiveServers(fallbackServer: String = "S1"): List<ServerItem> {
        val rawServers = servers ?: emptyList()
        if (rawServers.isEmpty()) return emptyList()

        val activeCode = server?.takeIf { it.isNotBlank() }
            ?: rawServers.firstOrNull { it.active }?.headerCode
            ?: fallbackServer

        return rawServers.map { item ->
            item.copy(active = item.headerCode.equals(activeCode, ignoreCase = true))
        }
    }
    val playUrl: String?
        get() = videoLink?.takeIf { it.isNotBlank() }
            ?: url?.takeIf { it.isNotBlank() }
            ?: streamUrl?.takeIf { it.isNotBlank() }
            ?: link?.takeIf { it.isNotBlank() }
            ?: videoUrl?.takeIf { it.isNotBlank() }
            ?: file?.takeIf { it.isNotBlank() }

    val dynamicReferer: String?
        get() = referer?.takeIf { it.isNotBlank() }
            ?: imgReferer?.takeIf { it.isNotBlank() }

    val displayTitle: String?
        get() {
            val mainTitle = title?.takeIf { it.isNotBlank() } ?: titleName
            val episodeTitle = epTitle?.takeIf { it.isNotBlank() && !it.equals("MOVIE", ignoreCase = true) }
            return when {
                mainTitle != null && episodeTitle != null -> "$mainTitle - $episodeTitle"
                mainTitle != null -> mainTitle
                else -> epTitle
            }
        }
}
