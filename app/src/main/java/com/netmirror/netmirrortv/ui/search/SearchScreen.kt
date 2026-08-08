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

private val RowCardShape = RoundedCornerShape(8.dp)
private val CardBorderColor = Color.White
private val CardContainerColor = Color(0xFF1A1A1A)
private val CardFocusedContainerColor = Color(0xFF262626)
private const val CardFocusedScale = 1.1f

// ---------------------------------------------------------------------------

@Composable
fun SearchScreen(
    onItemClick: (SearchItem) -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    val loading by viewModel.loading.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val error by viewModel.error.collectAsState()

    var query by remember { mutableStateOf("") }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }

    TvFocusManager.currentScreenContentRequester = searchFieldFocusRequester

    // Load "Top Searches" as soon as the screen opens. Passing "" here is
    // intentional and safe: SearchRepository converts a blank query to null
    // before calling the API, so the "s" param is omitted from the request
    // and the backend returns Top Searches.
    LaunchedEffect(Unit) {
        viewModel.search("")
        searchFieldFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 100.dp,top = 30.dp, bottom = 30.dp)
    ) {
        SearchField(
            query = query,
            onQueryChange = {
                query = it
                // "" (field cleared) is intentionally forwarded as-is; the
                // repository turns it into a null "s" param -> Top Searches.
                viewModel.search(it)
            },
            focusRequester = searchFieldFocusRequester,
            onDownPressed = {
                runCatching { firstCardFocusRequester.requestFocus() }.isSuccess
            }
        )

        Spacer(Modifier.height(28.dp))

        val results = searchResult?.searchResult.orEmpty()

        when {
            loading && results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = error ?: "Something went wrong",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) "No results" else "No results found for \"$query\"",
                        color = Color.White.copy(alpha = 0.7f)
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

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 7.dp, end = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                        SearchResultCard(
                            item = item,
                            imageUrl = buildImageUrl(imgTemplate, item.id),
                            isFirstInRow = index == 0,
                            focusRequester = if (index == 0) firstCardFocusRequester else null,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
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
    isFirstInRow: Boolean,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val cardFocusRequester = focusRequester ?: remember { FocusRequester() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenWidthDp < configuration.screenHeightDp

    val cardWidth = if (isPortrait) 110.dp else 200.dp
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

            // Show the title when we have one. Top Searches items with no
            // title just show the image with no label overlay.
            val labelText = item.title?.takeIf { it.isNotBlank() }

            if (labelText != null) {
                Text(
                    text = labelText,
                    maxLines = 1,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black)
                            )
                        )
                        .padding(6.dp)
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