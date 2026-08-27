package com.netmirror.netmirrortv.ui.home

import android.app.Application
import android.util.Log
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.AppSession
import com.netmirror.netmirrortv.utils.RecentPlayStore
import com.netmirror.netmirrortv.utils.WatchlistStore

class HomeRepository(
    private val application: Application
) {

    private val preferences = AppPreferences(application)

    suspend fun getHome(page: String = "all"): HomeResponse? {

        val baseUrl = AppSession.currentBaseUrl().ifBlank { preferences.getBaseUrl() }

        if (baseUrl.isEmpty()) return null

        val ott = AppSession.currentOtt().ifBlank { preferences.getOtt() }
        val recentPlay = RecentPlayStore.getRecentPlay(application, ott)
        val watchlist = WatchlistStore.getWatchlist(application, ott)
        val userToken = TokenStore.getUserToken(application) ?: ""

        Log.d("HomeRepository", "getHome -> Ott: '$ott', Page: '$page', Recentplay: '$recentPlay', Watchlist: '$watchlist', Usertoken: '$userToken'")

        val api = ApiClient.create(baseUrl)

        return api.getHome(
            ott = ott,
            page = page,
            recentPlay = recentPlay,
            watchlist = watchlist,
            userToken = userToken
        )
    }

    suspend fun deleteRecentPlay(id: String) {
        val baseUrl = AppSession.currentBaseUrl().ifBlank { preferences.getBaseUrl() }
        if (baseUrl.isEmpty()) return

        val ott = AppSession.currentOtt().ifBlank { preferences.getOtt() }
        val storedRecent = RecentPlayStore.getRecentPlay(application, ott)

        if (ott.isBlank() && storedRecent.isBlank()) {
            Log.d("HomeRepository", "deleteRecentPlay -> not running (no ott and storedRecent)")
            return
        }

        val api = ApiClient.create(baseUrl)

        runCatching {
            api.deleteRecentPlay(
                recentPlay = storedRecent,
                ott = ott,
                deleteId = id
            )
        }.onSuccess { response ->
            if (response.isSuccessful) {
                val data = response.body()
                Log.d("HomeRepository", "deleteRecentPlay API response for id $id: key=${data?.key}, value=${data?.value}")
                if (data != null) {
                    val key = data.key?.takeIf { it.isNotBlank() } ?: "${ott}_recentplay"
                    RecentPlayStore.saveRecentPlay(application, key, data.value.orEmpty())
                }
            } else {
                Log.w("HomeRepository", "deleteRecentPlay error code ${response.code()} for id $id")
            }
        }.onFailure { err ->
            Log.e("HomeRepository", "deleteRecentPlay request failed for id $id: ${err.message}", err)
        }
    }

    suspend fun deleteWatchlist(id: String) {
        val baseUrl = AppSession.currentBaseUrl().ifBlank { preferences.getBaseUrl() }
        if (baseUrl.isEmpty()) return

        val ott = AppSession.currentOtt().ifBlank { preferences.getOtt() }
        val storedWatchlist = WatchlistStore.getWatchlist(application, ott)

        // Optimistically remove locally
        WatchlistStore.removeLocally(application, ott, id)

        if (ott.isBlank() && storedWatchlist.isBlank()) {
            Log.d("HomeRepository", "deleteWatchlist -> not running (no ott and storedWatchlist)")
            return
        }

        val api = ApiClient.create(baseUrl)

        runCatching {
            api.deleteWatchlist(
                watchlist = storedWatchlist,
                ott = ott,
                deleteId = id
            )
        }.onSuccess { response ->
            if (response.isSuccessful) {
                val data = response.body()
                Log.d("HomeRepository", "deleteWatchlist API response for id $id: key=${data?.key}, value=${data?.value}")
                if (data != null) {
                    val key = data.key?.takeIf { it.isNotBlank() } ?: if (ott.isNotBlank()) "${ott}_watchlist" else "watchlist"
                    WatchlistStore.saveWatchlist(application, key, data.value.orEmpty())
                }
            } else {
                Log.w("HomeRepository", "deleteWatchlist error code ${response.code()} for id $id")
            }
        }.onFailure { err ->
            Log.e("HomeRepository", "deleteWatchlist request failed for id $id: ${err.message}", err)
        }
    }
}