package com.netmirror.netmirrortv.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.netmirror.netmirrortv.model.SearchItem
import com.netmirror.netmirrortv.utils.Constants
import com.netmirror.netmirrortv.utils.TvFocusManager
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.ui.home.CardSpacing
import com.netmirror.netmirrortv.ui.home.LandscapeAspectRatio
import com.netmirror.netmirrortv.ui.home.PortraitAspectRatio
import com.netmirror.netmirrortv.ui.home.RowEndPadding
import com.netmirror.netmirrortv.ui.home.RowStartPadding
import com.netmirror.netmirrortv.ui.home.calculateCardWidth

import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.navigation.Routes
import com.netmirror.netmirrortv.ui.components.AppToast
import kotlinx.coroutines.delay

private val RowCardShape = RoundedCornerShape(8.dp)
private val CardBorderColor = Color(0xFFE50914)
private val CardContainerColor = Color(0xFF1A1A1A)
private val CardFocusedContainerColor = Color(0xFF262626)
private const val CardFocusedScale = 1.04f

// Centers the focused card in the row viewport
@OptIn(ExperimentalFoundationApi::class)
private object CenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val itemCenter = offset + size / 2f
        val containerCenter = containerSize / 2f
        return itemCenter - containerCenter
    }
}

// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    appNavController: NavHostController? = null,
    onItemClick: (SearchItem) -> Unit = { item ->
        appNavController?.navigate(Routes.details(item.id))
    },
    viewModel: SearchViewModel = viewModel()
) {
    val loading by viewModel.loading.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val error by viewModel.error.collectAsState()

    val selectedOtt by com.netmirror.netmirrortv.utils.AppSession.selectedOtt.collectAsState()
    var lastOtt by rememberSaveable { mutableStateOf(selectedOtt) }

    var query by rememberSaveable { mutableStateOf("") }
    var lastSearchedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val rowState = rememberLazyListState()

    TvFocusManager.currentScreenContentRequester = searchFieldFocusRequester

    val results = searchResult?.searchResult.orEmpty()
    val resultError = searchResult?.error.orEmpty()
    val cardFocusRequesters = remember(results) { results.map { FocusRequester() } }
    var lastFocusedIndex by remember(results) { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Initial focus on search field only when there is no saved focused card
    LaunchedEffect(Unit) {
        if (TvFocusManager.lastFocusedSearchCardId == null) {
            repeat(5) { attempt ->
                val focused = runCatching { searchFieldFocusRequester.requestFocus() }.getOrDefault(false)
                if (focused) return@LaunchedEffect
                delay(50L * (attempt + 1))
            }
        }
    }

    // When OTT changes (like Navbar and Home), reload search results for the current query
    LaunchedEffect(selectedOtt) {
        if (selectedOtt != lastOtt) {
            lastOtt = selectedOtt
            lastSearchedQuery = query
            TvFocusManager.lastFocusedSearchCardId = null
            runCatching { rowState.scrollToItem(0) }
            viewModel.search(query)
        }
    }

    // Debouncing on text input: wait 3.5s after typing stops before triggering search with loader.
    LaunchedEffect(query) {
        if (query != lastSearchedQuery || searchResult == null) {
            if (query.isBlank()) {
                lastSearchedQuery = ""
                viewModel.search("")
            } else {
                delay(3500L)
                lastSearchedQuery = query
                viewModel.search(query)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(start = 100.dp, top = 30.dp, bottom = 30.dp)
        ) {
            SearchField(
                query = query,
                onQueryChange = {
                    query = it
                    TvFocusManager.lastFocusedSearchCardId = null
                },
                focusRequester = searchFieldFocusRequester,
                onDownPressed = {
                    val lastReq = cardFocusRequesters.getOrNull(lastFocusedIndex)
                    if (lastReq != null && runCatching { lastReq.requestFocus() }.isSuccess) {
                        true
                    } else {
                        val visibleIndex = rowState.firstVisibleItemIndex
                        val visibleReq = cardFocusRequesters.getOrNull(visibleIndex)
                        if (visibleReq != null && runCatching { visibleReq.requestFocus() }.isSuccess) {
                            true
                        } else if (cardFocusRequesters.isNotEmpty() && runCatching { cardFocusRequesters.first().requestFocus() }.isSuccess) {
                            true
                        } else if (cardFocusRequesters.isNotEmpty()) {
                            coroutineScope.launch {
                                rowState.scrollToItem(0)
                                repeat(6) {
                                    if (cardFocusRequesters.firstOrNull()?.let { runCatching { it.requestFocus() }.isSuccess } == true) return@launch
                                    delay(30L)
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
            )

            Spacer(Modifier.height(28.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = error ?: "Search Page Loading Fail, Retry after some times.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                results.isEmpty() -> {
                    val displayedQuery = lastSearchedQuery ?: query
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = if (displayedQuery.isBlank()) resultError else "No results found for \"$displayedQuery\"",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                else -> {
                    val heading = searchResult?.head
                        ?: if ((lastSearchedQuery ?: query).isBlank()) "Top Searches" else "Results"

                    Text(
                        text = heading,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(16.dp))

                    val imgTemplate = searchResult?.imgcdn.orEmpty()
                    val imgReferer = searchResult?.imgReferer
                    val isPortraitRow = searchResult?.row.equals("v", ignoreCase = true)

                    val density = LocalDensity.current
                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val fallbackAvailableWidth = screenWidth - 100.dp

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val availableWidth = if (maxWidth > 0.dp && maxWidth != Dp.Infinity) {
                            maxWidth
                        } else {
                            fallbackAvailableWidth
                        }
                        val cardWidth = remember(availableWidth, isPortraitRow) { calculateCardWidth(availableWidth, isPortraitRow) }
                        val aspectRatioValue = if (isPortraitRow) PortraitAspectRatio else LandscapeAspectRatio
                        val widthPx = remember(cardWidth, density) { with(density) { cardWidth.toPx() }.toInt() }
                        val heightPx = remember(widthPx, aspectRatioValue) { (widthPx / aspectRatioValue).toInt() }

                        CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
                            LazyRow(
                                state = rowState,
                                horizontalArrangement = Arrangement.spacedBy(CardSpacing),
                                contentPadding = PaddingValues(start = RowStartPadding, end = RowEndPadding),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    items = results,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> if (isPortraitRow) "portrait" else "landscape" }
                                ) { index, item ->
                                    val cardRequester = cardFocusRequesters.getOrNull(index)
                                    SearchResultCard(
                                        item = item,
                                        imageUrl = buildImageUrl(imgTemplate, item.id),
                                        imgReferer = imgReferer,
                                        isPortrait = isPortraitRow,
                                        isFirstInRow = index == 0,
                                        cardWidth = cardWidth,
                                        aspectRatio = aspectRatioValue,
                                        widthPx = widthPx,
                                        heightPx = heightPx,
                                        focusRequester = cardRequester,
                                        onFocused = {
                                            lastFocusedIndex = index
                                        },
                                        onUpPressed = {
                                            searchFieldFocusRequester.requestFocus()
                                        },
                                        onClick = {
                                            TvFocusManager.lastFocusedContentRequester = cardRequester
                                            TvFocusManager.lastFocusedSearchCardId = item.id
                                            TvFocusManager.lastFocusedHomeCardId = null
                                            TvFocusManager.lastFocusedHomeRowPosition = null
                                            TvFocusManager.isCarouselLastFocused = false
                                            onItemClick(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (error != null) {
            AppToast(
                title = "Network Error",
                message = "Search Page Loading Fail, Retry after some times.",
                durationMs = 60000L,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onDownPressed: () -> Boolean = { false }
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.padding(end = 24.dp)
    ){
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text("Search movies & shows", color = Color.White.copy(alpha = 0.5f))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isFocused) CardBorderColor else Color.White.copy(alpha = 0.7f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF141414),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CardBorderColor,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        TvFocusManager.lastFocusedContentRequester = focusRequester
                    }
                }
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) CardBorderColor else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .onPreviewKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            onDownPressed()
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                            TvFocusManager.moveFocusToDrawer()
                            true
                        }
                        else -> false
                    }
                }
        )
    }
}

@Composable
private fun SearchResultCard(
    item: SearchItem,
    imageUrl: String,
    imgReferer: String? = null,
    isPortrait: Boolean,
    isFirstInRow: Boolean,
    cardWidth: Dp,
    aspectRatio: Float,
    widthPx: Int,
    heightPx: Int,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onUpPressed: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val cardFocusRequester = focusRequester ?: remember { FocusRequester() }
    val context = LocalContext.current

    // Restore focus to this card if it was the last focused card when navigating back from DetailsScreen
    LaunchedEffect(item.id) {
        if (TvFocusManager.lastFocusedSearchCardId == item.id) {
            repeat(5) { attempt ->
                val focused = runCatching { cardFocusRequester.requestFocus() }.getOrDefault(false)
                if (focused) return@LaunchedEffect
                delay(50L * (attempt + 1))
            }
        }
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(aspectRatio)
    ) {
        Card(
            onClick = {
                TvFocusManager.lastFocusedContentRequester = cardFocusRequester
                TvFocusManager.lastFocusedSearchCardId = item.id
                TvFocusManager.lastFocusedHomeCardId = null
                TvFocusManager.lastFocusedHomeRowPosition = null
                TvFocusManager.isCarouselLastFocused = false
                onClick()
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(cardFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        TvFocusManager.lastFocusedContentRequester = cardFocusRequester
                        TvFocusManager.lastFocusedSearchCardId = item.id
                        TvFocusManager.lastFocusedHomeCardId = null
                        TvFocusManager.lastFocusedHomeRowPosition = null
                        TvFocusManager.isCarouselLastFocused = false
                        onFocused()
                    }
                }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        isFirstInRow && event.key == Key.DirectionLeft -> {
                            TvFocusManager.moveFocusToDrawer()
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            onUpPressed?.invoke()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
            shape = CardDefaults.shape(shape = RowCardShape),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 3.dp, color = CardBorderColor),
                    shape = RowCardShape,
                ),
            ),
            colors = CardDefaults.colors(
                containerColor = CardContainerColor,
                focusedContainerColor = CardFocusedContainerColor,
            ),
            scale = CardDefaults.scale(focusedScale = CardFocusedScale),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = remember(imageUrl, widthPx, heightPx, imgReferer) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .size(Size(widthPx, heightPx))
                            .precision(Precision.INEXACT)
                            .allowRgb565(true)
                            .apply {
                                if (!imgReferer.isNullOrBlank()) {
                                    setHeader("Referer", imgReferer)
                                }
                                setHeader("X-Requested-With", Constants.X_REQUESTED_WITH)
                            }
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun buildImageUrl(template: String, id: String): String {
    if (template.isEmpty()) return ""

    val placeholder = Regex("-{4,}").find(template)?.value

    return if (placeholder != null) {
        template.replaceFirst(placeholder, id)
    } else {
        template
    }
}