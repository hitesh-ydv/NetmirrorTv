package com.netmirror.netmirrortv.ui.home

import android.util.Log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.netmirror.netmirrortv.utils.TvFocusManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.netmirror.netmirrortv.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.navigation.Routes
import com.netmirror.netmirrortv.utils.PlaybackProgressStore
import kotlinx.coroutines.delay

private val RowCardShape = RoundedCornerShape(5)
private val PosterPlaceholder = Regex("-{3,}")
private const val CardFocusedScale = 1.04f
private val CardBorderColor = Color.White
private val CardFocusedContainerColor = Color(0xFF303030)
private val CardContainerColor = Color(0xFF202020)

internal val PortraitCardWidth = 150.dp
internal val LandscapeCardWidth = 200.dp
internal const val PortraitAspectRatio = 2f / 3f
internal const val LandscapeAspectRatio = 16f / 9f

/**
 * Resolves a poster URL for a single id from a CDN template. Shared between the row
 * renderer and HomeScreen's prefetcher so both build the exact same URL (and therefore
 * hit the same Coil cache key).
 */
internal fun resolveCardImageUrl(template: String?, id: String): String =
    template?.replace(PosterPlaceholder, id).orEmpty()

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeMediaRow(
    row: HomePost,
    horizontalImageTemplate: String?,
    verticalImageTemplate: String?,
    rowPosition: Int?, // 0‑based index of this row in the LazyColumn
    hasCarousel: Boolean = false,
    appNavController: NavHostController,
    firstCardFocusRequester: FocusRequester? = null,
    onFirstCardRequester: ((FocusRequester) -> Unit)? = null, // callback for the first card's requester
    onDelete: ((String) -> Unit)? = null,
) {
    val ids = remember(row.ids) {
        row.ids.split(',').map(String::trim).filter(String::isNotEmpty)
    }
    if (ids.isEmpty()) return

    val isPortraitRow = row.row.equals("v", ignoreCase = true)
    val imageTemplate = if (isPortraitRow) verticalImageTemplate else horizontalImageTemplate

    val cardFocusRequesters = remember(ids, firstCardFocusRequester, rowPosition) {
        ids.mapIndexed { index, _ ->
            if (index == 0 && rowPosition == 0 && firstCardFocusRequester != null) {
                firstCardFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    var lastFocusedIndex by remember(ids) { mutableIntStateOf(0) }

    val isTop10Row = row.cate.contains("top 10", ignoreCase = true)
    val isContinueWatchingRow = row.cate.startsWith("Continue Watching", ignoreCase = true) || row.cate.contains("Continue Watching", ignoreCase = true)

    // If this is the first row (rowPosition == 0), report the first card's requester
    LaunchedEffect(rowPosition, cardFocusRequesters) {
        if (rowPosition == 0 && cardFocusRequesters.isNotEmpty()) {
            onFirstCardRequester?.invoke(cardFocusRequesters.first())
        }
    }

    val rowState = rememberLazyListState()

    val density = LocalDensity.current
    val cardWidth = if (isPortraitRow) PortraitCardWidth else LandscapeCardWidth
    val aspectRatioValue = if (isPortraitRow) PortraitAspectRatio else LandscapeAspectRatio
    val widthPx = remember(cardWidth, density) { with(density) { cardWidth.toPx() }.toInt() }
    val heightPx = remember(widthPx, aspectRatioValue) { (widthPx / aspectRatioValue).toInt() }

    val cardBorder = CardDefaults.border(
        focusedBorder = Border(
            border = BorderStroke(
                width = 3.dp,
                color = CardBorderColor
            ),
            shape = RowCardShape
        )
    )

    val cardColors = CardDefaults.colors(
        containerColor = CardContainerColor,
        focusedContainerColor = CardFocusedContainerColor
    )
    val cardScale = remember { CardDefaults.scale(focusedScale = CardFocusedScale) }
    val cardShape = remember { CardDefaults.shape(shape = RowCardShape) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        Text(
            text = row.cate,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = 7.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = ids,
                key = { index, id -> "${id}_$index" },
                contentType = { _, _ -> if (isPortraitRow) "portrait" else "landscape" },
            ) { index, id ->
                val onFocused: () -> Unit = remember(index, cardFocusRequesters, id, rowPosition) {
                    {
                        lastFocusedIndex = index
                        TvFocusManager.lastFocusedContentRequester = cardFocusRequesters[index]
                        TvFocusManager.lastFocusedHomeCardId = id
                        TvFocusManager.lastFocusedHomeRowPosition = rowPosition
                        TvFocusManager.isCarouselLastFocused = false
                        TvFocusManager.lastFocusedSearchCardId = null
                    }
                }
                HomeMediaCard(
                    id = id,
                    imageUrl = resolveCardImageUrl(imageTemplate, id),
                    isPortrait = isPortraitRow,
                    isTop10 = isTop10Row,
                    isContinueWatching = isContinueWatchingRow,
                    rank = index + 1,
                    isFirstInRow = index == 0,
                    isFirstRow = (rowPosition == 0),
                    rowPosition = rowPosition,
                    hasCarousel = hasCarousel,
                    cardWidth = cardWidth,
                    aspectRatio = aspectRatioValue,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    border = cardBorder,
                    colors = cardColors,
                    scale = cardScale,
                    shape = cardShape,
                    focusRequester = cardFocusRequesters[index],
                    nextCardFocusRequester = if (index + 1 < cardFocusRequesters.size) cardFocusRequesters[index + 1] else null,
                    onFocused = onFocused,
                    onDelete = { onDelete?.invoke(id) },
                    appNavController = appNavController
                )
            }
        }
    }
}

@Composable
private fun HomeMediaCard(
    id: String,
    imageUrl: String,
    isPortrait: Boolean,
    isTop10: Boolean,
    isContinueWatching: Boolean = false,
    rank: Int,
    isFirstInRow: Boolean,
    isFirstRow: Boolean = false,
    rowPosition: Int? = null,
    hasCarousel: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp,
    aspectRatio: Float,
    widthPx: Int,
    heightPx: Int,
    border: CardBorder,
    colors: CardColors,
    scale: CardScale,
    shape: CardShape,
    focusRequester: FocusRequester,
    nextCardFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onDelete: (() -> Unit)? = null,
    appNavController: NavHostController,
) {
    val context = LocalContext.current

    // Restore focus to this card if it was the last focused card when navigating back from DetailsScreen
    LaunchedEffect(id, rowPosition) {
        if (TvFocusManager.lastFocusedHomeCardId == id &&
            (TvFocusManager.lastFocusedHomeRowPosition == null || TvFocusManager.lastFocusedHomeRowPosition == rowPosition)
        ) {
            repeat(5) { attempt ->
                val focused = runCatching { focusRequester.requestFocus() }.getOrDefault(false)
                if (focused) return@LaunchedEffect
                delay(50L * (attempt + 1))
            }
        }
    }

    val deleteFocusRequester = remember { FocusRequester() }
    var isDeleteFocused by remember { mutableStateOf(false) }

    val progressRatio = remember(id, isContinueWatching) {
        if (isContinueWatching) {
            PlaybackProgressStore.getProgressRatio(context, id)
        } else {
            null
        }
    }

    val rankDrawable = when (rank) {
        1 -> R.drawable.top_1
        2 -> R.drawable.top_2
        3 -> R.drawable.top_3
        4 -> R.drawable.top_4
        5 -> R.drawable.top_5
        6 -> R.drawable.top_6
        7 -> R.drawable.top_7
        8 -> R.drawable.top_8
        9 -> R.drawable.top_9
        10 -> R.drawable.top_10
        else -> R.drawable.top_10
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(aspectRatio)
    ) {

        Card(
            onClick = {
                Log.d("HomeMediaRows", "Clicked card ID: $id")
                onFocused()
                appNavController.navigate(Routes.details(id))
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when {
                            isContinueWatching && event.key == Key.DirectionRight -> {
                                deleteFocusRequester.requestFocus()
                                true
                            }
                            isContinueWatching && (event.key == Key.Delete || event.key == Key.Menu) -> {
                                onDelete?.invoke()
                                true
                            }
                            isFirstInRow && event.key == Key.DirectionLeft -> {
                                TvFocusManager.isCarouselLastFocused = false
                                TvFocusManager.lastFocusedHomeCardId = null
                                TvFocusManager.lastFocusedHomeRowPosition = null
                                TvFocusManager.moveFocusToDrawer()
                                true
                            }
                            isFirstRow && hasCarousel && event.key == Key.DirectionUp -> {
                                TvFocusManager.carousel.requestFocus()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            shape = shape,
            border = border,
            colors = colors,
            scale = scale,
        ) {

            Box {

                AsyncImage(
                    model = remember(imageUrl, widthPx, heightPx) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .size(Size(widthPx, heightPx))
                            .precision(Precision.INEXACT) // lets Coil reuse similarly-sized cached bitmaps instead of always decoding exact-fit
                            .allowRgb565(true) // ~half the memory + decode time of ARGB_8888; posters don't need alpha/full color depth
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Bottom Gradient for Top 10 rows
                if (isTop10 && rank <= 10){
                    val bottomGradient = remember {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .align(Alignment.BottomCenter)
                            .background(bottomGradient)
                    )
                }

                // Netflix-Style Continue Watching Bottom Scrim Gradient & Progress Bar
                if (isContinueWatching) {
                    val ratio = progressRatio ?: 0.35f
                    val continueWatchingGradient = remember {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.80f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    }

                    // Smooth bottom scrim gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .align(Alignment.BottomCenter)
                            .background(continueWatchingGradient)
                    )

                    // Netflix Red Progress Bar floating over the scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = ratio.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFE50914))
                        )
                    }
                }
            }
        }

        // TV-Focusable Delete button for Continue Watching cards (Top-Right)
        if (isContinueWatching) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .focusRequester(deleteFocusRequester)
                    .onFocusChanged { isDeleteFocused = it.isFocused }
                    .focusable()
                    .scale(if (isDeleteFocused) 1.25f else 1.0f)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDeleteFocused) Color.White else Color(0xFF141414).copy(alpha = 0.85f)
                    )
                    .border(
                        width = if (isDeleteFocused) 2.dp else 1.dp,
                        color = if (isDeleteFocused) Color(0xFFE50914) else Color.White.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onDelete?.invoke()
                                    true
                                }
                                Key.DirectionLeft, Key.Back -> {
                                    focusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (nextCardFocusRequester != null) {
                                        nextCardFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionDown -> {
                                    focusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .clickable { onDelete?.invoke() }
                    .zIndex(4f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from Continue Watching",
                    tint = if (isDeleteFocused) Color(0xFFE50914) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Netflix Top Number
        if (isTop10 && rank <= 10) {
            Image(
                painter = painterResource(rankDrawable),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (20).dp, y = (-20).dp)
                    .height(50.dp)
                    .zIndex(2f)
            )
        }
    }
}