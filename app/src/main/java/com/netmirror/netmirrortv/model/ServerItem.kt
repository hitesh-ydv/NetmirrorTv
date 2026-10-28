package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

data class ServerItem(
    @SerializedName("name")
    val name: String,

    @SerializedName(value = "headerCode", alternate = ["header_code", "header", "code", "server"])
    val headerCode: String,

    @SerializedName("active")
    val active: Boolean = false
)
