package com.netmirror.netmirrortv.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.model.NavbarResponse
import com.netmirror.netmirrortv.network.NavbarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.paging.LOG_TAG
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.flow.collectLatest

class NavbarViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        NavbarRepository(application)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _navbar =
        MutableStateFlow<NavbarResponse?>(null)

    val navbar: StateFlow<NavbarResponse?> =
        _navbar

    init {
        viewModelScope.launch {
            AppSession.selectedOtt.collect { ott ->
                Log.d("NAVBAR", "OTT Changed = $ott")
                loadNavbar()
            }
        }
    }

    fun loadNavbar() {

        viewModelScope.launch {

            try {

                _loading.value = true

                _navbar.value =
                    repository.getNavbar()

            } catch (e: Exception) {

                e.printStackTrace()

            } finally {

                _loading.value = false

            }

        }

    }

}