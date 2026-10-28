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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import android.os.SystemClock
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
private val CardBorderColor = Color(0xFFE50914)
private val CardFocusedContainerColor = Color(0xFF303030)
private val CardContainerColor = Color(0xFF202020)

internal const val LandscapeFullCardsCount = 5
internal const val PortraitFullCardsCount = 5
internal const val CardPeekFraction = 0.05f // 25% of the next card is "little visible" (peeking)
internal val RowStartPadding = 7.dp
internal val RowEndPadding = 24.dp
internal val CardSpacing = 12.dp

/**
 * Calculates the width of a card such that:
 * - For landscape: 5 full cards + 6th card little visible (peeking)
 * - For portrait: 6 full cards + 7th card little visible (peeking)
 * dynamically based on device measurements.
 */
internal fun calculateCardWidth(availableWidth: Dp, isPortrait: Boolean = true): Dp {
    val fullCardsCount = if (isPortrait) PortraitFullCardsCount else LandscapeFullCardsCount
    val effectiveCount = fullCardsCount + CardPeekFraction
    val totalSpacing = (CardSpacing * fullCardsCount) + RowStartPadding
    val computedWidth = (availableWidth - totalSpacing) / effectiveCount
    return computedWidth.coerceAtLeast(80.dp)
}

internal fun calculateCardWidthForScreen(screenWidth: Dp, isPortrait: Boolean = true): Dp {
    val availableWidth = screenWidth - 100.dp // 100.dp start padding for the navigation drawer
    return calculateCardWidth(availableWidth, isPortrait)
}

internal val PortraitCardWidth = 200.dp
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

// Centers the focused card in the viewport (horizontally in LazyRow and vertically in LazyColumn)
@OptIn(ExperimentalFoundationApi::class)
internal object CenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val itemCenter = offset + size / 2f
        val containerCenter = containerSize / 2f
        return itemCenter - containerCenter
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun HomeMediaRow(
    row: HomePost,
    horizontalImageTemplate: String?,
    verticalImageTemplate: String?,
    rowPosition: Int?, // 0‑based index of this row in the LazyColumn
    hasCarousel: Boolean = false,
    imgReferer: String? = null,
    appNavController: NavHostController,
    firstCardFocusRequester: FocusRequester? = null,
    onFirstCardRequester: ((FocusRequester) -> Unit)? = null, // callback for the first card's requester
    onDelete: ((String) -> Unit)? = null,
    onDeleteLastCard: ((rowPosition: Int) -> Unit)? = null,
) {
    val ids = remember(row.ids) {
        row.ids.split(',').map(String::trim).filter(String::isNotEmpty)
    }
    if (ids.isEmpty()) return

    val isPortraitRow = row.row.equals("v", ignoreCase = true)
    val imageTemplate = if (isPortraitRow) verticalImageTemplate else horizontalImageTemplate

    val cardFocusRequesters = remember(ids, firstCardFocusRequester) {
        ids.mapIndexed { index, _ ->
            if (index == 0 && firstCardFocusRequester != null) {
                firstCardFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    var lastFocusedIndex by remember(ids) { mutableIntStateOf(0) }

    val isTop10Row = row.cate.contains("top 10", ignoreCase = true)
    val isContinueWatchingRow = row.cate.startsWith("Continue Watching", ignoreCase = true) || row.cate.contains("Continue Watching", ignoreCase = true)

    // If this row has the first card focus requester, report it
    LaunchedEffect(cardFocusRequesters, onFirstCardRequester) {
        if (firstCardFocusRequester != null && cardFocusRequesters.isNotEmpty()) {
            onFirstCardRequester?.invoke(cardFocusRequesters.first())
        }
    }

    DisposableEffect(rowPosition, cardFocusRequesters) {
        if (rowPosition != null && cardFocusRequesters.isNotEmpty()) {
            val targetReq = cardFocusRequesters.getOrNull(lastFocusedIndex) ?: cardFocusRequesters.first()
            TvFocusManager.rowFocusRequesters[rowPosition] = targetReq
        }
        onDispose {
            if (rowPosition != null) {
                TvFocusManager.rowFocusRequesters.remove(rowPosition)
            }
        }
    }

    var pendingFocusTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ids, pendingFocusTargetId) {
        val targetId = pendingFocusTargetId ?: return@LaunchedEffect
        val targetIndex = ids.indexOf(targetId)
        if (targetIndex != -1) {
            repeat(10) { attempt ->
                val requester = cardFocusRequesters.getOrNull(targetIndex)
                if (requester != null && TvFocusManager.requestFocus(requester)) {
                    pendingFocusTargetId = null
                    return@LaunchedEffect
                }
                delay(25L * (attempt + 1))
            }
        }
    }

    val rowState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (rowPosition == 0) {
        DisposableEffect(cardFocusRequesters, rowState) {
            TvFocusManager.focusFirstRow = {
                val lastReq = cardFocusRequesters.getOrNull(lastFocusedIndex)
                if (lastReq != null && TvFocusManager.requestFocus(lastReq)) {
                    true
                } else {
                    val visibleIndex = rowState.firstVisibleItemIndex
                    val visibleReq = cardFocusRequesters.getOrNull(visibleIndex)
                    if (visibleReq != null && TvFocusManager.requestFocus(visibleReq)) {
                        true
                    } else if (cardFocusRequesters.isNotEmpty() && TvFocusManager.requestFocus(cardFocusRequesters.first())) {
                        true
                    } else {
                        coroutineScope.launch {
                            rowState.scrollToItem(0)
                            repeat(6) {
                                if (cardFocusRequesters.firstOrNull()?.let { TvFocusManager.requestFocus(it) } == true) return@launch
                                delay(30L)
                            }
                        }
                        true
                    }
                }
            }
            onDispose {
                if (TvFocusManager.focusFirstRow != null) {
                    TvFocusManager.focusFirstRow = null
                }
            }
        }
    }

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val fallbackAvailableWidth = screenWidth - 100.dp

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        val availableWidth = if (maxWidth > 0.dp && maxWidth != Dp.Infinity) {
            maxWidth
        } else {
            fallbackAvailableWidth
        }
        val cardWidth = remember(availableWidth, isPortraitRow) { calculateCardWidth(availableWidth, isPortraitRow) }
        val aspectRatioValue = if (isPortraitRow) PortraitAspectRatio else LandscapeAspectRatio
        val widthPx = remember(cardWidth, density) { with(density) { cardWidth.toPx() }.toInt() }
        val heightPx = remember(widthPx, aspectRatioValue) { (widthPx / aspectRatioValue).toInt() }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = row.cate,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(start = RowStartPadding, end = RowEndPadding),
                    horizontalArrangement = Arrangement.spacedBy(CardSpacing)
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
                                if (rowPosition != null) {
                                    TvFocusManager.rowFocusRequesters[rowPosition] = cardFocusRequesters[index]
                                }
                            }
                        }
                        val handleDeleteCard: () -> Unit = remember(index, ids, cardFocusRequesters, rowPosition, hasCarousel, onDelete, onDeleteLastCard, id) {
                            {
                                if (index + 1 < ids.size) {
                                    // 1. Right side has card -> focus it
                                    val targetId = ids[index + 1]
                                    pendingFocusTargetId = targetId
                                    onDelete?.invoke(id)
                                } else if (index > 0) {
                                    // 2. Else in left side -> focus it
                                    val targetId = ids[index - 1]
                                    pendingFocusTargetId = targetId
                                    onDelete?.invoke(id)
                                } else {
                                    // 3. Those card are last -> focus on above row card
                                    if (onDeleteLastCard != null) {
                                        onDeleteLastCard.invoke(rowPosition ?: 0)
                                    } else {
                                        if (rowPosition != null && rowPosition > 0) {
                                            val aboveRequester = TvFocusManager.rowFocusRequesters[rowPosition - 1]
                                            if (!TvFocusManager.requestFocus(aboveRequester)) {
                                                if (hasCarousel) {
                                                    TvFocusManager.requestFocus(TvFocusManager.carousel)
                                                }
                                            }
                                        } else if (hasCarousel) {
                                            TvFocusManager.requestFocus(TvFocusManager.carousel)
                                        }
                                    }
                                    onDelete?.invoke(id)
                                }
                            }
                        }
                        HomeMediaCard(
                            id = id,
                            imageUrl = resolveCardImageUrl(imageTemplate, id),
                            imgReferer = imgReferer,
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
                            onDelete = handleDeleteCard,
                            appNavController = appNavController
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMediaCard(
    id: String,
    imageUrl: String,
    imgReferer: String? = null,
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
    LaunchedEffect(id, rowPosition, focusRequester) {
        if (!TvFocusManager.isUpdateDialogOpen &&
            TvFocusManager.lastFocusedHomeCardId == id &&
            (TvFocusManager.lastFocusedHomeRowPosition == null || TvFocusManager.lastFocusedHomeRowPosition == rowPosition)
        ) {
            TvFocusManager.lastFocusedContentRequester = focusRequester
            repeat(8) { attempt ->
                val focused = runCatching { focusRequester.requestFocus() }.getOrDefault(false)
                if (focused) return@LaunchedEffect
                delay(40L * (attempt + 1))
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME, lifecycleOwner) {
        if (!TvFocusManager.isUpdateDialogOpen &&
            TvFocusManager.lastFocusedHomeCardId == id &&
            (TvFocusManager.lastFocusedHomeRowPosition == null || TvFocusManager.lastFocusedHomeRowPosition == rowPosition)
        ) {
            TvFocusManager.lastFocusedContentRequester = focusRequester
            coroutineScope.launch {
                repeat(10) { attempt ->
                    val focused = runCatching { focusRequester.requestFocus() }.getOrDefault(false)
                    if (focused) return@launch
                    delay(30L * (attempt + 1))
                }
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

    var lastFocusGainTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(aspectRatio)
    ) {

        Card(
            onClick = {
                // Ignore accidental clicks if this card gained focus within the last 400ms (e.g. from deleting adjacent card)
                if (SystemClock.uptimeMillis() - lastFocusGainTime < 400L) {
                    return@Card
                }
                Log.d("HomeMediaRows", "Clicked card ID: $id")
                onFocused()
                appNavController.navigate(Routes.details(id))
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        lastFocusGainTime = SystemClock.uptimeMillis()
                        onFocused()
                    }
                }
                .onPreviewKeyEvent { event ->
                    // Consume stray KeyUp events for Center / Enter if card gained focus very recently
                    if (event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                        SystemClock.uptimeMillis() - lastFocusGainTime < 400L
                    ) {
                        return@onPreviewKeyEvent true
                    }
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
                    model = remember(imageUrl, widthPx, heightPx, imgReferer) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .size(Size(widthPx, heightPx))
                            .precision(Precision.INEXACT) // lets Coil reuse similarly-sized cached bitmaps instead of always decoding exact-fit
                            .allowRgb565(true) // ~half the memory + decode time of ARGB_8888; posters don't need alpha/full color depth
                            .apply {
                                if (!imgReferer.isNullOrBlank()) {
                                    setHeader("Referer", imgReferer)
                                }
                                setHeader("X-Requested-With", "NetmirrorNewTV v1.0")
                            }
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
                        when {
                            event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                true
                            }
                            event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                onDelete?.invoke()
                                true
                            }
                            event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionLeft || event.key == Key.Back) -> {
                                focusRequester.requestFocus()
                                true
                            }
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionRight -> {
                                if (nextCardFocusRequester != null) {
                                    nextCardFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown -> {
                                focusRequester.requestFocus()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDelete?.invoke() }
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
                    .offset(x = (14).dp, y = (-14).dp)
                    .height(44.dp)
                    .zIndex(2f)
            )
        }
    }
}