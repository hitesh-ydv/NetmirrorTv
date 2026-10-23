package com.netmirror.netmirrortv.utils

import androidx.compose.ui.focus.FocusRequester

object TvFocusManager {
    var carousel: FocusRequester = FocusRequester()
    var selectedDrawerRequester: FocusRequester? = null
    var drawerRequesters: List<FocusRequester> = emptyList()
    var lastFocusedContentRequester: FocusRequester? = null
    var firstCardRequester: FocusRequester? = null
    var accountDrawerRequester: FocusRequester? = null
    var lastFocusedSearchCardId: String? = null

    // Track focused card or carousel in HomeScreen across navigation
    var lastFocusedHomeCardId: String? = null
    var lastFocusedHomeRowPosition: Int? = null
    var isCarouselLastFocused: Boolean = false

    // Default focus target of the currently visible screen
    var currentScreenContentRequester: FocusRequester? = null

    // Track active focus requesters per row position
    val rowFocusRequesters: MutableMap<Int, FocusRequester> = mutableMapOf()

    // Callback to focus the first media row in HomeScreen (restoring last-focused or visible card)
    var focusFirstRow: (() -> Boolean)? = null

    // Callback to focus the currently visible content/row in the active screen (HomeScreen, etc.)
    var focusVisibleContent: (() -> Boolean)? = null

    fun hasSavedHomeContentFocus(): Boolean {
        return isCarouselLastFocused || lastFocusedHomeCardId != null || rowFocusRequesters.isNotEmpty() || focusVisibleContent != null
    }

    fun hasSavedContentFocus(): Boolean {
        return hasSavedHomeContentFocus() || lastFocusedSearchCardId != null
    }

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
     * Tries lastFocusedContentRequester first, then visible content, then first row, then active row requesters, then screen defaults.
     */
    fun moveFocusToContent(): Boolean {
        if (isCarouselLastFocused && requestFocus(carousel)) return true
        if (lastFocusedHomeCardId != null && requestFocus(lastFocusedContentRequester)) return true
        if (requestFocus(lastFocusedContentRequester)) return true
        if (focusVisibleContent?.invoke() == true) return true
        if (focusFirstRow?.invoke() == true) return true
        for (pos in rowFocusRequesters.keys.sorted()) {
            val req = rowFocusRequesters[pos]
            if (req != null && requestFocus(req)) return true
        }
        if (requestFocus(currentScreenContentRequester)) return true
        if (requestFocus(firstCardRequester)) return true
        if (requestFocus(carousel)) return true
        return false
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

    /**
     * Attempts to move focus specifically to the Account button in the Navigation Drawer.
     */
    fun restoreAccountFocus(): Boolean {
        return requestFocus(accountDrawerRequester)
    }

    /**
     * Directly focuses on the Home Carousel and marks it as the active focused item.
     */
    fun focusCarouselDirectly(): Boolean {
        isCarouselLastFocused = true
        lastFocusedContentRequester = carousel
        lastFocusedHomeCardId = null
        lastFocusedHomeRowPosition = null
        lastFocusedSearchCardId = null
        return requestFocus(carousel)
    }
}