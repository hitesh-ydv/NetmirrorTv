package com.netmirror.netmirrortv.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "netmirror_prefs")

class AppPreferences(
    private val context: Context
) {

    companion object {

        private val BASE_URL = stringPreferencesKey("base_url")

        private val SELECTED_OTT = stringPreferencesKey("selected_ott")

        private val API_VERSION = stringPreferencesKey("api_version")

        private val APP_VERSION = stringPreferencesKey("app_version")

    }


    suspend fun saveBaseUrl(url: String) {

        context.dataStore.edit {

            it[BASE_URL] = url

        }

    }

    suspend fun getBaseUrl(): String {

        return context.dataStore.data.first()[BASE_URL] ?: ""

    }

    suspend fun saveOtt(ott: String) {

        context.dataStore.edit {

            it[SELECTED_OTT] = ott

        }

    }

    suspend fun getOtt(): String {

        return context.dataStore.data.first()[SELECTED_OTT] ?: "nf"

    }

    suspend fun saveApiVersion(version: String) {

        context.dataStore.edit {

            it[API_VERSION] = version

        }

    }

    suspend fun getApiVersion(): String {

        return context.dataStore.data.first()[API_VERSION] ?: ""

    }

    suspend fun saveAppVersion(version: String) {

        context.dataStore.edit {

            it[APP_VERSION] = version

        }

    }

    suspend fun getAppVersion(): String {

        return context.dataStore.data.first()[APP_VERSION] ?: ""

    }

}