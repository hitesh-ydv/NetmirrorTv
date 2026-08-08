package com.netmirror.netmirrortv.ui.otp

import com.netmirror.netmirrortv.model.OtpResponse

/**
 * Mirrors the RN screen's `otp` / `error` / `success` / `isValidating` /
 * `item` state variables.
 */
data class OtpUiState(
    val otp: String = "",
    val error: String = "",
    val success: Boolean = false,
    val isValidating: Boolean = false,
    val response: OtpResponse? = null
)

/**
 * One-shot events the screen reacts to but shouldn't survive
 * recomposition/rotation as persisted state — navigation and refocusing
 * the input, same as the RN screen's `navigation.replace('Home')` and
 * `inputRef2.current?.focus()` calls.
 */
sealed interface OtpEvent {
    data object NavigateHome : OtpEvent
    data object RefocusInput : OtpEvent
}