package com.netmirror.netmirrortv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.netmirror.netmirrortv.utils.TvFocusManager
import kotlinx.coroutines.delay

/**
 * Data model for a single drawer destination.
 */
data class NavDrawerItem(
    val label: String,
    val icon: ImageVector,
)

/** The five destinations requested: Home, TV Shows, Movies, Search, OTT. */
val appNavDrawerItems =
    listOf(
        NavDrawerItem("Home", Icons.Default.Home),
        NavDrawerItem("TV Shows", Icons.Default.Tv),
        NavDrawerItem("Movies", Icons.Default.Movie),
        NavDrawerItem("Search", Icons.Default.Search),
        NavDrawerItem("OTT", Icons.Default.Apps),
    )

// ---- All drawer sizing/styling lives here as plain top-level constants.
// Hoisting these (instead of building them inline inside the composable)
// means they're allocated ONCE for the whole app process, not on every
// recomposition/animation frame — this was a real source of jank during
// the collapse/expand transition. ----
private val DRAWER_WIDTH_EXPANDED = 220.dp
private val DRAWER_WIDTH_COLLAPSED = 88.dp
private val DRAWER_ITEM_HEIGHT = 48.dp
private val DRAWER_ITEM_CORNER_RADIUS = 12.dp
private val DRAWER_ITEM_SHAPE = RoundedCornerShape(DRAWER_ITEM_CORNER_RADIUS)
private val DRAWER_ICON_SIZE = 24.dp
private const val DRAWER_ANIM_DURATION = 220
private const val DRAWER_FOCUS_ANIM_DURATION = 20
private val DRAWER_ANIM_EASING = FastOutSlowInEasing
private val DRAWER_SCRIM_WIDTH = DRAWER_WIDTH_EXPANDED + 180.dp
private val DRAWER_SELECTED_CONTAINER_COLOR = Color(0xFF3B3746)
private val DRAWER_LOGO_PLACEHOLDER_COLOR = Color(0xFF2A2A2A)
private val DRAWER_LOGO_SHAPE = RoundedCornerShape(6.dp)
private val DRAWER_SCRIM_BRUSH = Brush.horizontalGradient(
    colorStops = arrayOf(
        0.0f to Color.Black,
        0.15f to Color(0xF5000000),
        0.35f to Color(0xDD000000),
        0.60f to Color(0x88000000),
        1.0f to Color.Transparent
    )
)

@Composable
fun AppModalNavigationDrawer(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    content: @Composable () -> Unit,
) {

    val viewModel: NavbarViewModel = viewModel()
    val navbar by viewModel.navbar.collectAsState()
    val selectedOtt by com.netmirror.netmirrortv.utils.AppSession.selectedOtt.collectAsState()

    val isHsOtt = selectedOtt.equals("hs", ignoreCase = true)
    val visibleDrawerItems = remember(isHsOtt) {
        if (isHsOtt) {
            appNavDrawerItems.filter { it.label != "TV Shows" && it.label != "Movies" }
        } else {
            appNavDrawerItems
        }
    }

    val selectedItemIndex = if (isHsOtt && (selectedIndex == 1 || selectedIndex == 2)) 0 else selectedIndex.coerceIn(appNavDrawerItems.indices)
    val focusRequesters = remember {
        appNavDrawerItems.map { FocusRequester() }
    }

    // Tracks whether focus is anywhere inside the drawer (an item, or the
    // drawer container itself). Compose propagates `hasFocus` up to any
    // ancestor of a focused node, so this flips to false the moment focus
    // moves out to the carousel/content, and back to true on D-pad LEFT.
    var drawerHasFocus by remember { mutableStateOf(true) }

    val drawerWidth by animateDpAsState(
        targetValue = if (drawerHasFocus) DRAWER_WIDTH_EXPANDED else DRAWER_WIDTH_COLLAPSED,
        animationSpec = tween(DRAWER_ANIM_DURATION, easing = DRAWER_ANIM_EASING),
        label = "drawerWidth"
    )

    // FOCUS FIX:
    // requestFocus() can silently fail on the very first frame if the
    // target composable hasn't finished laying out yet — the old
    // single-shot runCatching left the drawer with nothing focused when
    // that happened. Retry with backoff instead of a single best-effort try.
    LaunchedEffect(selectedItemIndex) {
        TvFocusManager.selectedDrawerRequester = focusRequesters[selectedItemIndex]
        repeat(5) { attempt ->
            val focused = runCatching { focusRequesters[selectedItemIndex].requestFocus() }
                .getOrDefault(false)
            if (focused) return@LaunchedEffect
            delay(50L * (attempt + 1))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Content sits underneath the drawer.
        content()

        DrawerScrim(visible = drawerHasFocus)

        // The drawer itself — plain Column, fully custom + animated width.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .onFocusChanged { drawerHasFocus = it.hasFocus }
                .padding(horizontal = 16.dp, vertical = 15.dp)
                .selectableGroup(),

            horizontalAlignment = Alignment.Start,

            verticalArrangement = Arrangement.Center
        ) {

            DrawerLogo(url = navbar?.main_logo)

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.focusRestorer(focusRequesters[selectedItemIndex])
            ) {

                visibleDrawerItems.forEach { item ->
                    val originalIndex = appNavDrawerItems.indexOf(item)

                    CustomNavDrawerItem(
                        item = item,
                        selected = selectedItemIndex == originalIndex,
                        showLabel = drawerHasFocus,
                        onClick = {
                            if (selectedItemIndex != originalIndex) {
                                onItemSelected(originalIndex)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequesters[originalIndex])
                            .onFocusChanged {
                                if (it.isFocused) {
                                    TvFocusManager.selectedDrawerRequester = focusRequesters[originalIndex]
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                    TvFocusManager.moveFocusToContent()
                                    true  // always consume Right key to prevent default navigation
                                } else {
                                    false
                                }
                            }
                            .padding(vertical = 6.dp),
                    )

                }

            }

            Spacer(modifier = Modifier.weight(1f))

            DrawerLogo(url = navbar?.acc_logo)

        }

    }

}

/**
 * Scrim gradient, isolated into its own composable so recomposing it
 * (during the fade in/out) never touches the rest of the drawer tree.
 */
@Composable
private fun DrawerScrim(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(DRAWER_ANIM_DURATION)),
        exit = fadeOut(tween(DRAWER_ANIM_DURATION)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(DRAWER_SCRIM_WIDTH)
                .background(DRAWER_SCRIM_BRUSH)
        )
    }
}

/**
 * Top/bottom logo slot, isolated so a navbar update only recomposes this
 * small piece instead of the whole drawer.
 */
@Composable
private fun DrawerLogo(url: String?) {
    AnimatedContent(
        modifier = Modifier.padding(start = 10.dp),
        targetState = url ?: "",
        transitionSpec = {
            (slideInHorizontally { it / 4 } + fadeIn())
                .togetherWith(
                    slideOutHorizontally { -it / 4 } + fadeOut()
                )
                .using(SizeTransform(clip = false))
        },
        label = "DrawerLogoAnimation"
    ) { logo ->

        // Starts false on every recomposition of this slot (new `logo`
        // value from AnimatedContent), so the placeholder color shows again
        // until Coil actually reports success — not just while `url` is null.
        var isLoaded by remember(logo) { mutableStateOf(false) }

        AsyncImage(
            model = logo,
            contentDescription = null,
            onState = { state ->
                isLoaded = state is AsyncImagePainter.State.Success
            },
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
                .background(
                    color = if (isLoaded) Color.Transparent else DRAWER_LOGO_PLACEHOLDER_COLOR,
                    shape = DRAWER_LOGO_SHAPE,
                )
        )
    }
}

/**
 * Drop-in replacement for androidx.tv.material3.NavigationDrawerItem.
 * Plain Row + manual focus/selected color handling, so width, height,
 * corner radius, colors, etc. are all just normal Modifiers you control.
 *
 * The label collapses away (fade + horizontal shrink) whenever [showLabel]
 * is false, leaving just the icon at a fixed size — driven by whether the
 * drawer as a whole currently has focus. Item background/text color changes
 * transition quickly between focus and selection states, while the drawer's
 * overall width transition remains the primary motion.
 */
@Composable
private fun CustomNavDrawerItem(
    item: NavDrawerItem,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    val targetContainerColor = when {
        isFocused -> Color.White
        selected -> DRAWER_SELECTED_CONTAINER_COLOR
        else -> Color.Transparent
    }

    val targetContentColor = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.Gray
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(DRAWER_FOCUS_ANIM_DURATION),
        label = "drawerItemContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(DRAWER_FOCUS_ANIM_DURATION),
        label = "drawerItemContentColor",
    )

    // 👇 Animate the icon offset from 0 (expanded) to 2.dp (collapsed)
    val iconOffset by animateDpAsState(
        targetValue = if (showLabel) 0.dp else 2.dp,  // 2.dp = (collapsed width - padding - iconSize)/2
        animationSpec = tween(DRAWER_ANIM_DURATION, easing = DRAWER_ANIM_EASING),
        label = "iconOffset"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(DRAWER_ITEM_HEIGHT)
            .clip(DRAWER_ITEM_SHAPE)
            .background(containerColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,   // always start – no sudden change
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier
                .requiredSize(DRAWER_ICON_SIZE)
                .offset(x = iconOffset),  // 👈 smooth shift when collapsing
        )

        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(tween(DRAWER_ANIM_DURATION)),
            exit = fadeOut(tween(DRAWER_ANIM_DURATION)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.label,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}