package com.netmirror.netmirrortv.network

import com.netmirror.netmirrortv.utils.Constants
import okhttp3.Interceptor
import okhttp3.Response

class HeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
            .addHeader("Accept", "application/json")

        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", Constants.DEFAULT_USER_AGENT)
        }

        return chain.proceed(builder.build())
    }
}
