package com.netmirror.netmirrortv.utils

import androidx.compose.ui.focus.FocusRequester

object TvFocusManager {
    var carousel: FocusRequester = FocusRequester()
    var selectedDrawerRequester: FocusRequester? = null
    var drawerRequesters: List<FocusRequester> = emptyList()
    var lastFocusedContentRequester: FocusRequester? = null
    var firstCardRequester: FocusRequester? = null
    var lastFocusedSearchCardId: String? = null

    // Track focused card or carousel in HomeScreen across navigation
    var lastFocusedHomeCardId: String? = null
    var lastFocusedHomeRowPosition: Int? = null
    var isCarouselLastFocused: Boolean = false

    // Default focus target of the currently visible screen
    var currentScreenContentRequester: FocusRequester? = null

    /**
     * Safely attempts to request focus on the given FocusRequester.
     * Returns true if successful, false otherwise.
     */
    fun requestFocus(requester: FocusRequester?): Boolean {
        if (requester == null) return false
        return runCatching { requester.requestFocus() }.isSuccess
    }

    /**
     * Attempts to move focus from Navigation Drawer into Content area.
     * Tries lastFocusedContentRequester first, then currentScreenContentRequester, then carousel, then firstCardRequester.
     */
    fun moveFocusToContent(): Boolean {
        if (isCarouselLastFocused && requestFocus(carousel)) return true
        if (requestFocus(lastFocusedContentRequester)) return true
        if (requestFocus(currentScreenContentRequester)) return true
        if (requestFocus(carousel)) return true
        return requestFocus(firstCardRequester)
    }

    /**
     * Attempts to move focus from Content area into Navigation Drawer.
     */
    fun moveFocusToDrawer(): Boolean {
        if (requestFocus(selectedDrawerRequester)) return true
        drawerRequesters.forEach { req ->
            if (requestFocus(req)) return true
        }
        return false
    }
}