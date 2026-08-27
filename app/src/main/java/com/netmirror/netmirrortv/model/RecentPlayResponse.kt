package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for GET /newtv/recentplay.php
 *
 * Example JSON response:
 * {
 *   "key": "nf_recentplay",
 *   "value": "xyz...",
 *   "status": "success"
 * }
 */
data class RecentPlayResponse(
    @SerializedName("key")
    val key: String? = null,

    @SerializedName("value")
    val value: String? = null,

    @SerializedName("status")
    val status: String? = null
)
