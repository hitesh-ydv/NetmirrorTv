package com.netmirror.netmirrortv.ui.home

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
import androidx.compose.ui.focus.focusRestorer
import com.netmirror.netmirrortv.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.navigation.Routes

private val RowCardShape = RoundedCornerShape(5)
private val PosterPlaceholder = Regex("-{3,}")
private const val CardFocusedScale = 1.04f
private val CardBorderColor = Color.White
private val CardFocusedContainerColor = Color(0xFF303030)
private val CardContainerColor = Color(0xFF202020)

internal val PortraitCardWidth = 140.dp
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
    appNavController: NavHostController,
    onFirstCardRequester: ((FocusRequester) -> Unit)? = null, // callback for the first card's requester
) {
    val ids = remember(row.ids) {
        row.ids.split(',').map(String::trim).filter(String::isNotEmpty)
    }
    if (ids.isEmpty()) return

    val isPortraitRow = row.row.equals("v", ignoreCase = true)
    val imageTemplate = if (isPortraitRow) verticalImageTemplate else horizontalImageTemplate

    val cardFocusRequesters = remember(ids) { ids.map { FocusRequester() } }
    var lastFocusedIndex by remember(ids) { mutableIntStateOf(0) }

    val isTop10Row = row.cate.contains("top 10", ignoreCase = true)

    // If this is the first row (rowPosition == 0), report the first card's requester
    LaunchedEffect(rowPosition, cardFocusRequesters) {
        if (rowPosition == 0 && cardFocusRequesters.isNotEmpty()) {
            onFirstCardRequester?.invoke(cardFocusRequesters.first())
        }
    }

    val rowState = rememberLazyListState()

    // Sizing is identical for every card in this row — compute it once here
    // instead of re-deriving px sizes (and re-triggering density lookups)
    // inside every single HomeMediaCard on every recomposition.
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
                key = { _, id -> id },
                contentType = { _, _ -> if (isPortraitRow) "portrait" else "landscape" },
            ) { index, id ->
                // Stable across recompositions of this slot (only changes if index/requester
                // identity changes), so HomeMediaCard can be skipped when nothing it reads changed.
                val onFocused = remember(index, cardFocusRequesters) {
                    {
                        lastFocusedIndex = index
                        TvFocusManager.lastFocusedContentRequester = cardFocusRequesters[index]
                    }
                }
                HomeMediaCard(
                    id = id,
                    imageUrl = resolveCardImageUrl(imageTemplate, id),
                    isPortrait = isPortraitRow,
                    isTop10 = isTop10Row,
                    rank = index + 1,
                    isFirstInRow = index == 0,
                    cardWidth = cardWidth,
                    aspectRatio = aspectRatioValue,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    border = cardBorder,
                    colors = cardColors,
                    scale = cardScale,
                    shape = cardShape,
                    modifier = Modifier.focusRequester(cardFocusRequesters[index]),
                    onFocused = onFocused,
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
    rank: Int,
    isFirstInRow: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    aspectRatio: Float,
    widthPx: Int,
    heightPx: Int,
    border: CardBorder,
    colors: CardColors,
    scale: CardScale,
    shape: CardShape,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    appNavController: NavHostController,
) {
    val context = LocalContext.current

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
        modifier = modifier
            .width(cardWidth)
            .aspectRatio(aspectRatio)
    ) {

        Card(
            onClick = {
                appNavController.navigate(Routes.details(id.toInt()))
            },
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    if (
                        isFirstInRow &&
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft
                    ) {
                        TvFocusManager.moveFocusToDrawer()
                        true
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

                // Bottom Gradient
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