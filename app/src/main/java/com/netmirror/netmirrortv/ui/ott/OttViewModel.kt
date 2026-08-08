package com.netmirror.netmirrortv.ui.ott

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OttViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _background = MutableStateFlow("")
    val background: StateFlow<String> = _background

    private val _otts = MutableStateFlow<List<OttItem>>(emptyList())
    val otts: StateFlow<List<OttItem>> = _otts

    init {
        loadOtts()
    }

    private fun loadOtts() {

        viewModelScope.launch {

            try {

                val baseUrl = preferences.getBaseUrl()

                if (baseUrl.isEmpty()) {
                    _loading.value = false
                    return@launch
                }

                val apiService = ApiClient.create(baseUrl)
                val repository = OttRepository(apiService)

                repository.getOtts()?.let {

                    _background.value = it.bg_img
                    _otts.value = it.ott

                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveSelectedOtt(value: String) {

        viewModelScope.launch {
            preferences.saveOtt(value)
            AppSession.setOtt(value)
        }
    }
}