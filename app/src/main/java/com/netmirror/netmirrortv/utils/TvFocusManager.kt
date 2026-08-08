package com.netmirror.netmirrortv.utils

import androidx.compose.ui.focus.FocusRequester

object TvFocusManager {
    var carousel: FocusRequester = FocusRequester()
    var selectedDrawerRequester: FocusRequester? = null
    var lastFocusedContentRequester: FocusRequester? = null

    // Default focus target of the currently visible screen
    var currentScreenContentRequester: FocusRequester? = null

    /**
     * Safely attempts to request focus on the given FocusRequester.
     * Returns true if successful, false otherwise.
     */
    fun requestFocus(requester: FocusRequester?): Boolean {
        if (requester == null) return false
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }

    /**
     * Attempts to move focus from Navigation Drawer into Content area.
     * Tries lastFocusedContentRequester first, then currentScreenContentRequester, then carousel.
     */
    fun moveFocusToContent(): Boolean {
        if (requestFocus(lastFocusedContentRequester)) return true
        if (requestFocus(currentScreenContentRequester)) return true
        return requestFocus(carousel)
    }

    /**
     * Attempts to move focus from Content area into Navigation Drawer.
     */
    fun moveFocusToDrawer(): Boolean {
        return requestFocus(selectedDrawerRequester)
    }
}