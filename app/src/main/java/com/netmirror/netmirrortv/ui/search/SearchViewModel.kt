package com.netmirror.netmirrortv.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.model.SearchResponse
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchViewModel(

    application: Application

) : AndroidViewModel(application) {

    private val repository = SearchRepository(application)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _searchResult = MutableStateFlow<SearchResponse?>(null)
    val searchResult: StateFlow<SearchResponse?> = _searchResult

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var searchJob: Job? = null
    private var lastQuery: String = ""

    init {
        viewModelScope.launch {
            AppSession.selectedOtt.collectLatest {
                // When OTT changes, auto reload search if results were already loaded
                if (_searchResult.value != null) {
                    search(lastQuery)
                }
            }
        }
    }

    fun search(

        text: String

    ) {

        // Cancel any in-flight search so results from an old keystroke
        // can never overwrite results from a newer one.
        searchJob?.cancel()

        val query = text.trim()
        lastQuery = query

        searchJob = viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val result = repository.search(query)

                if (result == null) {
                    _error.value = "Search Page Loading Fail, Retry after some times."
                }

                _searchResult.value = result

            } catch (e: Exception) {
                _error.value = "Search Page Loading Fail, Retry after some times."
            } finally {
                _loading.value = false
            }
        }

    }

    /** Call when the search screen is closed/backed out of, to reset state. */
    fun clear() {
        searchJob?.cancel()
        _searchResult.value = null
        _error.value = null
        _loading.value = false
    }

}