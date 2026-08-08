package com.netmirror.netmirrortv.model


data class OtpResponse(
    val status: String? = null,
    val error_msg: String? = null,
    val usertoken: String? = null,
    val pub_msg: String? = null,
    val pub_msg_color: String? = null,
    val pub_msg_f_size: Int? = null,
    val pub_msg_img: PubMsgImg? = null
)

data class PubMsgImg(
    val img: String? = null,
    val maxw: Int? = null,
    val maxh: Int? = null,
    val maxh2: Int? = null
)