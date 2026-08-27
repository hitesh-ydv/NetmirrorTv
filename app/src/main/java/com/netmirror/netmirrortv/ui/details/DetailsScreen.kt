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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.netmirror.netmirrortv.utils.PlaybackProgressStore
import com.netmirror.netmirrortv.utils.ProgressInfo
import com.netmirror.netmirrortv.utils.TvFocusManager
import com.netmirror.netmirrortv.utils.WatchlistStore
import com.yourapp.data.model.TitleDetailsResponse
import com.yourapp.data.model.Episode
import com.yourapp.data.model.Season
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AccentGreen = Color(0xFF46D369)
private val FocusGlow = Color(0x66FFFFFF) // semi‑transparent white glow

// Disables Compose's built-in "scroll focused item into view" behavior so that
// ONLY our manual scrollState.animateScrollTo(...) calls control page scrolling.
@OptIn(ExperimentalFoundationApi::class)
private object NoOpBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

// Centers the focused item in its viewport (horizontally in LazyRow or vertically in page scroll)
@OptIn(ExperimentalFoundationApi::class)
private object CenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val itemCenter = offset + size / 2f
        val containerCenter = containerSize / 2f
        return itemCenter - containerCenter
    }
}

@Composable
fun DetailsScreen(
    navController: NavHostController,
    id: String,
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

                val context = LocalContext.current
                DetailsContent(
                    data = s.data,
                    episodesState = episodesState,
                    onSeasonSelected = viewModel::selectSeason,
                    onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                    onPlayClick = {
                        val playId = if (s.data.type == "t") {
                            PlaybackProgressStore.getSeriesLastEpisode(context, s.data.main_id)?.takeIf { it.isNotBlank() }
                                ?: s.data.last_ep?.takeIf { it.isNotBlank() }
                                ?: s.data.main_id
                        } else {
                            s.data.main_id
                        }
                        navController.navigate(
                            Routes.player(
                                id = playId,
                                mainId = s.data.main_id,
                                type = s.data.type ?: "m"
                            )
                        )
                    },
                    onAddClick = { titleData, isCurrentlyInWatchlist ->
                        viewModel.toggleWatchlist(titleData, isCurrentlyInWatchlist)
                    },
                    onEpisodeClick = { episode ->
                        navController.navigate(
                            Routes.player(
                                id = episode.id,
                                mainId = s.data.main_id,
                                type = s.data.type ?: "t"
                            )
                        )
                    },
                    onSuggestionClick = { suggestionId -> navController.navigate(Routes.details(suggestionId)) }
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
    onAddClick: (TitleDetailsResponse, Boolean) -> Unit,
    onEpisodeClick: (Episode) -> Unit = { onPlayClick(data) },
    onSuggestionClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val localProgress = remember(data.main_id, data.type, data.last_ep) {
        PlaybackProgressStore.getProgressInfo(
            context = context,
            mainId = data.main_id,
            type = data.type,
            lastEp = data.last_ep
        )
    }

    val storedRatio = remember(data.main_id) {
        PlaybackProgressStore.getProgressRatio(context, data.main_id)
            ?: PlaybackProgressStore.getProgressRatio(context, data.main_id.removePrefix("SE_"))
    }

    val progressInfo = remember(data, localProgress, storedRatio) {
        if (localProgress != null) {
            localProgress
        } else if (data.isSeries && !data.last_ep.isNullOrBlank()) {
            val ep = data.episodes?.filterNotNull()?.firstOrNull { it.id == data.last_ep }
            val epComplate = ep?.complate?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
            if (epComplate != null && epComplate > 0.01f) {
                val durationStr = ep.info?.firstOrNull { it.contains("m") || it.contains("h") || it.contains("min") }
                    ?: data.runtime
                val durationSec = parseDurationToSeconds(durationStr)
                val remainingSec = if (durationSec > 0) ((1f - epComplate) * durationSec).toLong() else 0L
                val remainingText = if (remainingSec > 0) PlaybackProgressStore.formatRemainingTime(remainingSec) else ""
                ProgressInfo(
                    ratio = epComplate,
                    remainingSeconds = remainingSec,
                    remainingText = remainingText,
                    targetPlayId = data.last_ep
                )
            } else if (!data.resume_title.isNullOrBlank()) {
                ProgressInfo(
                    ratio = 0.35f,
                    remainingSeconds = 0L,
                    remainingText = "",
                    targetPlayId = data.last_ep
                )
            } else {
                null
            }
        } else if (storedRatio != null && storedRatio > 0.01f) {
            val durationSec = parseDurationToSeconds(data.runtime)
            val remainingSec = if (durationSec > 0) ((1f - storedRatio) * durationSec).toLong() else 0L
            val remainingText = if (remainingSec > 0) PlaybackProgressStore.formatRemainingTime(remainingSec) else ""
            ProgressInfo(
                ratio = storedRatio,
                remainingSeconds = remainingSec,
                remainingText = remainingText,
                targetPlayId = data.main_id
            )
        } else if (!data.resume_title.isNullOrBlank() || data.row?.contains("Continue", ignoreCase = true) == true) {
            val durationSec = parseDurationToSeconds(data.runtime)
            val fallbackRatio = 0.35f
            val remainingSec = if (durationSec > 0) ((1f - fallbackRatio) * durationSec).toLong() else 0L
            val remainingText = if (remainingSec > 0) PlaybackProgressStore.formatRemainingTime(remainingSec) else ""
            ProgressInfo(
                ratio = fallbackRatio,
                remainingSeconds = remainingSec,
                remainingText = remainingText,
                targetPlayId = data.main_id
            )
        } else {
            null
        }
    }

    val playFocusRequester = remember { FocusRequester() }
    val addFocusRequester = remember { FocusRequester() }

    val ott = com.netmirror.netmirrortv.utils.AppSession.currentOtt().ifBlank { data.ott.orEmpty() }
    val videoId = if (data.type == "t") "SE_${data.main_id}" else data.main_id
    var isInWatchlist by remember(data.main_id, ott) {
        mutableStateOf(WatchlistStore.isInWatchlist(context, ott, videoId))
    }

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
    // Prevents the initial focus request from triggering a scroll animation — only real user focus navigation should scroll.
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
                                bottom = 20.dp
                            )
                            .focusGroup()
                            .focusRestorer()
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
                                    .height(38.dp)
                                    .padding(bottom = 19.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Title
                        Text(
                            text = data.title,
                            color = Color.White,
                            fontSize = 42.sp,
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
                            Spacer(modifier = Modifier.height(18.dp))
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
                            modifier = Modifier
                                .focusGroup()
                                .focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            PlayButton(
                                onClick = { onPlayClick(data) },
                                progressInfo = progressInfo,
                                resumeTitle = data.resume_title,
                                isSeries = data.isSeries,
                                focusRequester = playFocusRequester
                            )

                            AddButton(
                                onClick = {
                                    val wasInWatchlist = isInWatchlist
                                    isInWatchlist = !wasInWatchlist
                                    onAddClick(data, wasInWatchlist)
                                },
                                isInWatchlist = isInWatchlist,
                                focusRequester = addFocusRequester
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
                    minHeight = screenHeight,
                    onEpisodeClick = onEpisodeClick,
                    onSuggestionClick = onSuggestionClick,
                    onTabsFocused = {
                        if (hasInitializedFocus) {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(
                                    screenHeightPx,
                                    animationSpec = tween(durationMillis = 350)
                                )
                            }
                        }
                    },
                    onEpisodesFocusChanged = { hasFocus ->
                        // While focus is among episode rows, let the outer page
                        // scroll naturally to bring focused episode rows into view;
                        // otherwise keep it NoOp'd so it doesn't fight the manual jumps above.
                        pageBringIntoViewSpec =
                            if (hasFocus) defaultBringIntoViewSpec else NoOpBringIntoViewSpec
                    }
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        } // end CompositionLocalProvider (NoOpBringIntoViewSpec)
    } // end BoxWithConstraints

    LaunchedEffect(Unit) {
        delay(150)
        TvFocusManager.requestFocus(playFocusRequester)
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
    val languageFocusRequesters = remember(languages) { languages.map { FocusRequester() } }

    CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .focusGroup()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            languages.forEachIndexed { index, lang ->
                LanguageChip(
                    text = lang,
                    focusRequester = languageFocusRequesters.getOrElse(index) { remember { FocusRequester() } }
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(
    text: String,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
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
            .focusRequester(focusRequester)
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
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun parseDurationToSeconds(durationStr: String?): Long {
    if (durationStr.isNullOrBlank()) return 0L
    var totalSec = 0L
    val hoursMatch = Regex("(\\d+)\\s*h", RegexOption.IGNORE_CASE).find(durationStr)
    if (hoursMatch != null) {
        totalSec += (hoursMatch.groupValues[1].toLongOrNull() ?: 0L) * 3600L
    }
    val minsMatch = Regex("(\\d+)\\s*m", RegexOption.IGNORE_CASE).find(durationStr)
    if (minsMatch != null) {
        totalSec += (minsMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
    }
    if (totalSec == 0L) {
        val digitsOnly = durationStr.filter { it.isDigit() }.toLongOrNull()
        if (digitsOnly != null && digitsOnly < 500) {
            totalSec = digitsOnly * 60L
        }
    }
    return totalSec
}

@Composable
private fun PlayButton(
    onClick: () -> Unit,
    progressInfo: ProgressInfo? = null,
    resumeTitle: String? = null,
    isSeries: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() }
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

    val isResume = progressInfo != null || !resumeTitle.isNullOrBlank()
    val buttonWidth = if (isResume) {
        if (isSeries && !resumeTitle.isNullOrBlank()) 230.dp else 200.dp
    } else {
        180.dp
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .height(52.dp)
                .width(buttonWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 24f else 6f
                }
                .focusRequester(focusRequester),
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
                text = if (isResume) "RESUME" else "PLAY NOW",
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Only for Series: Resume Episode Title (e.g. "S1:E3 Episode Name")
            if (isSeries && !resumeTitle.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = resumeTitle.substringBefore(" -").trim(),
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Progress bar & Left Time text below Resume button
        if (progressInfo != null && progressInfo.ratio > 0f) {
            Column(
                modifier = Modifier
                    .width(buttonWidth)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Netflix Red progress bar track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressInfo.ratio.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE50914))
                    )
                }

                if (progressInfo.remainingText.isNotBlank()) {
                    Text(
                        text = progressInfo.remainingText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    isInWatchlist: Boolean,
    focusRequester: FocusRequester = remember { FocusRequester() }
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
            .focusRequester(focusRequester),
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
    }
}

// ===========================================================
// Tabs Section: Episodes (series only) / More Like This / More About
// ===========================================================

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
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    onEpisodeClick: (Episode) -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
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

    val tabFocusRequesters = remember(tabs) { tabs.map { FocusRequester() } }
    var selectedTab by remember(isSeries) { mutableStateOf(tabs.first()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .background(Color.Black)
            .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 16.dp)
            .onFocusChanged { state ->
                if (state.hasFocus) onTabsFocused()
            }
    ) {

        // ---- Tab Row ----
        Row(
            modifier = Modifier
                .focusGroup()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                DetailsTabButton(
                    text = tab.label,
                    selected = selectedTab == tab,
                    focusRequester = tabFocusRequesters.getOrElse(index) { remember { FocusRequester() } },
                    onFocused = {
                        selectedTab = tab
                        onTabsFocused()
                    },
                    onClick = {
                        selectedTab = tab
                        onTabsFocused()
                    }
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
                onUpToTabs = {
                    val epTabReq = tabFocusRequesters.firstOrNull()
                    if (epTabReq != null) {
                        TvFocusManager.requestFocus(epTabReq)
                    } else {
                        false
                    }
                },
                onEpisodeClick = onEpisodeClick,
                onEpisodesFocusChanged = onEpisodesFocusChanged
            )
            DetailsTab.MORE_LIKE_THIS -> MoreLikeThisTabContent(
                data = data,
                defaultBringIntoViewSpec = defaultBringIntoViewSpec,
                onCardClick = onSuggestionClick
            )
            DetailsTab.MORE_ABOUT -> MoreAboutTabContent(data)
        }
    }
}

@Composable
private fun DetailsTabButton(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester = remember { FocusRequester() },
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
            .focusRequester(focusRequester)
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
    onUpToTabs: () -> Boolean = { false },
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

    val episodeFocusRequesters = remember(episodesState.episodes) {
        episodesState.episodes.map { FocusRequester() }
    }
    val loadMoreFocusRequester = remember { FocusRequester() }
    val seasonFocusRequesters = remember(seasons) { seasons.map { FocusRequester() } }

    val selectedSeasonIndex = remember(seasons, episodesState.seasonId) {
        val idx = seasons.indexOfFirst { it.id == episodesState.seasonId }
        if (idx >= 0) idx else 0
    }
    var lastFocusedSeasonIndex by remember(seasons) { mutableIntStateOf(selectedSeasonIndex) }
    var lastFocusedEpisodeIndex by remember(episodesState.seasonId) { mutableIntStateOf(0) }
    var shouldFocusFirstEpisodeOnLoad by remember { mutableStateOf(false) }

    LaunchedEffect(episodesState.episodes, episodesState.isLoadingSeason) {
        if (shouldFocusFirstEpisodeOnLoad && !episodesState.isLoadingSeason && episodesState.episodes.isNotEmpty()) {
            shouldFocusFirstEpisodeOnLoad = false
            episodeFocusRequesters.firstOrNull()?.let {
                TvFocusManager.requestFocus(it)
            }
        }
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
                seasonFocusRequesters = seasonFocusRequesters,
                onUpKeyDown = onUpToTabs,
                onDownKeyDown = {
                    val targetIndex = lastFocusedEpisodeIndex.coerceIn(0, (episodeFocusRequesters.size - 1).coerceAtLeast(0))
                    val targetEpRequester = episodeFocusRequesters.getOrNull(targetIndex) ?: episodeFocusRequesters.firstOrNull()
                    if (targetEpRequester != null) {
                        TvFocusManager.requestFocus(targetEpRequester)
                    } else {
                        false
                    }
                },
                onSeasonFocused = { index ->
                    lastFocusedSeasonIndex = index
                },
                onSeasonSelected = { season ->
                    shouldFocusFirstEpisodeOnLoad = true
                    lastFocusedEpisodeIndex = 0
                    onSeasonSelected(season.id)
                }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onEpisodesFocusChanged(it.hasFocus) },
//                    verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    episodesState.episodes.forEachIndexed { index, episode ->
                        EpisodeRow(
                            episode = episode,
                            posterTemplate = data.ep_poster,
                            episodeIndex = index + 1,
                            focusRequester = episodeFocusRequesters.getOrElse(index) { remember { FocusRequester() } },
                            isFirstEpisode = index == 0,
                            isLastEpisode = index == episodesState.episodes.lastIndex && episodesState.hasMore,
                            onUpKeyDown = {
                                if (seasons.isNotEmpty()) {
                                    val targetSeasonReq = seasonFocusRequesters.getOrNull(lastFocusedSeasonIndex)
                                        ?: seasonFocusRequesters.getOrNull(selectedSeasonIndex)
                                        ?: seasonFocusRequesters.firstOrNull()
                                    if (targetSeasonReq != null) {
                                        TvFocusManager.requestFocus(targetSeasonReq)
                                    } else {
                                        false
                                    }
                                } else {
                                    onUpToTabs()
                                }
                            },
                            onDownKeyDown = {
                                if (episodesState.hasMore) {
                                    TvFocusManager.requestFocus(loadMoreFocusRequester)
                                } else {
                                    false
                                }
                            },
                            onFocused = {
                                lastFocusedEpisodeIndex = index
                            },
                            onClick = { onEpisodeClick(episode) }
                        )
                    }

                    if (episodesState.hasMore) {
                        LoadMoreRow(
                            isLoading = episodesState.isLoadingMore,
                            focusRequester = loadMoreFocusRequester,
                            onUpKeyDown = {
                                val lastEpRequester = episodeFocusRequesters.lastOrNull()
                                if (lastEpRequester != null) {
                                    TvFocusManager.requestFocus(lastEpRequester)
                                } else {
                                    false
                                }
                            },
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
    focusRequester: FocusRequester = remember { FocusRequester() },
    onUpKeyDown: () -> Boolean = { false },
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.10f)
    val textColor = if (isFocused) Color.Black else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
//            .onPreviewKeyEvent { event ->
//                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
//                    onUpKeyDown()
//                } else {
//                    false
//                }
//            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLoading,
                onClick = onClick
            )
            .fillMaxWidth(0.8f)
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
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
    seasonFocusRequesters: List<FocusRequester>,
    onUpKeyDown: () -> Boolean = { false },
    onDownKeyDown: () -> Boolean = { false },
    onSeasonFocused: (Int) -> Unit = {},
    onSeasonSelected: (Season) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .focusGroup()
            .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        seasons.forEachIndexed { index, season ->
            SeasonChip(
                text = season.s.takeIf { !it.isNullOrBlank() } ?: "Season ${index + 1}",
                selected = season.id == selectedSeasonId,
                focusRequester = seasonFocusRequesters.getOrElse(index) { remember { FocusRequester() } },
                onUpKeyDown = onUpKeyDown,
                onDownKeyDown = onDownKeyDown,
                onFocused = { onSeasonFocused(index) },
                onClick = { onSeasonSelected(season) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonChip(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onUpKeyDown: () -> Boolean = { false },
    onDownKeyDown: () -> Boolean = { false },
    onFocused: () -> Unit = {},
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
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDownKeyDown()
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUpKeyDown()
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(24.dp))
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
    episodeIndex: Int,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isFirstEpisode: Boolean = false,
    isLastEpisode: Boolean = false,
    onUpKeyDown: () -> Boolean = { false },
    onDownKeyDown: () -> Boolean = { false },
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) bringIntoViewRequester.bringIntoView()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "episodeScale"
    )

    // Dark grey card background as shown in image, lighter with white border on focus
    val backgroundColor =
        if (isFocused) Color(0xFF242424)
        else Color.Transparent
    val borderColor = Color.Transparent

    val context = LocalContext.current
    val posterUrl = remember(episode.id, posterTemplate) {
        episodePosterUrl(posterTemplate, episode.id)
    }

    val storedRatio = remember(episode.id) {
        PlaybackProgressStore.getProgressRatio(context, episode.id)
    }
    val fallbackRatio = episode.complate?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) ?: 0f
    val progress = storedRatio ?: (if (fallbackRatio > 0f) fallbackRatio else null)

    val epNumText = episode.ep?.takeIf { it.isNotBlank() } ?: episodeIndex.toString()
    val durationText = episode.info?.firstOrNull { it.contains("m") || it.contains("h") || it.contains("min") }
        ?: episode.info?.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                if (isFirstEpisode && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUpKeyDown()
                } else if (isLastEpisode && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDownKeyDown()
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Number (large, left-aligned)
        Text(
            text = epNumText,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Thumbnail with watched progress bar
        Box(
            modifier = Modifier
                .size(width = 135.dp, height = 76.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = episode.t,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFFE50914))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Episode Title, Duration, and Description
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = episode.t.orEmpty(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!durationText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = durationText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            episode.ep_desc?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = desc,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val POSTER_PLACEHOLDER = Regex("-{3,}")

private fun resolveCardImageUrl(template: String?, id: String): String =
    template?.replace(POSTER_PLACEHOLDER, id).orEmpty()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreLikeThisTabContent(
    data: TitleDetailsResponse,
    defaultBringIntoViewSpec: BringIntoViewSpec,
    onCardClick: (String) -> Unit = {}
) {
    val suggestions = data.suggest.orEmpty()

    if (suggestions.isEmpty()) {
        Text(
            text = "No suggestions available.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        return
    }

    // Check 'v' (vertical / portrait) vs 'h' (horizontal / landscape) matching HomeMediaRows
    val isPortrait = data.row.equals("v", ignoreCase = true)
    val cardWidth = if (isPortrait) 150.dp else 200.dp
    val aspectRatio = if (isPortrait) 2f / 3f else 16f / 9f

    // Prefer morelike_poster, fallback to main_poster (vertical) or ep_poster (horizontal)
    val imageTemplate = when {
        !data.morelike_poster.isNullOrBlank() -> data.morelike_poster
        isPortrait -> data.main_poster.orEmpty()
        else -> data.ep_poster ?: data.main_poster.orEmpty()
    }

    val cardFocusRequesters = remember(suggestions) { suggestions.map { FocusRequester() } }

    CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 7.dp, end = 10.dp),
        ) {
            itemsIndexed(
                items = suggestions,
                key = { index, item -> "${item.id}_$index" }
            ) { index, item ->
                MoreLikeThisCard(
                    id = item.id,
                    imageTemplate = imageTemplate,
                    cardWidth = cardWidth,
                    aspectRatio = aspectRatio,
                    focusRequester = cardFocusRequesters.getOrElse(index) { remember { FocusRequester() } },
                    onClick = { onCardClick(item.id) }
                )
            }
        }
    }
}

@Composable
private fun MoreLikeThisCard(
    id: String,
    imageTemplate: String,
    cardWidth: androidx.compose.ui.unit.Dp,
    aspectRatio: Float,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val posterUrl = remember(id, imageTemplate) {
        resolveCardImageUrl(imageTemplate, id)
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(180),
        label = "cardScale"
    )

    val cardShape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .width(cardWidth)
            .aspectRatio(aspectRatio)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(cardShape)
            .background(if (isFocused) Color(0xFF303030) else Color(0xFF202020))
            .border(
                width = 3.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = cardShape
            )
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