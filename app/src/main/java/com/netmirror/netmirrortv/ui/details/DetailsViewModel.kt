package com.netmirror.netmirrortv.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.yourapp.data.model.TitleDetailsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Success(val data: TitleDetailsResponse) : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
}

/**
 * UI state for the Episodes tab's season switching + "load more" pagination,
 * mirroring the `ep` / `nextPage` / `nextPageSeason` / `hasMore` state from
 * the React reference (seasonData / loadMoreEpisodes).
 */
data class EpisodesUiState(
    val seasonId: String? = null,
    val episodes: List<com.yourapp.data.model.Episode> = emptyList(),
    val isLoadingSeason: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPage: Int? = null,
    val nextPageSeason: String? = null,
    val error: String? = null
)

class DetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private val _state = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val state: StateFlow<DetailsUiState> = _state

    // Avoid re-fetching if the same id is re-loaded (e.g. on config change)
    private var loadedId: String? = null

    private val _episodesState = MutableStateFlow(EpisodesUiState())
    val episodesState: StateFlow<EpisodesUiState> = _episodesState

    fun load(id: String, force: Boolean = false) {
        if (!force && id == loadedId && _state.value is DetailsUiState.Success) return

        viewModelScope.launch {
            _state.value = DetailsUiState.Loading

            val baseUrl = com.netmirror.netmirrortv.utils.AppSession.currentBaseUrl().ifBlank { prefs.getBaseUrl() }
            val ott = com.netmirror.netmirrortv.utils.AppSession.currentOtt().ifBlank { prefs.getOtt() }

            if (baseUrl.isBlank()) {
                _state.value = DetailsUiState.Error("Server not set up yet")
                return@launch
            }

            val api = ApiClient.create(baseUrl)
            val userToken = com.netmirror.netmirrortv.util.TokenStore.getUserToken(getApplication()) ?: ""
            val lastEp = com.netmirror.netmirrortv.utils.PlaybackProgressStore.getSeriesLastEpisode(getApplication(), id) ?: ""
            val recentPlay = com.netmirror.netmirrortv.utils.RecentPlayStore.getRecentPlay(getApplication(), ott)
            android.util.Log.d("DetailsViewModel", "DetailsViewModel.load -> id: '$id', ott: '$ott', Recentplay: '$recentPlay', Lastep: '$lastEp', Usertoken: '$userToken'")
            runCatching {
                api.getDetails(
                    id = id,
                    ott = ott,
                    recentPlay = recentPlay,
                    lastEp = lastEp,
                    userToken = userToken
                )
            }
                .onSuccess { response ->
                    _state.value = if (response.status == "ok") {
                        loadedId = id
                        DetailsUiState.Success(response)
                    } else {
                        DetailsUiState.Error(response.error ?: "Something went wrong")
                    }
                }
                .onFailure { e ->
                    _state.value = DetailsUiState.Error(e.message ?: "Network error")
                }
        }
    }

    fun seedEpisodesFromDetails(data: TitleDetailsResponse) {
        val selectedSeason = data.season.orEmpty().firstOrNull { it.selected == true }
            ?: data.season.orEmpty().firstOrNull()

        _episodesState.value = EpisodesUiState(
            seasonId = selectedSeason?.id,
            episodes = data.episodes.orEmpty().filterNotNull(),
            hasMore = data.nextPageShow == 1 && data.nextPage != null,
            nextPage = data.nextPage,
            nextPageSeason = data.nextPageSeason
        )
    }

    /** Call when the user taps a different season chip. */
    fun selectSeason(seasonId: String) {
        if (_episodesState.value.seasonId == seasonId) return

        _episodesState.value = _episodesState.value.copy(
            seasonId = seasonId,
            episodes = emptyList(),
            isLoadingSeason = true,
            isLoadingMore = false,
            hasMore = false,
            nextPage = null,
            nextPageSeason = null,
            error = null
        )

        viewModelScope.launch {
            val baseUrl = com.netmirror.netmirrortv.utils.AppSession.currentBaseUrl().ifBlank { prefs.getBaseUrl() }
            val ott = com.netmirror.netmirrortv.utils.AppSession.currentOtt().ifBlank { prefs.getOtt() }

            if (baseUrl.isBlank()) {
                _episodesState.value = _episodesState.value.copy(
                    isLoadingSeason = false,
                    error = "Server not set up yet"
                )
                return@launch
            }

            val api = ApiClient.create(baseUrl)
            runCatching { api.getEpisodes(id = seasonId, ott = ott) }
                .onSuccess { res ->
                    _episodesState.value = _episodesState.value.copy(
                        episodes = res.episodes.orEmpty().filterNotNull(),
                        isLoadingSeason = false,
                        hasMore = res.nextPageShow == 1 && res.nextPage != null,
                        nextPage = res.nextPage,
                        nextPageSeason = res.nextPageSeason
                    )
                }
                .onFailure { e ->
                    _episodesState.value = _episodesState.value.copy(
                        isLoadingSeason = false,
                        error = e.message ?: "Failed to load episodes"
                    )
                }
        }
    }

    /** Call when the user scrolls near the bottom of the episode list / taps "Load more". */
    fun loadMoreEpisodes() {
        val current = _episodesState.value
        val seasonBreadcrumb = current.nextPageSeason
        val page = current.nextPage
        if (!current.hasMore || current.isLoadingMore || seasonBreadcrumb == null || page == null) return

        _episodesState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            val baseUrl = com.netmirror.netmirrortv.utils.AppSession.currentBaseUrl().ifBlank { prefs.getBaseUrl() }
            val ott = com.netmirror.netmirrortv.utils.AppSession.currentOtt().ifBlank { prefs.getOtt() }

            if (baseUrl.isBlank()) {
                _episodesState.value = _episodesState.value.copy(
                    isLoadingMore = false,
                    error = "Server not set up yet"
                )
                return@launch
            }

            val api = ApiClient.create(baseUrl)
            runCatching { api.getEpisodes(id = seasonBreadcrumb, page = page, ott = ott) }
                .onSuccess { res ->
                    _episodesState.value = _episodesState.value.copy(
                        episodes = _episodesState.value.episodes + res.episodes.orEmpty().filterNotNull(),
                        isLoadingMore = false,
                        hasMore = res.nextPageShow == 1 && res.nextPage != null,
                        nextPage = res.nextPage,
                        nextPageSeason = res.nextPageSeason
                    )
                }
                .onFailure { e ->
                    _episodesState.value = _episodesState.value.copy(
                        isLoadingMore = false,
                        error = e.message ?: "Failed to load more episodes"
                    )
                }
        }
    }

    fun toggleWatchlist(data: TitleDetailsResponse, isCurrentlyInWatchlist: Boolean) {
        viewModelScope.launch {
            val baseUrl = com.netmirror.netmirrortv.utils.AppSession.currentBaseUrl().ifBlank { prefs.getBaseUrl() }
            val ott = com.netmirror.netmirrortv.utils.AppSession.currentOtt().ifBlank { prefs.getOtt() }.ifBlank { data.ott.orEmpty() }
            if (baseUrl.isBlank()) return@launch

            val storedWatchlist = com.netmirror.netmirrortv.utils.WatchlistStore.getWatchlist(getApplication(), ott)
            val videoId = if (data.type == "t") "SE_${data.main_id}" else data.main_id

            // Optimistically update local watchlist
            if (isCurrentlyInWatchlist) {
                com.netmirror.netmirrortv.utils.WatchlistStore.removeLocally(getApplication(), ott, videoId)
            } else {
                com.netmirror.netmirrortv.utils.WatchlistStore.addLocally(getApplication(), ott, videoId)
            }

            val api = ApiClient.create(baseUrl)
            runCatching {
                if (isCurrentlyInWatchlist) {
                    // Item was in watchlist -> remove using Deleteid header
                    api.deleteWatchlist(
                        watchlist = storedWatchlist,
                        ott = ott,
                        deleteId = videoId
                    )
                } else {
                    // Item was not in watchlist -> add using Videoid header
                    api.toggleWatchlist(
                        watchlist = storedWatchlist,
                        ott = ott,
                        videoId = videoId
                    )
                }
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    val body = response.body()
                    android.util.Log.d("DetailsViewModel", "toggleWatchlist (deleted=$isCurrentlyInWatchlist) API response: key=${body?.key}, value=${body?.value}")
                    if (body != null) {
                        val key = body.key?.takeIf { it.isNotBlank() } ?: if (ott.isNotBlank()) "${ott}_watchlist" else "watchlist"
                        com.netmirror.netmirrortv.utils.WatchlistStore.saveWatchlist(getApplication(), key, body.value.orEmpty())
                    }
                } else {
                    android.util.Log.w("DetailsViewModel", "toggleWatchlist failed with code ${response.code()}")
                }
            }.onFailure { err ->
                android.util.Log.e("DetailsViewModel", "toggleWatchlist request failed: ${err.message}", err)
            }
        }
    }
}