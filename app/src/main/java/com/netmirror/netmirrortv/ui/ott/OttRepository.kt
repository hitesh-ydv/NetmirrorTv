package com.netmirror.netmirrortv.ui.ott

import com.netmirror.netmirrortv.network.ApiService

class OttRepository(
    private val apiService: ApiService
) {

    suspend fun getOtts(userToken: String = ""): OttResponse? {

        val response = apiService.getOtts(userToken = userToken)

        return if (response.isSuccessful) {
            response.body()
        } else {
            null
        }
    }
}