package com.netmirror.netmirrortv.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    appNavController: NavHostController,
) {
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
                TvFocusManager.moveFocusToContent()
            },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    TvFocusManager.moveFocusToContent()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

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
    val firstCardFocusRequester = remember(homeResponse) { FocusRequester() }
    val validPosts = remember(homeResponse.post) {
        homeResponse.post.filter { row ->
            row.ids.split(',').any { it.trim().isNotEmpty() }
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val prefetchPortraitWidthDp = remember(screenWidth) { calculateCardWidthForScreen(screenWidth, isPortrait = true) }
    val prefetchLandscapeWidthDp = remember(screenWidth) { calculateCardWidthForScreen(screenWidth, isPortrait = false) }
    val hasCarousel = homeResponse.slider.isNotEmpty()
    val sliderItemOffset = if (hasCarousel) 1 else 0

    val selectedPage by viewModel.page.collectAsState()
    val selectedOtt by com.netmirror.netmirrortv.utils.AppSession.selectedOtt.collectAsState()

    var lastPage by rememberSaveable { mutableStateOf(selectedPage) }
    var lastOtt by rememberSaveable { mutableStateOf(selectedOtt) }

    TvFocusManager.firstCardRequester = firstCardFocusRequester
    if (hasCarousel) {
        TvFocusManager.currentScreenContentRequester = TvFocusManager.carousel
    } else {
        TvFocusManager.currentScreenContentRequester = firstCardFocusRequester
    }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(listState, hasCarousel, firstCardFocusRequester) {
        TvFocusManager.firstCardRequester = firstCardFocusRequester
        if (hasCarousel) {
            TvFocusManager.currentScreenContentRequester = TvFocusManager.carousel
        } else {
            TvFocusManager.currentScreenContentRequester = firstCardFocusRequester
        }
        fun doFocusVisible(): Boolean {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            for (item in visibleItems) {
                if (hasCarousel && item.index == 0) {
                    if (TvFocusManager.requestFocus(TvFocusManager.carousel)) return true
                } else {
                    val rowPos = if (hasCarousel) item.index - 1 else item.index
                    val req = TvFocusManager.rowFocusRequesters[rowPos]
                    if (req != null && TvFocusManager.requestFocus(req)) return true
                }
            }
            if (TvFocusManager.lastFocusedHomeCardId != null &&
                TvFocusManager.requestFocus(TvFocusManager.lastFocusedContentRequester)
            ) {
                return true
            }
            for (pos in TvFocusManager.rowFocusRequesters.keys.sorted()) {
                val req = TvFocusManager.rowFocusRequesters[pos]
                if (req != null && TvFocusManager.requestFocus(req)) return true
            }
            if (TvFocusManager.requestFocus(firstCardFocusRequester)) return true
            if (hasCarousel && TvFocusManager.requestFocus(TvFocusManager.carousel)) return true
            return false
        }

        TvFocusManager.focusVisibleContent = ::doFocusVisible

        onDispose {
            if (TvFocusManager.focusVisibleContent != null) {
                TvFocusManager.focusVisibleContent = null
            }
            if (TvFocusManager.firstCardRequester == firstCardFocusRequester) {
                TvFocusManager.firstCardRequester = null
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME, lifecycleOwner) {
        TvFocusManager.firstCardRequester = firstCardFocusRequester
        if (hasCarousel) {
            TvFocusManager.currentScreenContentRequester = TvFocusManager.carousel
        } else {
            TvFocusManager.currentScreenContentRequester = firstCardFocusRequester
        }

        if (TvFocusManager.isCarouselLastFocused) {
            coroutineScope.launch {
                repeat(10) { attempt ->
                    if (TvFocusManager.requestFocus(TvFocusManager.carousel)) return@launch
                    kotlinx.coroutines.delay(30L * (attempt + 1))
                }
            }
        } else if (TvFocusManager.lastFocusedHomeCardId != null) {
            coroutineScope.launch {
                repeat(10) { attempt ->
                    if (TvFocusManager.requestFocus(TvFocusManager.lastFocusedContentRequester)) return@launch
                    kotlinx.coroutines.delay(30L * (attempt + 1))
                }
            }
        }
    }

    // When switching page or OTT provider: scroll to top index and reset focus
    LaunchedEffect(selectedPage, selectedOtt) {
        if (selectedPage != lastPage || selectedOtt != lastOtt) {
            lastPage = selectedPage
            lastOtt = selectedOtt
            listState.scrollToItem(0)
            TvFocusManager.lastFocusedContentRequester = null
            TvFocusManager.lastFocusedHomeCardId = null
            TvFocusManager.lastFocusedHomeRowPosition = null
            TvFocusManager.isCarouselLastFocused = false
            TvFocusManager.firstCardRequester = firstCardFocusRequester
            if (hasCarousel) {
                TvFocusManager.currentScreenContentRequester = TvFocusManager.carousel
            } else {
                TvFocusManager.currentScreenContentRequester = firstCardFocusRequester
            }
        }
    }

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
                val cardWidthDp = if (isPortraitRow) prefetchPortraitWidthDp else prefetchLandscapeWidthDp
                val aspectRatioValue = if (isPortraitRow) PortraitAspectRatio else LandscapeAspectRatio
                val widthPx = with(density) { cardWidthDp.toPx() }.toInt()
                val heightPx = (widthPx / aspectRatioValue).toInt()

                val imageLoader = context.imageLoader
                val referer = homeResponse.imgReferer
                row.ids.split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .take(6)
                    .forEach { id ->
                        val url = resolveCardImageUrl(template, id)
                        if (url.isNotEmpty()) {
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(Size(widthPx, heightPx))
                                    .precision(Precision.INEXACT)
                                    .apply {
                                        if (!referer.isNullOrBlank()) {
                                            setHeader("Referer", referer)
                                        }
                                        setHeader("X-Requested-With", "NetmirrorNewTV v1.0")
                                    }
                                    .allowRgb565(true)
                                    .build()
                            )
                        }
                    }
            }
    }

    val screenHeight = configuration.screenHeightDp.dp

    CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = screenHeight * 0.5f),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(start = 100.dp)
        ) {
            if (hasCarousel) {
                item(key = "home-carousel", contentType = "carousel") {
                    HomeCarousel(
                        slider = homeResponse.slider,
                        imgReferer = homeResponse.imgReferer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight * 0.85f),
                        onPlay = {},
                        firstCardRequester = firstCardFocusRequester,
                        onDownPress = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(1)
                                repeat(8) { attempt ->
                                    val rowZeroRequester = TvFocusManager.rowFocusRequesters[0]
                                    val focused = (TvFocusManager.focusFirstRow?.invoke() == true) ||
                                            TvFocusManager.requestFocus(rowZeroRequester) ||
                                            TvFocusManager.requestFocus(firstCardFocusRequester) ||
                                            TvFocusManager.requestFocus(TvFocusManager.firstCardRequester)
                                    if (focused) return@launch
                                    delay(30L * (attempt + 1))
                                }
                            }
                        },
                        appNavController = appNavController
                    )
                }
            }

            validPosts.forEachIndexed { index, row ->
                item(
                    key = row.cate.ifEmpty { "${row.row}_$index" },
                    contentType = row.row
                ) {
                    HomeMediaRow(
                        row = row,
                        rowPosition = index,
                        hasCarousel = hasCarousel,
                        imgReferer = homeResponse.imgReferer,
                        horizontalImageTemplate = homeResponse.imageCdnHorizontal ?: homeResponse.imageCdn,
                        verticalImageTemplate = homeResponse.imageCdnVertical ?: homeResponse.imageCdn,
                        appNavController = appNavController,
                        firstCardFocusRequester = if (index == 0) firstCardFocusRequester else null,
                        onFirstCardRequester = if (index == 0) { requester ->
                            TvFocusManager.firstCardRequester = requester
                            if (!hasCarousel) {
                                TvFocusManager.currentScreenContentRequester = requester
                            }
                        } else null,
                        onDelete = { deleteId ->
                            val isWatchlist = row.cate.startsWith("Watchlist", ignoreCase = true) ||
                                    row.cate.contains("Watchlist", ignoreCase = true) ||
                                    row.cate.startsWith("My List", ignoreCase = true) ||
                                    row.cate.contains("My List", ignoreCase = true)
                            if (isWatchlist) {
                                viewModel.deleteWatchlistItem(deleteId)
                            } else {
                                viewModel.deleteContinueWatchingItem(deleteId)
                            }
                        },
                        onDeleteLastCard = { rowPos ->
                            coroutineScope.launch {
                                if (rowPos > 0) {
                                    val targetListIndex = if (hasCarousel) rowPos else (rowPos - 1)
                                    listState.animateScrollToItem(targetListIndex.coerceAtLeast(0))
                                    repeat(10) { attempt ->
                                        val requester = TvFocusManager.rowFocusRequesters[rowPos - 1]
                                        if (requester != null && TvFocusManager.requestFocus(requester)) return@launch
                                        delay(30L * (attempt + 1))
                                    }
                                } else if (hasCarousel) {
                                    listState.animateScrollToItem(0)
                                    repeat(10) { attempt ->
                                        if (TvFocusManager.requestFocus(TvFocusManager.carousel)) return@launch
                                        delay(30L * (attempt + 1))
                                    }
                                } else {
                                    repeat(10) { attempt ->
                                        if (TvFocusManager.requestFocus(TvFocusManager.firstCardRequester)) return@launch
                                        delay(30L * (attempt + 1))
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}