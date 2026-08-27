package com.netmirror.netmirrortv.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = HomeRepository(application)
    private val _page = MutableStateFlow("all")
    val page: StateFlow<String> = _page.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _home = MutableStateFlow<HomeResponse?>(null)
    val home: StateFlow<HomeResponse?> = _home

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {

        viewModelScope.launch {

            AppSession.selectedOtt.collectLatest {

                loadHome()

            }

        }

    }

    fun loadHome(page: String = _page.value) {

        _page.value = page

        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null
                val result = repository.getHome(page)
                _home.value = result
                if (result == null) {
                    _error.value = "Main Page Loading Fail, Retry after some times."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Main Page Loading Fail, Retry after some times."
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteContinueWatchingItem(id: String) {
        // 1. Update UI state immediately
        _home.value = _home.value?.let { currentHome ->
            val updatedPosts = currentHome.post.mapNotNull { post ->
                val isContinueWatching = post.cate.startsWith("Continue Watching", ignoreCase = true) || post.cate.contains("Continue Watching", ignoreCase = true)
                if (isContinueWatching) {
                    val remainingIds = post.ids.split(',')
                        .map(String::trim)
                        .filter { it.isNotEmpty() && it != id && it != "SE_$id" && it.removePrefix("SE_") != id }
                    if (remainingIds.isEmpty()) {
                        null
                    } else {
                        post.copy(ids = remainingIds.joinToString(","))
                    }
                } else {
                    post
                }
            }
            currentHome.copy(post = updatedPosts)
        }

        // 2. Remove local playback progress
        com.netmirror.netmirrortv.utils.PlaybackProgressStore.removeProgress(getApplication(), id)

        // 3. Send API request & save updated progress
        viewModelScope.launch {
            repository.deleteRecentPlay(id)
        }
    }

    fun deleteWatchlistItem(id: String) {
        // 1. Update UI state immediately
        _home.value = _home.value?.let { currentHome ->
            val updatedPosts = currentHome.post.mapNotNull { post ->
                val isWatchlist = post.cate.startsWith("Watchlist", ignoreCase = true) ||
                        post.cate.contains("Watchlist", ignoreCase = true) ||
                        post.cate.startsWith("My List", ignoreCase = true) ||
                        post.cate.contains("My List", ignoreCase = true)
                if (isWatchlist) {
                    val remainingIds = post.ids.split(',')
                        .map(String::trim)
                        .filter { it.isNotEmpty() && it != id && it != "SE_$id" && it.removePrefix("SE_") != id }
                    if (remainingIds.isEmpty()) {
                        null
                    } else {
                        post.copy(ids = remainingIds.joinToString(","))
                    }
                } else {
                    post
                }
            }
            currentHome.copy(post = updatedPosts)
        }

        // 2. Send API request & save updated watchlist
        viewModelScope.launch {
            repository.deleteWatchlist(id)
        }
    }

}