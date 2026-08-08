package com.netmirror.netmirrortv.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSession {

    private val _selectedOtt = MutableStateFlow("nf")
    val selectedOtt: StateFlow<String> = _selectedOtt.asStateFlow()

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    fun setOtt(ott: String) {
        _selectedOtt.value = ott
    }

    fun setBaseUrl(url: String) {
        _baseUrl.value = url
    }

    fun currentOtt(): String = _selectedOtt.value

    fun currentBaseUrl(): String = _baseUrl.value
}