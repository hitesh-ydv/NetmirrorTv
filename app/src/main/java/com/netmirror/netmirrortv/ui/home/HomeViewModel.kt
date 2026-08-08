package com.netmirror.netmirrortv.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = HomeRepository(application)
    private val _page = MutableStateFlow("all")

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _home = MutableStateFlow<HomeResponse?>(null)
    val home: StateFlow<HomeResponse?> = _home

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
                _home.value = repository.getHome(page)
            } finally {
                _loading.value = false
            }
        }
    }

}