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
    private var loadedId: Int? = null

    private val _episodesState = MutableStateFlow(EpisodesUiState())
    val episodesState: StateFlow<EpisodesUiState> = _episodesState

    fun load(id: Int, force: Boolean = false) {
        if (!force && id == loadedId && _state.value is DetailsUiState.Success) return

        viewModelScope.launch {
            _state.value = DetailsUiState.Loading

            val baseUrl = prefs.getBaseUrl()
            val ott = prefs.getOtt()

            if (baseUrl.isBlank()) {
                _state.value = DetailsUiState.Error("Server not set up yet")
                return@launch
            }

            val api = ApiClient.create(baseUrl)
            runCatching { api.getDetails(id = id.toString(), ott = ott) }
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

    /**
     * Call once after a title finishes loading, to seed the currently
     * selected season's episodes from the post.php payload without an
     * extra network round trip.
     */
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
            val baseUrl = prefs.getBaseUrl()
            val ott = prefs.getOtt()

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
            val baseUrl = prefs.getBaseUrl()
            val ott = prefs.getOtt()

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
}