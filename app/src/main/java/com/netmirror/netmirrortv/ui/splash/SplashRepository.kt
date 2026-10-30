package com.netmirror.netmirrortv.ui.splash

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.compose.ui.unit.Constraints
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

                val endpoint = decode("L2NoZWNrdHYtdmVyLnBocA==") // /checktv-ver.php?ver=3.3

                val response = ApiClient
                    .create(serverUrl)
                    .checkServer("$endpoint?ver=${Constants.APP_VERSION}")

                Log.d("VERSION", "Main url -----  $endpoint?ver=${Constants.APP_VERSION}")


                if (response.isSuccessful) {

                    val body = response.body()
                    val tokenHash = body?.tokenHash
                    val apiVersion = body?.apiVersion

                    if (!apiVersion.isNullOrBlank()) {
                        AppSession.setApiVersion(apiVersion)
                        preferences.saveApiVersion(apiVersion)
                    }
                    AppSession.setAppVersion(Constants.APP_VERSION)
                    preferences.saveAppVersion(Constants.APP_VERSION)
                    AppSession.resetUpdateDialogState()

                    if (!tokenHash.isNullOrEmpty()) {

                        val baseUrl = decode(tokenHash)

                        Log.d("BASEURLS", "Main url -----  $baseUrl")

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