package com.netmirror.netmirrortv.ui.ott

import com.netmirror.netmirrortv.network.ApiService

class OttRepository(
    private val apiService: ApiService
) {

    suspend fun getOtts(): OttResponse? {

        val response = apiService.getOtts()

        return if (response.isSuccessful) {
            response.body()
        } else {
            null
        }
    }
}