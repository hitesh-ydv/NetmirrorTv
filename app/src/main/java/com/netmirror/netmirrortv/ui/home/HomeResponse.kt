package com.netmirror.netmirrortv.ui.home

import com.google.gson.annotations.SerializedName

data class HomeResponse(
    val status: String = "",
    val slider: List<SliderItem> = emptyList(),
    val post: List<HomePost> = emptyList(),
    @SerializedName("img_referer") val imgReferer: String? = null,
    @SerializedName("imgcdn") val imageCdn: String? = null,
    @SerializedName("imgcdn_v") val imageCdnVertical: String? = null,
    @SerializedName("imgcdn_h") val imageCdnHorizontal: String? = null,
)

data class HomePost(
    val ids: String = "",
    val cate: String = "",
    val row: String = "h",
)

data class SliderItem(
    val id: String,
    val img: String,
    val desc: String,
    val ua: String,
    val namelogo: String,
    val title: String,
)