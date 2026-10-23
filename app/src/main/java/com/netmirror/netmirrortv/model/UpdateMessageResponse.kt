package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

data class UpdateMessageResponse(
    @SerializedName("img")
    val img: UpdateImageInfo? = null,

    @SerializedName("btn_ok")
    val btnOk: String? = null
)

data class UpdateImageInfo(
    @SerializedName("img")
    val url: String? = null,

    @SerializedName("maxh")
    val maxh: Int? = null,

    @SerializedName("maxw")
    val maxw: Int? = null,

    @SerializedName("maxh2")
    val maxh2: Int? = null
)
