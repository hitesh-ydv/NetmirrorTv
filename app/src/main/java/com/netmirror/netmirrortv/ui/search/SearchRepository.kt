package com.netmirror.netmirrortv.ui.search

import android.app.Application
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.model.SearchResponse

class SearchRepository(

    application: Application

) {

    private val preferences = AppPreferences(application)

    suspend fun search(
        text: String
    ): SearchResponse? {

        val baseUrl = preferences.getBaseUrl()
        val ott = preferences.getOtt()

        if (baseUrl.isEmpty() || ott.isEmpty()) return null

        val api = ApiClient.create(baseUrl)

        // Blank means "no query yet" (initial load or cleared field). Pass null
        // so Retrofit omits the "s" param entirely -> backend returns Top Searches.
        val queryText: String? = text.trim().ifBlank { null }

        return api.search(ott = ott, text = queryText)
    }

}