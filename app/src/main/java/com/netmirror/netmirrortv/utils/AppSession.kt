package com.netmirror.netmirrortv.utils

import com.netmirror.netmirrortv.model.UpdateMessageResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSession {

    private val _selectedOtt = MutableStateFlow("nf")
    val selectedOtt: StateFlow<String> = _selectedOtt.asStateFlow()

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiVersion = MutableStateFlow("")
    val apiVersion: StateFlow<String> = _apiVersion.asStateFlow()

    private val _appVersion = MutableStateFlow(Constants.APP_VERSION)
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    private val _updateCheckTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updateCheckTrigger: SharedFlow<Unit> = _updateCheckTrigger.asSharedFlow()

    fun setOtt(ott: String) {
        _selectedOtt.value = ott
    }

    fun setBaseUrl(url: String) {
        _baseUrl.value = url
    }

    fun setApiVersion(version: String) {
        _apiVersion.value = version
    }

    fun setAppVersion(version: String) {
        _appVersion.value = version
    }

    fun triggerUpdateCheck() {
        _updateCheckTrigger.tryEmit(Unit)
    }

    fun currentOtt(): String = _selectedOtt.value

    fun currentBaseUrl(): String = _baseUrl.value

    fun currentApiVersion(): String = _apiVersion.value

    fun currentAppVersion(): String = _appVersion.value

    var hasShownUpdateDialog: Boolean = false
    var cachedUpdateData: UpdateMessageResponse? = null

    fun resetUpdateDialogState() {
        hasShownUpdateDialog = false
    }
}