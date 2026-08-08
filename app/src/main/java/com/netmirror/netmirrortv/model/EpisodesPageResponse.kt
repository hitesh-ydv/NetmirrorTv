package com.netmirror.netmirrortv.model

import com.yourapp.data.model.Episode

data class EpisodesPageResponse(
    val status: String?,
    val episodes: List<Episode?>?,
    val nextPageShow: Int?,
    val nextPage: Int?,
    val nextPageSeason: String?,
    val error: String?
)