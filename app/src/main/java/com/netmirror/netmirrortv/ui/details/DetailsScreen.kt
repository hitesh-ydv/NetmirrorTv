package com.netmirror.netmirrortv.ui.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netmirror.netmirrortv.navigation.Routes
import com.yourapp.data.model.TitleDetailsResponse
import com.yourapp.data.model.Episode
import com.yourapp.data.model.Season
import kotlinx.coroutines.launch

private val AccentGreen = Color(0xFF46D369)
private val FocusGlow = Color(0x66FFFFFF) // semi‑transparent white glow

// Disables Compose's built-in "scroll focused item into view" behavior so that
// ONLY our manual scrollState.animateScrollTo(...) calls control page scrolling.
@OptIn(ExperimentalFoundationApi::class)
private object NoOpBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
fun DetailsScreen(
    navController: NavHostController,
    id: Int,
    viewModel: DetailsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(id) { viewModel.load(id) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val s = state) {
            is DetailsUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            is DetailsUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.load(id, force = true) }) {
                        Text("Retry")
                    }
                }
            }
            is DetailsUiState.Success -> {
                val episodesState by viewModel.episodesState.collectAsState()

                LaunchedEffect(s.data.main_id) {
                    viewModel.seedEpisodesFromDetails(s.data)
                }

                DetailsContent(
                    data = s.data,
                    episodesState = episodesState,
                    onSeasonSelected = viewModel::selectSeason,
                    onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                    onPlayClick = { navController.navigate(Routes.player(id)) },
                    onAddClick = { /* TODO: implement watchlist toggle */ }
                )
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsContent(
    data: TitleDetailsResponse,
    episodesState: EpisodesUiState,
    onSeasonSelected: (String) -> Unit,
    onLoadMoreEpisodes: () -> Unit,
    onPlayClick: (TitleDetailsResponse) -> Unit,
    onAddClick: (TitleDetailsResponse) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // Local state to toggle watchlist (placeholder)
    var isInWatchlist by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    // Default behavior — restored inside horizontal chip/card rows below (they're
    // separate scrollable containers, so it's safe there), and also swapped in
    // dynamically for the OUTER page scroll itself while focus sits inside the
    // episode rows list (see `pageBringIntoViewSpec` below), so a focused
    // EpisodeRow centers by scrolling the whole page instead of a nested container.
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    // Ambient used by the outer page Column's verticalScroll. Defaults to NoOp so
    // that hero/tab focus changes don't fight our manual scrollState.animateScrollTo(...)
    // jumps (down-key -> episodes, back-to-top, etc). Episode rows flip this to
    // `defaultBringIntoViewSpec` while focus is among them, then flip it back on exit.
    var pageBringIntoViewSpec by remember { mutableStateOf<BringIntoViewSpec>(NoOpBringIntoViewSpec) }
    // Prevents the initial focusRequester.requestFocus() call (below) from
    // triggering a scroll animation — only real user focus navigation should scroll.
    var hasInitializedFocus by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val screenHeightPx = with(density) { screenHeight.roundToPx() }

        CompositionLocalProvider(LocalBringIntoViewSpec provides pageBringIntoViewSpec) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight)
                ) {

                    // ===========================
                    // Background Poster
                    // ===========================
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(data.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = data.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(12.dp)
                    )

                    // Horizontal Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.92f),
                                        Color.Black.copy(alpha = 0.70f),
                                        Color.Black.copy(alpha = 0.30f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Vertical Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // ===========================
                    // Bottom Left Content
                    // ===========================
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.65f)
                            .padding(
                                start = 48.dp,
                                end = 24.dp,
                                bottom = 56.dp
                            )
                            .focusGroup()
                            .onFocusChanged { state ->
                                if (state.hasFocus && hasInitializedFocus) {
                                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                                }
                            },
                        horizontalAlignment = Alignment.Start
                    ) {

                        // OTT Logo
                        data.ott_logo?.logo?.let { logoUrl ->
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = data.ott,
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(bottom = 16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Title
                        Text(
                            text = data.title,
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(0f, 4f),
                                    blurRadius = 16f
                                )
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Meta
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            data.year?.let { MetaBadge(it) }
                            data.ua?.let { MetaBadge(it) }
                            data.hdsd?.let { MetaBadge(it) }

                            data.runtime?.let {
                                Text(
                                    text = it,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Languages
                        data.lang?.takeIf { it.isNotEmpty() }?.let { langs ->
                            LanguagesRow(langs.map { it.l }, defaultBringIntoViewSpec)
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Description
                        data.desc?.let {
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 24.sp,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.4f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayButton(
                                onClick = { onPlayClick(data) },
                                focusRequester = focusRequester
                            )

                            AddButton(
                                onClick = {
                                    isInWatchlist = !isInWatchlist
                                    onAddClick(data)
                                },
                                isInWatchlist = isInWatchlist
                            )
                        }
                    }
                } // end hero Box

                // ===========================
                // Tabs Section (Episodes / More Like This / More About)
                // ===========================
                DetailsTabsSection(
                    data = data,
                    episodesState = episodesState,
                    onSeasonSelected = onSeasonSelected,
                    onLoadMoreEpisodes = onLoadMoreEpisodes,
                    defaultBringIntoViewSpec = defaultBringIntoViewSpec,
                    onTabsFocused = {
                        if (hasInitializedFocus) {
                            coroutineScope.launch { scrollState.animateScrollTo(screenHeightPx) }
                        }
                    },
                    onEpisodesFocusChanged = { hasFocus ->
                        // While focus is among episode rows, let the outer page
                        // scroll natively to center them; otherwise keep it NoOp'd
                        // so it doesn't fight the manual jumps above.
                        pageBringIntoViewSpec =
                            if (hasFocus) defaultBringIntoViewSpec else NoOpBringIntoViewSpec
                    }
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        } // end CompositionLocalProvider (NoOpBringIntoViewSpec)
    } // end BoxWithConstraints

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        hasInitializedFocus = true
    }
}

@Composable
private fun MetaBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LanguagesRow(languages: List<String>, defaultBringIntoViewSpec: BringIntoViewSpec) {
    val scrollState = rememberScrollState()
    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            languages.forEach { lang ->
                LanguageChip(text = lang)
            }
        }
    }
}

@Composable
private fun LanguageChip(text: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) bringIntoViewRequester.bringIntoView()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "langScale"
    )
    // Glow effect on focus – we use a semi‑transparent border
    val borderColor = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f)
    val backgroundColor = if (isFocused) AccentGreen else Color.White.copy(alpha = 0.10f)
    val textColor = if (isFocused) Color.Black else AccentGreen

    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(24.dp))
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlayButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(180),
        label = ""
    )

    val background by animateColorAsState(
        targetValue = if (focused)
            Color.White
        else
            Color.White.copy(alpha = 0.12f),
        label = ""
    )

    val contentColor by animateColorAsState(
        targetValue = if (focused)
            Color.Black
        else
            Color.White,
        label = ""
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .height(52.dp)
            .width(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 24f else 6f
            }
            .focusRequester(focusRequester)
            .focusGroup()
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp
        )
    ) {

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = "PLAY NOW",
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    isInWatchlist: Boolean
) {

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(180),
        label = ""
    )

    val background by animateColorAsState(
        targetValue = if (focused)
            Color.White
        else
            Color.White.copy(alpha = 0.12f),
        label = ""
    )

    val contentColor by animateColorAsState(
        targetValue = if (focused)
            Color.Black
        else
            Color.White,
        label = ""
    )

    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 24f else 6f
            }
            .focusable(interactionSource = interactionSource).focusGroup().focusRestorer(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.20f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = contentColor
        )
    ) {

        Icon(
            imageVector =
                if (isInWatchlist)
                    Icons.Default.Check
                else
                    Icons.Default.Add,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
//
//        Spacer(Modifier.width(8.dp))

//        Text(
//            text =
//                if (isInWatchlist)
//                    "IN MY LIST"
//                else
//                    "MY LIST",
//            color = contentColor,
//            fontWeight = FontWeight.Bold,
//            fontSize = 16.sp
//        )
    }
}
// ===========================================================
// Tabs Section: Episodes (series only) / More Like This / More About
// ===========================================================

/**
 * NOTE: This section assumes TitleDetailsResponse exposes:
 *   - data.type        ("t" for series, "m" for movie)
 *   - data.moredetails (List<MoreDetail> with .k / .v)
 *   - data.suggest     (List<Suggest> with .id)
 * If your model uses different property names, rename the references below to match.
 */

private enum class DetailsTab(val label: String) {
    EPISODES("Episodes"),
    MORE_LIKE_THIS("More Like This"),
    MORE_ABOUT("More About")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsTabsSection(
    data: TitleDetailsResponse,
    episodesState: EpisodesUiState,
    onSeasonSelected: (String) -> Unit,
    onLoadMoreEpisodes: () -> Unit,
    defaultBringIntoViewSpec: BringIntoViewSpec,
    onTabsFocused: () -> Unit = {},
    onEpisodesFocusChanged: (Boolean) -> Unit = {}
) {
    val isSeries = data.type == "t"

    val tabs = remember(isSeries) {
        if (isSeries) {
            listOf(DetailsTab.EPISODES, DetailsTab.MORE_LIKE_THIS, DetailsTab.MORE_ABOUT)
        } else {
            listOf(DetailsTab.MORE_LIKE_THIS, DetailsTab.MORE_ABOUT)
        }
    }

    var selectedTab by remember(isSeries) { mutableStateOf(tabs.first()) }

    val configuration = LocalConfiguration.current


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.Black)
            .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 16.dp)
            .focusGroup()
            .onFocusChanged { state ->
                if (state.hasFocus) onTabsFocused()
            }
    ) {

        // ---- Tab Row ----
        Row(
            modifier = Modifier.focusGroup().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            tabs.forEach { tab ->
                DetailsTabButton(
                    text = tab.label,
                    selected = selectedTab == tab,
                    onFocused = {
                        selectedTab = tab
                    },
                    onClick = { selectedTab = tab }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Tab Content ----
        when (selectedTab) {
            DetailsTab.EPISODES -> EpisodesTabContent(
                data = data,
                episodesState = episodesState,
                onSeasonSelected = onSeasonSelected,
                onLoadMore = onLoadMoreEpisodes,
                defaultBringIntoViewSpec = defaultBringIntoViewSpec,
                onEpisodesFocusChanged = onEpisodesFocusChanged
            )
            DetailsTab.MORE_LIKE_THIS -> MoreLikeThisTabContent(data, defaultBringIntoViewSpec)
            DetailsTab.MORE_ABOUT -> MoreAboutTabContent(data)
        }
    }
}

@Composable
private fun DetailsTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = when {
        isFocused -> Color.White
        selected -> Color.Black
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }

    val borderColor = when {
        isFocused -> Color.White
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.25f)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "tabScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                }
            }
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Same dash-placeholder convention used by TitleDetailsResponse.posterUrl —
// ep_poster arrives as a template like ".../poster/h/------.jpg" and the
// run of dashes gets swapped for the episode id.
private val EPISODE_POSTER_PLACEHOLDER = Regex("-{4,}")

private fun episodePosterUrl(template: String?, episodeId: String): String? =
    template?.let { it.replace(EPISODE_POSTER_PLACEHOLDER, episodeId) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodesTabContent(
    data: TitleDetailsResponse,
    episodesState: EpisodesUiState,
    onSeasonSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    defaultBringIntoViewSpec: BringIntoViewSpec,
    onEpisodeClick: (Episode) -> Unit = {},
    onEpisodesFocusChanged: (Boolean) -> Unit = {}
) {
    val seasons = data.season.orEmpty()

    if (seasons.isEmpty() && episodesState.episodes.isEmpty() && !episodesState.isLoadingSeason) {
        Text(
            text = "No episodes available.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        if (seasons.isNotEmpty()) {
            SeasonSelectorRow(
                seasons = seasons,
                selectedSeasonId = episodesState.seasonId,
                defaultBringIntoViewSpec = defaultBringIntoViewSpec,
                onSeasonSelected = { season -> onSeasonSelected(season.id) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        episodesState.error?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        when {
            episodesState.isLoadingSeason -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            episodesState.episodes.isEmpty() -> {
                Text(
                    text = "No episodes available for this season.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            else -> {
                // Deliberately a plain Column, not an independently-scrolling
                // LazyColumn: episode rows must live directly inside the outer
                // page's verticalScroll Column so a focused row's bringIntoView()
                // request scrolls the WHOLE PAGE, not a nested viewport. We report
                // focus in/out via onFocusChanged so DetailsContent can flip the
                // outer page's ambient BringIntoViewSpec to "default" only while
                // we're focused here (see `pageBringIntoViewSpec` up in
                // DetailsContent), and back to NoOp once focus leaves.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .onFocusChanged { onEpisodesFocusChanged(it.hasFocus) },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    episodesState.episodes.forEach { episode ->
                        EpisodeRow(
                            episode = episode,
                            posterTemplate = data.ep_poster,
                            onClick = { onEpisodeClick(episode) }
                        )
                    }

                    if (episodesState.hasMore) {
                        LoadMoreRow(
                            isLoading = episodesState.isLoadingMore,
                            onClick = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreRow(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.10f)
    val textColor = if (isFocused) Color.Black else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource, enabled = !isLoading)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLoading,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = textColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = "Load More Episodes",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonSelectorRow(
    seasons: List<Season>,
    selectedSeasonId: String?,
    defaultBringIntoViewSpec: BringIntoViewSpec,
    onSeasonSelected: (Season) -> Unit
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
        LazyRow(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = seasons,
                key = { it.id }
            ) { season ->
                SeasonChip(
                    text = season.s,
                    selected = season.id == selectedSeasonId,
                    onClick = { onSeasonSelected(season) }
                )
            }
        }
    }
}

@Composable
private fun SeasonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) bringIntoViewRequester.bringIntoView()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "seasonChipScale"
    )

    val backgroundColor = when {
        isFocused -> Color.White
        selected -> AccentGreen
        else -> Color.White.copy(alpha = 0.10f)
    }
    val textColor = when {
        isFocused -> Color.Black
        selected -> Color.Black
        else -> Color.White.copy(alpha = 0.85f)
    }
    val borderColor = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    posterTemplate: String?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) bringIntoViewRequester.bringIntoView()
    }

    val backgroundColor = if (isFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f)
    val borderColor = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Transparent

    val posterUrl = remember(episode.id, posterTemplate) {
        episodePosterUrl(posterTemplate, episode.id)
    }

    val progress = episode.complate?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) ?: 0f

    Row(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 140.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = episode.t,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = AccentGreen,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${episode.ep?.let { "$it. " } ?: ""}${episode.t.orEmpty()}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            episode.info?.takeIf { it.isNotEmpty() }?.let { info ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = info.joinToString(" • "),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            episode.ep_desc?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreLikeThisTabContent(
    data: TitleDetailsResponse,
    defaultBringIntoViewSpec: BringIntoViewSpec
) {
    val suggestions = data.suggest.orEmpty()
    val posterTemplate = data.main_poster.orEmpty()

    if (suggestions.isEmpty()) {
        Text(
            text = "No suggestions available.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        return
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
        LazyRow(
            modifier = Modifier
                .focusGroup()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = suggestions,
                key = { it.id }
            ) { item ->
                MoreLikeThisCard(
                    id = item.id,
                    posterTemplate = posterTemplate
                )
            }
        }
    }
}
@Composable
private fun MoreLikeThisCard(
    id: String,
    posterTemplate: String,

    ) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val posterUrl = remember(id, posterTemplate) {
        posterTemplate.replace("------------------", id)
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "cardScale"
    )


    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .width(200.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 3.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(posterUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------------------------------------------------
// More About Tab
// ---------------------------------------------------------
@Composable
private fun MoreAboutTabContent(data: TitleDetailsResponse) {
    val details = data.moredetails.orEmpty()

    if (details.isEmpty()) {
        Text(
            text = "No additional details available.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        details.forEach { detail ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = detail.k,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(160.dp)
                )
                Text(
                    text = detail.v,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}