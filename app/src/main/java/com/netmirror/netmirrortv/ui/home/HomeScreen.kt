package com.netmirror.netmirrortv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.netmirror.netmirrortv.utils.TvFocusManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    appNavController: NavHostController,
) {
    val loading by viewModel.loading.collectAsState()
    val home by viewModel.home.collectAsState()

    if (loading || home == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }
    val homeResponse = home ?: return

    // Holds the FocusRequester of the first card of the first row
    val firstCardRequester = remember { mutableStateOf<FocusRequester?>(null) }

    // Request focus on carousel initially, or restore the last focused card if present
    LaunchedEffect(homeResponse.slider) {
        if (homeResponse.slider.isNotEmpty()) {
            TvFocusManager.currentScreenContentRequester = TvFocusManager.carousel
            TvFocusManager.carousel.requestFocus()
        } else {
            TvFocusManager.currentScreenContentRequester = firstCardRequester.value
        }
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val sliderItemOffset = if (homeResponse.slider.isNotEmpty()) 1 else 0

    // Warm Coil's cache for the row just below what's currently visible, *before* the user
    // scrolls/focuses into it. Without this, the moment a new row's LazyRow first composes,
    // every AsyncImage in it fires a fresh network+decode request at once — that burst is
    // what causes the vertical focus jerk on low-end TVs. Horizontal focus is smooth because
    // by then those cards are already composed and their images already resolved.
    LaunchedEffect(listState, homeResponse.post) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { lastVisibleItemIndex ->
                val nextRowIndex = lastVisibleItemIndex - sliderItemOffset + 1
                val row = homeResponse.post.getOrNull(nextRowIndex) ?: return@collect

                val isPortraitRow = row.row.equals("v", ignoreCase = true)
                val template = if (isPortraitRow) {
                    homeResponse.imageCdnVertical ?: homeResponse.imageCdn
                } else {
                    homeResponse.imageCdnHorizontal ?: homeResponse.imageCdn
                }
                val cardWidthDp = if (isPortraitRow) PortraitCardWidth else LandscapeCardWidth
                val aspectRatioValue = if (isPortraitRow) PortraitAspectRatio else LandscapeAspectRatio
                val widthPx = with(density) { cardWidthDp.toPx() }.toInt()
                val heightPx = (widthPx / aspectRatioValue).toInt()

                val imageLoader = context.imageLoader
                row.ids.split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .take(6) // only the cards likely to be on/near screen when the row lands
                    .forEach { id ->
                        val url = resolveCardImageUrl(template, id)
                        if (url.isNotEmpty()) {
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(Size(widthPx, heightPx))
                                    .precision(Precision.INEXACT)
                                    .allowRgb565(true)
                                    .build()
                            )
                        }
                    }
            }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 100.dp)
    ) {
        if (homeResponse.slider.isNotEmpty()) {
            item(key = "home-carousel", contentType = "carousel") {
                HomeCarousel(
                    slider = homeResponse.slider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.8f),
                    onPlay = {},
                    firstCardRequester = firstCardRequester.value
                )
            }
        }
        itemsIndexed(
            items = homeResponse.post,
            key = { _, row -> "${row.row}-${row.cate}" },
            contentType = { _, row -> row.row },
        ) { index, row ->
            // Stabilize the lambda per row index to avoid unnecessary recomposition
            val onFirstCardRequester = remember(index) {
                if (index == 0) { requester: FocusRequester ->
                    firstCardRequester.value = requester
                } else null
            }
            HomeMediaRow(
                row = row,
                rowPosition = index,
                horizontalImageTemplate = homeResponse.imageCdnHorizontal ?: homeResponse.imageCdn,
                verticalImageTemplate = homeResponse.imageCdnVertical ?: homeResponse.imageCdn,
                appNavController = appNavController,
                onFirstCardRequester = onFirstCardRequester,
            )
        }
    }
}