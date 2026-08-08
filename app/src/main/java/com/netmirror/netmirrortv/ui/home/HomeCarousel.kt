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
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeCarousel(
    slider: List<SliderItem>,
    modifier: Modifier = Modifier,
    onPlay: (SliderItem) -> Unit = {},
    firstCardRequester: FocusRequester? = null,
    appNavController: NavHostController,
) {
    if (slider.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Compute target size for background images (use screen dimensions as limit)
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()
    // For logos, we can use a smaller size (e.g., max width 800px, height 400px)
    val logoWidthPx = 800
    val logoHeightPx = 400

    var isCarouselFocused by remember { mutableStateOf(false) }
    var wrapTransitionKey by remember { mutableIntStateOf(0) }

    AnimatedContent(
        targetState = wrapTransitionKey,
        transitionSpec = {
            (fadeIn(tween(420)) + slideInHorizontally(tween(420)) { it / 12 })
                .togetherWith(fadeOut(tween(320)) + slideOutHorizontally(tween(320)) { -it / 12 })
        },
        label = "carouselWrapTransition",
    ) { transitionKey ->
        val carouselState = rememberCarouselState(initialActiveItemIndex = 0)

        LaunchedEffect(transitionKey) {
            if (transitionKey > 0) {
                TvFocusManager.carousel.requestFocus()
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
                            TvFocusManager.lastFocusedContentRequester = TvFocusManager.carousel
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                val activeItem = slider.getOrNull(carouselState.activeItemIndex)
                                if (activeItem != null) {
                                    TvFocusManager.lastFocusedContentRequester = TvFocusManager.carousel
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
                                firstCardRequester?.requestFocus()
                                true
                            }
                            else -> false
                        }
                    }
            ) { index ->
                val item = slider[index]
                Box(modifier = Modifier.fillMaxSize()) {
                    // Optimized background image
                    AsyncImage(
                        model = remember(item.img, screenWidthPx, screenHeightPx) {
                            ImageRequest.Builder(context)
                                .data(item.img)
                                .size(Size(screenWidthPx, screenHeightPx))
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
                            model = remember(item.namelogo, logoWidthPx, logoHeightPx) {
                                ImageRequest.Builder(context)
                                    .data(item.namelogo)
                                    .size(Size(logoWidthPx, logoHeightPx))
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