package com.netmirror.netmirrortv.network

object BaseUrlProvider {

    private var baseUrl = ""

    fun setBaseUrl(url: String) {
        baseUrl = url
    }

    fun getBaseUrl(): String {
        return baseUrl
    }

}