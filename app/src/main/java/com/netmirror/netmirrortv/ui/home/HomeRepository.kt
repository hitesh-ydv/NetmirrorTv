package com.netmirror.netmirrortv.ui.home

import android.app.Application
import android.util.Log
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.utils.AppSession

class HomeRepository(
    application: Application
) {

    private val preferences = AppPreferences(application)

    suspend fun getHome(page: String = "all"): HomeResponse? {

        val baseUrl = preferences.getBaseUrl()

        if (baseUrl.isEmpty()) return null

        val ott = preferences.getOtt()

        val api = ApiClient.create(baseUrl)

        return api.getHome(
            ott = AppSession.currentOtt(),
            page = page
        )


    }
}