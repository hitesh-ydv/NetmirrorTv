package com.netmirror.netmirrortv.model

import com.google.gson.annotations.SerializedName

data class TokenResponse(

    @SerializedName("token_hash")
    val tokenHash: String?

)