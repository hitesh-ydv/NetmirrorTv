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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadOtts()
    }

    private fun loadOtts() {

        viewModelScope.launch {

            try {

                val baseUrl = preferences.getBaseUrl()

                if (baseUrl.isEmpty()) {
                    _error.value = "OTT Page Loading Fail, Retry after some times."
                    _loading.value = false
                    return@launch
                }

                val apiService = ApiClient.create(baseUrl)
                val repository = OttRepository(apiService)
                val userToken = com.netmirror.netmirrortv.util.TokenStore.getUserToken(getApplication()) ?: ""
                android.util.Log.d("UserToken", "OttViewModel.loadOtts -> Usertoken: '$userToken'")

                val response = repository.getOtts(userToken)
                if (response != null) {
                    _background.value = response.bg_img
                    _otts.value = response.ott
                } else {
                    _error.value = "OTT Page Loading Fail, Retry after some times."
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "OTT Page Loading Fail, Retry after some times."
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