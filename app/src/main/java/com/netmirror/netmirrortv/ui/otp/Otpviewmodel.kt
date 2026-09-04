package com.netmirror.netmirrortv.ui.otp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.network.ApiService
import com.netmirror.netmirrortv.network.BaseUrlProvider
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val OTP_LENGTH = 6
private const val SUCCESS_REDIRECT_DELAY_MS = 3000L
private const val ERROR_RESET_DELAY_MS = 5000L

/**
 * `apiUrl` plays the same role as `route.params.apiUrl` in the RN screen —
 * the working mirror base URL, presumably resolved earlier (e.g. during
 * the splash server check) and handed down to this screen.
 */
class OtpViewModel(
    application: Application,
    private val apiUrl: String
) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)

    private suspend fun resolveBaseUrl(): String {
        if (apiUrl.isNotBlank() && apiUrl != "{apiUrl}" && (apiUrl.startsWith("http://") || apiUrl.startsWith("https://"))) {
            return apiUrl
        }
        val fromSession = AppSession.currentBaseUrl()
        if (fromSession.isNotBlank()) return fromSession

        val fromProvider = BaseUrlProvider.getBaseUrl()
        if (fromProvider.isNotBlank()) return fromProvider

        val fromPrefs = preferences.getBaseUrl()
        if (fromPrefs.isNotBlank()) return fromPrefs

        return ""
    }

    private val _uiState = MutableStateFlow(OtpUiState())
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OtpEvent>()
    val events: SharedFlow<OtpEvent> = _events

    init {
        // Mirrors the RN screen's checkOtp('') call on mount — a "ping"
        // that also surfaces any pub_msg / pub_msg_img before the user has
        // typed anything.
        checkOtp("")
    }

    fun onOtpChanged(text: String) {
        if (_uiState.value.success) return // no edits after success, same as RN

        val numeric = text.filter(Char::isDigit).take(OTP_LENGTH)
        _uiState.update { it.copy(otp = numeric, error = "") }

        if (numeric.length == OTP_LENGTH && !_uiState.value.isValidating) {
            checkOtp(numeric)
        }
    }

    private fun checkOtp(otp: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true) }

            try {
                val baseUrl = resolveBaseUrl()
                Log.d("OtpViewModel", "checkOtp with url: '$baseUrl', otp: '$otp'")

                if (baseUrl.isBlank()) {
                    Log.e("OtpViewModel", "No valid Base URL found for OTP check!")
                    if (otp.isNotEmpty()) {
                        _uiState.update {
                            it.copy(isValidating = false, error = "Server connection error. Please restart the app.")
                        }
                    } else {
                        _uiState.update { it.copy(isValidating = false) }
                    }
                    return@launch
                }

                val apiService = ApiClient.create(baseUrl)
                val res = apiService.checkOtp(otp)
                Log.d("OtpViewModel", "checkOtp response code: ${res.code()}, isSuccessful: ${res.isSuccessful}")

                val body = res.body()

                if (!res.isSuccessful || body == null) {
                    Log.e("OtpViewModel", "checkOtp error body: ${res.errorBody()?.string()}")
                    if (otp.isNotEmpty()) {
                        _uiState.update {
                            it.copy(isValidating = false, error = "An error occurred. Please try again.")
                        }
                    } else {
                        _uiState.update { it.copy(isValidating = false) }
                    }
                    return@launch
                }

                Log.d("OtpViewModel", "checkOtp body: status=${body.status}, pub_msg=${body.pub_msg}, usertoken=${body.usertoken}")
                _uiState.update { it.copy(response = body) }

                when (body.status) {
                    "ok" -> {
                        _uiState.update { it.copy(success = true, isValidating = false) }
                        body.usertoken?.let { TokenStore.saveUserToken(getApplication(), it) }
                        delay(SUCCESS_REDIRECT_DELAY_MS)
                        _events.emit(OtpEvent.NavigateHome)
                    }
                    "error" -> {
                        if (otp.isNotEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isValidating = false,
                                    error = body.error_msg ?: "Invalid OTP. Please try again."
                                )
                            }
                            delay(ERROR_RESET_DELAY_MS)
                            _uiState.update { it.copy(otp = "", error = "") }
                            _events.emit(OtpEvent.RefocusInput)
                        } else {
                            _uiState.update { it.copy(isValidating = false) }
                        }
                    }
                    else -> _uiState.update { it.copy(isValidating = false) }
                }
            } catch (e: Exception) {
                Log.e("OtpViewModel", "checkOtp exception: ${e.message}", e)
                if (otp.isNotEmpty()) {
                    _uiState.update {
                        it.copy(isValidating = false, error = "An error occurred. Please try again.")
                    }
                } else {
                    _uiState.update { it.copy(isValidating = false) }
                }
            }
        }
    }
}

class OtpViewModelFactory(
    private val application: Application,
    private val apiUrl: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return OtpViewModel(application, apiUrl) as T
    }
}