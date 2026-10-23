package com.netmirror.netmirrortv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    onAccountClick: () -> Unit = {},
    isAccountDialogOpen: Boolean = false,
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
    val hasActiveContentFocus = TvFocusManager.hasSavedContentFocus()

    var drawerHasFocus by remember { mutableStateOf(!hasActiveContentFocus) }
    var isAccountFocused by remember { mutableStateOf(false) }
    val isDrawerExpanded = drawerHasFocus && !isAccountFocused

    val drawerWidth by animateDpAsState(
        targetValue = if (isDrawerExpanded) DRAWER_WIDTH_EXPANDED else DRAWER_WIDTH_COLLAPSED,
        animationSpec = tween(DRAWER_ANIM_DURATION, easing = DRAWER_ANIM_EASING),
        label = "drawerWidth"
    )

    val coroutineScope = rememberCoroutineScope()

    // Ensure initial focus is on Home tab (index 0) on launch
    LaunchedEffect(Unit) {
        repeat(12) { attempt ->
            val hasSavedContentFocus = TvFocusManager.hasSavedContentFocus()
            if (hasSavedContentFocus) return@LaunchedEffect
            val requester = focusRequesters.getOrNull(0)
            if (requester != null) {
                val focused = runCatching { requester.requestFocus() }.getOrDefault(false)
                if (focused) return@LaunchedEffect
            }
            delay(50L * (attempt + 1))
        }
    }

    // FOCUS FIX:
    // Only request focus into the drawer if the content area doesn't have an active/saved target.
    LaunchedEffect(selectedItemIndex, focusRequesters) {
        TvFocusManager.drawerRequesters = focusRequesters
        TvFocusManager.selectedDrawerRequester = focusRequesters.getOrNull(selectedItemIndex)
        val hasSavedContentFocus = TvFocusManager.hasSavedContentFocus()
        if (!hasSavedContentFocus) {
            repeat(5) { attempt ->
                val focused = runCatching { focusRequesters[selectedItemIndex].requestFocus() }
                    .getOrDefault(false)
                if (focused) return@LaunchedEffect
                delay(50L * (attempt + 1))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content sits underneath the drawer.
        content()
        DrawerScrim(visible = isDrawerExpanded)
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

            Column {

                visibleDrawerItems.forEach { item ->
                    val originalIndex = appNavDrawerItems.indexOf(item)

                    CustomNavDrawerItem(
                        item = item,
                        selected = selectedItemIndex == originalIndex,
                        showLabel = isDrawerExpanded,
                        onClick = {
                            onItemSelected(originalIndex)
                            coroutineScope.launch {
                                repeat(12) { attempt ->
                                    if (TvFocusManager.moveFocusToContent()) return@launch
                                    delay(25L * (attempt + 1))
                                }
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
                                    if (!TvFocusManager.moveFocusToContent()) {
                                        coroutineScope.launch {
                                            repeat(12) { attempt ->
                                                if (TvFocusManager.moveFocusToContent()) return@launch
                                                delay(25L * (attempt + 1))
                                            }
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .padding(vertical = 6.dp),
                    )

                }

            }

            Spacer(modifier = Modifier.weight(1f))

            DrawerAccountLogo(
                url = navbar?.acc_logo,
                isAccountActive = isAccountDialogOpen,
                onFocusChanged = { isAccountFocused = it },
                onClick = onAccountClick
            )

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
 * Focusable Account Logo with corner radius, D-pad navigation, and visual feedback.
 */
@Composable
private fun DrawerAccountLogo(
    url: String?,
    isAccountActive: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val accountRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(accountRequester) {
        TvFocusManager.accountDrawerRequester = accountRequester
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(DRAWER_ANIM_DURATION),
        label = "accountLogoScale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isAccountActive -> Color(0xFFE50914)
            else -> Color.Transparent
        },
        animationSpec = tween(DRAWER_FOCUS_ANIM_DURATION),
        label = "accountLogoBorder"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.22f)
            isAccountActive -> Color(0xFFE50914).copy(alpha = 0.20f)
            else -> Color.Transparent
        },
        animationSpec = tween(DRAWER_FOCUS_ANIM_DURATION),
        label = "accountLogoBg"
    )

    val shape = RoundedCornerShape(3.dp)

    Box(
        modifier = Modifier.padding(start = 10.dp),
        contentAlignment = Alignment.TopStart
    ) {
        // Tooltip Overlay placed directly above the account logo button
        AnimatedVisibility(
            visible = isFocused,
            enter = fadeIn(tween(140)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = tween(140, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .zIndex(10f)
                .layout { measurable, _ ->
                    // Measure unconstrained so it is never squeezed by the narrow collapsed drawer
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = Constraints.Infinity,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity
                        )
                    )
                    // Report 0x0 so parent Box size is 100% determined by the button itself
                    layout(0, 0) {
                        // Place directly above the button at the bottom of the drawer:
                        // -10.dp x-offset aligns with the drawer content boundary and centers over button
                        // -(placeable.height + 6.dp) places the arrow tip 6dp above the top of the button
                        placeable.placeRelative(
                            x = (-10).dp.roundToPx(),
                            y = -(placeable.height + 6.dp.roundToPx())
                        )
                    }
                }
        ) {
            AccountLogoTooltip(
                text = "My Profile",
                arrowCenterX = 29.dp // 29dp from tooltip start points exactly at the 19dp center of the 38dp button (-10dp + 29dp = 19dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .focusRequester(accountRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
//            .graphicsLayer {
//                scaleX = scale
//                scaleY = scale
//            }
                .clip(shape)
                .background(backgroundColor)
                .border(1.dp, borderColor, shape)
                .clickable(onClick = onClick)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionRight -> {
                                if (!TvFocusManager.moveFocusToContent()) {
                                    coroutineScope.launch {
                                        repeat(12) { attempt ->
                                            if (TvFocusManager.moveFocusToContent()) return@launch
                                            delay(25L * (attempt + 1))
                                        }
                                    }
                                }
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onClick()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable()
                .padding(3.dp)
        ) {
            var isLoaded by remember(url) { mutableStateOf(false) }

            AsyncImage(
                model = url,
                contentDescription = "My Account",
                onState = { state ->
                    isLoaded = state is AsyncImagePainter.State.Success
                },
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
                    .clip(shape)
                    .background(
                        color = if (isLoaded) Color.Transparent else DRAWER_LOGO_PLACEHOLDER_COLOR,
                        shape = shape,
                    ),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Sleek tooltip overlay badge displayed above the account logo button on focus.
 */
@Composable
private fun AccountLogoTooltip(
    text: String,
    arrowCenterX: Dp = 29.dp,
    modifier: Modifier = Modifier
) {
    val arrowHeight = 6.dp
    val shape = remember(arrowCenterX) {
        TooltipShape(
            cornerRadius = 6.dp,
            arrowWidth = 10.dp,
            arrowHeight = arrowHeight,
            arrowCenterX = arrowCenterX
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.65f)
            )
            .background(Color(0xFF222228), shape = shape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = shape
            )
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = 5.dp,
                bottom = 5.dp + arrowHeight
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Custom tooltip bubble shape with rounded corners and a bottom-pointing arrow.
 */
private class TooltipShape(
    private val cornerRadius: Dp = 6.dp,
    private val arrowWidth: Dp = 10.dp,
    private val arrowHeight: Dp = 6.dp,
    private val arrowCenterX: Dp? = null
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cr = with(density) { cornerRadius.toPx() }
        val aw = with(density) { arrowWidth.toPx() }
        val ah = with(density) { arrowHeight.toPx() }
        val bubbleBottom = size.height - ah

        val arrowCenter = if (arrowCenterX != null) {
            with(density) { arrowCenterX.toPx() }
        } else {
            size.width / 2f
        }
        val arrowLeft = (arrowCenter - aw / 2f).coerceIn(cr, size.width - cr - aw)
        val arrowRight = arrowLeft + aw
        val arrowTip = arrowCenter.coerceIn(cr + aw / 4f, size.width - cr - aw / 4f)

        val path = Path().apply {
            moveTo(cr, 0f)
            lineTo(size.width - cr, 0f)
            arcTo(
                rect = Rect(size.width - 2 * cr, 0f, size.width, 2 * cr),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(size.width, bubbleBottom - cr)
            arcTo(
                rect = Rect(size.width - 2 * cr, bubbleBottom - 2 * cr, size.width, bubbleBottom),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(arrowRight, bubbleBottom)
            lineTo(arrowTip, size.height)
            lineTo(arrowLeft, bubbleBottom)
            lineTo(cr, bubbleBottom)
            arcTo(
                rect = Rect(0f, bubbleBottom - 2 * cr, 2 * cr, bubbleBottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(0f, cr)
            arcTo(
                rect = Rect(0f, 0f, 2 * cr, 2 * cr),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }
        return Outline.Generic(path)
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