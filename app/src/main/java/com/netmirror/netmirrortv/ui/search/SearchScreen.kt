package com.netmirror.netmirrortv.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.netmirror.netmirrortv.model.SearchItem
import com.netmirror.netmirrortv.utils.TvFocusManager

import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.navigation.Routes
import com.netmirror.netmirrortv.ui.components.AppToast
import kotlinx.coroutines.delay

private val RowCardShape = RoundedCornerShape(8.dp)
private val CardBorderColor = Color.White
private val CardContainerColor = Color(0xFF1A1A1A)
private val CardFocusedContainerColor = Color(0xFF262626)
private const val CardFocusedScale = 1.04f

// ---------------------------------------------------------------------------

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

    var query by remember { mutableStateOf("") }
    val searchFieldFocusRequester = remember { FocusRequester() }

    TvFocusManager.currentScreenContentRequester = searchFieldFocusRequester

    val results = searchResult?.searchResult.orEmpty()
    val cardFocusRequesters = remember(results) { results.map { FocusRequester() } }

    // Restore focus to clicked search card by ID when returning from details screen
    LaunchedEffect(results) {
        val lastCardId = TvFocusManager.lastFocusedSearchCardId
        if (!lastCardId.isNullOrEmpty() && results.isNotEmpty()) {
            val targetIndex = results.indexOfFirst { it.id == lastCardId }
            if (targetIndex != -1 && targetIndex < cardFocusRequesters.size) {
                val requester = cardFocusRequesters[targetIndex]
                TvFocusManager.lastFocusedContentRequester = requester
                repeat(5) { attempt ->
                    val focused = runCatching { requester.requestFocus() }.getOrDefault(false)
                    if (focused) return@LaunchedEffect
                    delay(50L * (attempt + 1))
                }
            } else {
                searchFieldFocusRequester.requestFocus()
            }
        } else {
            searchFieldFocusRequester.requestFocus()
        }
    }

    // 5-second debouncing on text input. Blank queries trigger immediately.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            viewModel.search("")
        } else {
            delay(3000L)
            viewModel.search(query)
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
                    runCatching { cardFocusRequesters.firstOrNull()?.requestFocus() }.isSuccess
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = if (query.isBlank()) "No results" else "No results found for \"$query\"",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                else -> {
                    val heading = searchResult?.head
                        ?: if (query.isBlank()) "Top Searches" else "Results"

                    Text(
                        text = heading,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(16.dp))

                    val imgTemplate = searchResult?.imgcdn.orEmpty()
                    val isPortraitRow = searchResult?.row.equals("v", ignoreCase = true)

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 7.dp, end = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                            val cardRequester = cardFocusRequesters.getOrNull(index)
                            SearchResultCard(
                                item = item,
                                imageUrl = buildImageUrl(imgTemplate, item.id),
                                isPortrait = isPortraitRow,
                                isFirstInRow = index == 0,
                                focusRequester = cardRequester,
                                onClick = {
                                    if (cardRequester != null) {
                                        TvFocusManager.lastFocusedContentRequester = cardRequester
                                        TvFocusManager.lastFocusedSearchCardId = item.id
                                    }
                                    onItemClick(item)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            AppToast(
                title = "Network Error",
                message = "Search Page Loading Fail, Retry after some times.",
                durationMs = 6000L,
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
                    tint = Color.White.copy(alpha = 0.7f)
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
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        TvFocusManager.lastFocusedContentRequester = focusRequester
                    }
                }
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
    isPortrait: Boolean,
    isFirstInRow: Boolean,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val cardFocusRequester = focusRequester ?: remember { FocusRequester() }
    val context = LocalContext.current
    val density = LocalDensity.current

    val cardWidth = if (isPortrait) 150.dp else 200.dp
    val aspectRatio = if (isPortrait) 2f / 3f else 16f / 9f

    val widthPx = with(density) { cardWidth.toPx() }.toInt()
    val heightPx = (widthPx / aspectRatio).toInt()

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(aspectRatio)
            .focusRequester(cardFocusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    TvFocusManager.lastFocusedContentRequester = cardFocusRequester
                    onFocused()
                }
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
        Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(Size(widthPx, heightPx))
                    .crossfade(false)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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