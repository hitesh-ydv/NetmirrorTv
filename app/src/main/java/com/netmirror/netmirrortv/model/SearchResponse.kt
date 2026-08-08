package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

data class SearchResponse(

    @SerializedName("ott")
    val ott: String?,

    @SerializedName("imgcdn")
    val imgcdn: String?,

    @SerializedName("detailsimgcdn")
    val detailsImgcdn: String?,

    @SerializedName("img_referer")
    val imgReferer: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("row")
    val row: String?,

    // e.g. "Top Searches" when no query text was sent, or a results heading otherwise.
    @SerializedName("head")
    val head: String?,

    @SerializedName("type")
    val type: Int?,

    @SerializedName("searchResult")
    val searchResult: List<SearchItem> = emptyList(),

    @SerializedName("error")
    val error: String?
)

data class SearchItem(

    @SerializedName("id")
    val id: String,

    // Top Searches results only include "id", no "t" — so this must be nullable.
    @SerializedName("t")
    val title: String? = null

)