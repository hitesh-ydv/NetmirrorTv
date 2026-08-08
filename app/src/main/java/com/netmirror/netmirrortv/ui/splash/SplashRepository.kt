package com.netmirror.netmirrortv.ui.splash

import android.content.Context
import android.util.Base64
import android.util.Log
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.network.BaseUrlProvider
import com.netmirror.netmirrortv.utils.AppSession
import com.netmirror.netmirrortv.utils.Constants

class SplashRepository (
    context: Context
){

    private val preferences = AppPreferences(context)

    // Replace these with your encoded URLs
    private val encodedUrls = Constants.ENCODED_URLS

    suspend fun checkServer(): Result<String> {

        encodedUrls.forEach { encoded ->

            try {

                val serverUrl = decode(encoded)

                val endpoint = decode("L2NoZWNrbmV3dHYucGhw") // /checknewtv.php

                val response = ApiClient
                    .create(serverUrl)
                    .checkServer(endpoint)



                if (response.isSuccessful) {

                    val tokenHash = response.body()?.tokenHash

                    if (!tokenHash.isNullOrEmpty()) {

                        val baseUrl = decode(tokenHash)

                        BaseUrlProvider.setBaseUrl(baseUrl)
                        AppSession.setBaseUrl(baseUrl)
                        preferences.saveBaseUrl(baseUrl)

                        return Result.success(baseUrl)

                    }

                }

            } catch (_: Exception) {
                // Try next server
            }

        }

        return Result.failure(
            Exception("No working server found.")
        )
    }

    private fun decode(value: String): String {

        return String(
            Base64.decode(value, Base64.DEFAULT)
        )

    }
}