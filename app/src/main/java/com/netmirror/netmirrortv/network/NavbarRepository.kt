package com.netmirror.netmirrortv.network

import android.content.Context
import android.util.Log
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.model.NavbarResponse

class NavbarRepository(
    context: Context
) {

    private val preferences = AppPreferences(context)

    suspend fun getNavbar(): NavbarResponse {

        val baseUrl = preferences.getBaseUrl()

        val ott = preferences.getOtt()

        val api = ApiClient.create(baseUrl)

        val navbar = api.getNavbar(ott)


        return api.getNavbar(ott)

    }

}