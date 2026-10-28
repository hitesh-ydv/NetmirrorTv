package com.netmirror.netmirrortv.ui.home

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.rememberCarouselState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.netmirror.netmirrortv.utils.TvFocusManager

import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.navigation.Routes
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeCarousel(
    slider: List<SliderItem>,
    imgReferer: String? = null,
    modifier: Modifier = Modifier,
    onPlay: (SliderItem) -> Unit = {},
    firstCardRequester: FocusRequester? = null,
    onDownPress: (() -> Unit)? = null,
    appNavController: NavHostController,
) {
    if (slider.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Compute target size for background images (use screen dimensions as limit)
    val screenWidthPx = remember(density, configuration.screenWidthDp) {
        with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    }
    val screenHeightPx = remember(density, configuration.screenHeightDp) {
        with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()
    }
    // For logos, we can use a smaller size (e.g., max width 800px, height 400px)
    val logoWidthPx = 800
    val logoHeightPx = 400

    var isCarouselFocused by remember { mutableStateOf(false) }
    var wrapTransitionKey by remember { mutableIntStateOf(0) }
    // Survives the trip to DetailsScreen so the carousel reopens on the same slide.
    var savedActiveIndex by rememberSaveable { mutableIntStateOf(0) }
    // Bumped on every ON_RESUME (e.g. coming back from details/player) to re-run the focus restore.
    var restoreTick by remember { mutableIntStateOf(0) }
    // Set when the user presses a key themselves -> stop forcing focus back to the carousel.
    var userMoved by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, LocalLifecycleOwner.current) { restoreTick++ }

    AnimatedContent(
        targetState = wrapTransitionKey,
        transitionSpec = {
            (fadeIn(tween(420)) + slideInHorizontally(tween(420)) { it / 12 })
                .togetherWith(fadeOut(tween(320)) + slideOutHorizontally(tween(320)) { -it / 12 })
        },
        label = "carouselWrapTransition",
    ) { transitionKey ->
        val carouselState = rememberCarouselState(
            initialActiveItemIndex = if (transitionKey > 0) 0 else savedActiveIndex.coerceIn(0, slider.lastIndex)
        )

        LaunchedEffect(carouselState) {
            snapshotFlow { carouselState.activeItemIndex }.collect { savedActiveIndex = it }
        }

        // Sticky focus restore. A single request is not enough: when returning from another screen the
        // window / previous destination can clear focus AFTER our first successful request. So keep
        // (cheaply) re-requesting for ~2.5s, until the user presses a key or leaves the carousel.
        LaunchedEffect(transitionKey, restoreTick) {
            if (transitionKey == 0 && !TvFocusManager.isCarouselLastFocused) return@LaunchedEffect
            userMoved = false
            var elapsed = 0L
            while (elapsed < RestoreWindowMs && !userMoved &&
                (transitionKey > 0 || TvFocusManager.isCarouselLastFocused)
            ) {
                withFrameNanos { }
                TvFocusManager.requestFocus(TvFocusManager.carousel)   // no-op when already focused
                delay(RestoreStepMs)
                elapsed += RestoreStepMs
            }
        }

        Box(modifier = Modifier.fillMaxWidth().background(color = Color.Black)) {
            Carousel(
                itemCount = slider.size,
                carouselState = carouselState,
                contentTransformStartToEnd =
                    (fadeIn(tween(420)) + slideInHorizontally(tween(420)) { it / 12 })
                        .togetherWith(
                            fadeOut(tween(320)) + slideOutHorizontally(tween(320)) { -it / 12 }
                        ),
                contentTransformEndToStart =
                    (fadeIn(tween(420)) + slideInHorizontally(tween(420)) { -it / 12 })
                        .togetherWith(
                            fadeOut(tween(320)) + slideOutHorizontally(tween(320)) { it / 12 }
                        ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(modifier)
                    .focusRequester(TvFocusManager.carousel)
                    .onFocusChanged {
                        isCarouselFocused = it.isFocused || it.hasFocus
                        if (it.isFocused || it.hasFocus) {
                            markCarouselFocused()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        val isWrapRight = event.key == Key.DirectionRight &&
                                carouselState.activeItemIndex == slider.lastIndex
                        if (event.type == KeyEventType.KeyDown && !isWrapRight) userMoved = true
                        when {
                            event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                val activeItem = slider.getOrNull(carouselState.activeItemIndex)
                                if (activeItem != null) {
                                    markCarouselFocused()
                                    appNavController.navigate(Routes.details(activeItem.id))
                                }
                                true
                            }
                            event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionLeft -> {
                                TvFocusManager.moveFocusToDrawer()
                                true
                            }
                            event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionRight &&
                                    carouselState.activeItemIndex == slider.lastIndex -> {
                                wrapTransitionKey++
                                true
                            }
                            event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionDown -> {
                                TvFocusManager.isCarouselLastFocused = false
                                val rowZeroRequester = TvFocusManager.rowFocusRequesters[0]
                                val focused = (TvFocusManager.focusFirstRow?.invoke() == true) ||
                                        TvFocusManager.requestFocus(rowZeroRequester) ||
                                        TvFocusManager.requestFocus(firstCardRequester) ||
                                        TvFocusManager.requestFocus(TvFocusManager.firstCardRequester)
                                if (focused) {
                                    true
                                } else if (onDownPress != null) {
                                    onDownPress()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
            ) { index ->
                val item = slider[index]
                Box(modifier = Modifier.fillMaxSize()) {
                    // Optimized background image
                    AsyncImage(
                        model = remember(item.img, screenWidthPx, screenHeightPx, imgReferer) {
                            ImageRequest.Builder(context)
                                .data(item.img)
                                .size(Size(screenWidthPx, screenHeightPx))
                                .apply {
                                    if (!imgReferer.isNullOrBlank()) {
                                        setHeader("Referer", imgReferer)
                                    }
                                    setHeader("X-Requested-With", "NetmirrorNewTV v1.0")
                                }
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val verticalGradient = Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.30f),
                                    0.35f to Color.Transparent,
                                    0.65f to Color.Transparent,
                                    0.85f to Color.Black.copy(alpha = 0.78f),
                                    1.0f to Color.Black,
                                )
                                val horizontalGradient = Brush.horizontalGradient(
                                    0.0f to Color.Black,
                                    0.20f to Color.Black.copy(alpha = 0.95f),
                                    0.40f to Color.Black.copy(alpha = 0.75f),
                                    0.65f to Color.Black.copy(alpha = 0.35f),
                                    1.0f to Color.Transparent,
                                )
                                onDrawBehind {
                                    drawRect(verticalGradient)
                                    drawRect(horizontalGradient)
                                }
                            },
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(end = 120.dp, top = 24.dp, bottom = 40.dp)
                            .widthIn(max = 700.dp),
                    ) {
                        // Optimized logo image
                        AsyncImage(
                            model = remember(item.namelogo, logoWidthPx, logoHeightPx, imgReferer) {
                                ImageRequest.Builder(context)
                                    .data(item.namelogo)
                                    .size(Size(logoWidthPx, logoHeightPx))
                                    .apply {
                                        if (!imgReferer.isNullOrBlank()) {
                                            setHeader("Referer", imgReferer)
                                        }
                                        setHeader("X-Requested-With", "NetmirrorNewTV v1.0")
                                    }
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = null,
                            modifier = Modifier.heightIn(max = 135.dp).wrapContentWidth(),
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = item.desc,
                            modifier = Modifier.widthIn(max = 500.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(28.dp))
                        PlayHintChip()
                    }
                }
            }
        }
    }
}

private const val RestoreWindowMs = 2500L
private const val RestoreStepMs = 100L

private fun markCarouselFocused() {
    TvFocusManager.lastFocusedContentRequester = TvFocusManager.carousel
    TvFocusManager.isCarouselLastFocused = true
    TvFocusManager.lastFocusedHomeCardId = null
    TvFocusManager.lastFocusedHomeRowPosition = null
    TvFocusManager.lastFocusedSearchCardId = null
}

@Composable
private fun PlayHintChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        val contentColor = Color.Black
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = contentColor)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Play Now",
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}