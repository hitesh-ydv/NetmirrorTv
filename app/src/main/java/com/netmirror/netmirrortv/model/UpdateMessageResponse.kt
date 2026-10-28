package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

data class UpdateMessageResponse(
    @SerializedName("img")
    val img: UpdateImageInfo? = null,

    @SerializedName("btn_ok")
    val btnOk: String? = null,

    @SerializedName("apk_url")
    val apkUrl: String? = null,

    @SerializedName("apk_link")
    val apkLink: String? = null,

    @SerializedName("apk")
    val apk: String? = null,

    @SerializedName("download_url")
    val downloadUrl: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("link")
    val link: String? = null
)

data class UpdateImageInfo(
    @SerializedName("img")
    val url: String? = null,

    @SerializedName("maxh")
    val maxh: Any? = null,

    @SerializedName("maxw")
    val maxw: Any? = null,

    @SerializedName("maxh2")
    val maxh2: Any? = null
)
