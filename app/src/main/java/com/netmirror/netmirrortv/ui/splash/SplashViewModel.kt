package com.netmirror.netmirrortv.ui.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)

    private val repository = SplashRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    fun checkServer() {

        viewModelScope.launch {

            _uiState.value = SplashUiState(isLoading = true)

            repository.checkServer()
                .onSuccess {
                    val ott = preferences.getOtt()

                    AppSession.setOtt(ott)

                    showSuccess()
                }
                .onFailure { exception ->
                    showError(exception.message ?: "Unknown Error")
                }
        }
    }

    private fun showSuccess() {
        _uiState.value = SplashUiState(
            isLoading = false,
            isSuccess = true
        )
    }

    private fun showError(message: String) {
        _uiState.value = SplashUiState(
            isLoading = false,
            error = message
        )
    }
}